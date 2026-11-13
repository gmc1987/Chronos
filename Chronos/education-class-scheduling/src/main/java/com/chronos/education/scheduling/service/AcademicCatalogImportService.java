package com.chronos.education.scheduling.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.chronos.education.scheduling.dao.TeacherTeachingAssignmentRepository;
import com.chronos.education.scheduling.model.AcademicTerm;
import com.chronos.education.scheduling.model.AdministrativeClass;
import com.chronos.education.scheduling.model.CourseCatalog;
import com.chronos.education.scheduling.model.EducationGrade;
import com.chronos.education.scheduling.model.Major;
import com.chronos.education.scheduling.model.Subject;
import com.chronos.education.scheduling.model.TeacherAcademicProfile;
import com.chronos.education.scheduling.model.TeacherTeachingAssignment;

@Service
public class AcademicCatalogImportService {
	public enum Kind {
		SUBJECTS("学科编码*", "学科名称*", "学科类别（字典值）", "排序"),
		COURSES("课程编码*", "课程名称*", "学科编码", "理论学时*", "实践学时*", "课程类别（字典值）", "课程性质（字典值）", "适用专业编码（多个用逗号分隔）"),
		MAJORS("专业编码*", "专业名称*", "学制年数"),
		CLASSES("班级编码*", "班级名称*", "入学年份*", "年级编码", "专业编码", "班主任工号"),
		TERM_COURSE_PLANS("行政班编码*", "培养方案编码*", "课程编码*", "周课时*"),
		PROGRAMS("专业编码*", "入学年份*", "培养层次*", "方案编码*", "方案名称*", "最低毕业学分*"),
		PROGRAM_COURSES("方案编码*", "课程编码*", "建议学期*", "课程性质*", "计入学分"),
		TEACHER_PROFILES("教师工号*", "专业方向（字典值）", "最大周课时*"),
		TERMS("校内学期编码*", "学期名称*", "学年*", "学期序号*", "开始日期*", "结束日期*", "教学周数*"),
		GRADES("年级编码*", "年级名称*", "入学年份*", "教育阶段*", "排序", "年级主任工号"),
		ASSIGNMENTS("学期编码*", "教师工号*", "学科编码*", "行政班编码", "年级编码", "周课时*", "任教角色（字典值）", "课程编码");

		final List<String> headers;
		Kind(String... headers) { this.headers = List.of(headers); }
	}

	public record Result(int rowCount, int validCount, List<String> errors) {}
	private record Entry(Object key, Runnable write) {}
	private final AcademicDataService academic;
	private final EducationDataScopeService scopes;
	private final TeacherTeachingAssignmentRepository assignments;
	private final EducationResourceTransactionLock locks;

	public AcademicCatalogImportService(AcademicDataService academic, EducationDataScopeService scopes,
			TeacherTeachingAssignmentRepository assignments, EducationResourceTransactionLock locks) {
		this.academic = academic;
		this.scopes = scopes;
		this.assignments = assignments;
		this.locks = locks;
	}

	public byte[] template(Kind kind) throws IOException {
		try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
			var header = workbook.createSheet("导入数据").createRow(0);
			for (int i = 0; i < kind.headers.size(); i++) header.createCell(i).setCellValue(kind.headers.get(i));
			var instructions = workbook.createSheet("填写说明");
			instructions.createRow(0).createCell(0)
					.setCellValue("带 * 的列必填。请将编码与工号按文本填写。仅新增，重复记录会报错。课程总学时由理论与实践学时相加。");
			if (kind == Kind.TEACHER_PROFILES) {
				instructions.getRow(0).getCell(0).setCellValue("仅更新已审批建档的教师。专业方向留空时保留原值。");
			}
			if (kind == Kind.ASSIGNMENTS) {
				instructions.createRow(1).createCell(0)
						.setCellValue("课程编码用于明确开课课程。已有唯一同键关系且课程为空时可补录；重复导入同一课程不会新增。");
			}
			if (kind == Kind.PROGRAMS || kind == Kind.PROGRAM_COURSES) {
				instructions.createRow(1).createCell(0).setCellValue(kind == Kind.PROGRAMS
						? "培养层次填 VOCATIONAL、COLLEGE 或 UNDERGRADUATE。先导入方案，再导入草稿方案课程。"
						: "课程性质填 REQUIRED、ELECTIVE 或 LIMITED_ELECTIVE。计入学分留空时沿用课程学分。");
			}
			if (kind == Kind.CLASSES) {
				instructions.createRow(1).createCell(0)
						.setCellValue("年级编码可留空。按入学年份自动匹配启用年级，优先使用本机构同届班级关联的年级。");
			}
			workbook.write(output);
			return output.toByteArray();
		}
	}

	@Transactional
	public Result run(Kind kind, MultipartFile file, boolean dryRun, String organizationId,
			Authentication authentication) throws IOException {
		return run(kind, file, dryRun, organizationId, authentication, false);
	}

	@Transactional
	public Result run(Kind kind, MultipartFile file, boolean dryRun, String organizationId,
			Authentication authentication, boolean canUpdateAssignment) throws IOException {
		if (file.isEmpty()) throw new IllegalArgumentException("请选择 Excel 文件");
		// 同一目录或机构的并行导入在读取已有编码之前排队。
		if (!dryRun) locks.lockSemester("catalog-import:" + kind + ":" + (kind == Kind.ASSIGNMENTS ? organizationId : "global-codes"));
		var parse = parser(kind, organizationId, authentication, canUpdateAssignment);
		var entries = new ArrayList<Entry>();
		var errors = new ArrayList<String>();
		var keys = new HashSet<>();
		for (var row : readRows(kind, file)) {
			try {
				var entry = parse.apply(row.subList(1, row.size()));
				if (!keys.add(entry.key())) throw new IllegalArgumentException("文件内记录重复");
				entries.add(entry);
			} catch (IllegalArgumentException | AccessDeniedException exception) {
				errors.add("第 " + row.getFirst() + " 行：" + exception.getMessage());
			}
		}

		int rowCount = entries.size() + errors.size();
		if (rowCount == 0) errors.add("工作表没有可导入的数据");
		if (!dryRun && errors.isEmpty()) entries.forEach(entry -> entry.write().run());
		return new Result(rowCount, entries.size(), errors);
	}

	public List<List<String>> readRows(Kind kind, MultipartFile file) throws IOException {
		if (file.isEmpty()) throw new IllegalArgumentException("请选择 Excel 文件");
		var formatter = new DataFormatter();
		var rows = new ArrayList<List<String>>();
		try (var input = file.getInputStream(); var workbook = new XSSFWorkbook(input)) {
			if (workbook.getNumberOfSheets() == 0) throw new IllegalArgumentException("文件没有工作表");
			var sheet = workbook.getSheetAt(0);
			if (sheet.getLastRowNum() > 5000) throw new IllegalArgumentException("单次最多导入 5000 行");
			var header = cells(sheet.getRow(0), formatter, kind.headers.size());
			boolean legacyTemplate = (kind == Kind.ASSIGNMENTS || kind == Kind.COURSES)
					&& header.getLast().isBlank() && kind.headers.subList(0, 7).equals(header.subList(0, 7));
			if (!kind.headers.equals(header) && !legacyTemplate) {
				throw new IllegalArgumentException("表头不匹配，请使用对应页面下载的模板");
			}
			for (int index = 1; index <= sheet.getLastRowNum(); index++) {
				var values = cells(sheet.getRow(index), formatter, kind.headers.size());
				if (values.stream().allMatch(String::isBlank)) continue;
				values.addFirst(String.valueOf(index + 1));
				rows.add(values);
			}
		}
		if (rows.isEmpty()) throw new IllegalArgumentException("工作表没有可导入的数据");
		return rows;
	}

	public static ResponseEntity<byte[]> xlsx(byte[] content, String filename) {
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
				.body(content);
	}

	private Function<List<String>, Entry> parser(Kind kind, String organizationId, Authentication authentication,
			boolean canUpdateAssignment) {
		var subjects = academic.subjects();
		var availableSubjects = subjects.stream().filter(item -> item.getOrganizationId() == null
				|| java.util.Objects.equals(organizationId, item.getOrganizationId())).toList();
		return switch (kind) {
			case SUBJECTS -> row -> {
				var item = new Subject();
				item.setOrganizationId(organizationId);
				item.setSubjectCode(requiredText(row.get(0), "学科编码", 64));
				unique(subjects, Subject::getSubjectCode, item.getSubjectCode());
				item.setSubjectName(requiredText(row.get(1), "学科名称", 128));
				item.setSubjectCategory(option(row.get(2), "GENERAL", Set.of("GENERAL", "PROFESSIONAL", "PRACTICE", "ACTIVITY")));
				item.setSortOrder(number(row.get(3), 0, 0));
				return new Entry(item.getSubjectCode(), () -> academic.saveSubject(null, item));
			};
			case COURSES -> {
				var courses = academic.courses();
				var availableMajors = academic.visibleMajors(Set.of(organizationId));
				yield row -> {
					var item = new CourseCatalog();
					item.setOrganizationId(organizationId);
					item.setCourseCode(requiredText(row.get(0), "课程编码", 64));
					unique(courses, CourseCatalog::getCourseCode, item.getCourseCode());
					item.setCourseName(requiredText(row.get(1), "课程名称", 128));
					if (!row.get(2).isBlank()) item.setSubjectId(match(availableSubjects, Subject::getSubjectCode, row.get(2), "学科").getId());
					item.setTheoryHours(number(required(row.get(3), "理论学时"), 0, 0));
					item.setPracticeHours(number(required(row.get(4), "实践学时"), 0, 0));
					long total = (long) item.getTheoryHours() + item.getPracticeHours();
					if (total <= 0 || total > Integer.MAX_VALUE) throw new IllegalArgumentException("总学时超出有效范围");
					item.setTotalHours((int) total);
					item.setCourseCategory(option(row.get(5), "PROFESSIONAL", Set.of("GENERAL", "PROFESSIONAL", "PRACTICE")));
					item.setCourseNature(option(row.get(6), "REQUIRED", Set.of("REQUIRED", "ELECTIVE", "LIMITED_ELECTIVE")));
					if (!row.get(7).isBlank()) {
						item.setMajorIds(java.util.Arrays.stream(row.get(7).split("[,，]", -1))
								.map(String::strip)
								.map(code -> match(availableMajors, Major::getMajorCode, code, "专业").getId())
								.collect(Collectors.toSet()));
					}
					return new Entry(item.getCourseCode(), () -> academic.saveCourse(null, item));
				};
			}
			case MAJORS -> {
				var majors = academic.majors();
				yield row -> {
					var item = new Major();
					item.setOrganizationId(organizationId);
					item.setMajorCode(requiredText(row.get(0), "专业编码", 64));
					unique(majors, Major::getMajorCode, item.getMajorCode());
					item.setMajorName(requiredText(row.get(1), "专业名称", 128));
					item.setSchoolingYears(number(row.get(2), 3, 1));
					return new Entry(item.getMajorCode(), () -> academic.saveMajor(null, item));
				};
			}
			case CLASSES -> classParser(organizationId, authentication);
			case TERMS -> termParser(organizationId);
			case GRADES -> gradeParser(organizationId, authentication);
			case TEACHER_PROFILES -> teacherProfileParser(organizationId, authentication);
			case PROGRAMS, PROGRAM_COURSES, TERM_COURSE_PLANS ->
				throw new IllegalArgumentException("请从对应业务页面导入");
			case ASSIGNMENTS -> assignmentParser(organizationId, authentication, availableSubjects, canUpdateAssignment);
		};
	}

	private Function<List<String>, Entry> termParser(String organizationId) {
		var terms = academic.terms(Set.of(organizationId));
		return row -> {
			var item = new AcademicTerm();
			item.setOrganizationId(organizationId);
			String code = requiredText(row.get(0), "校内学期编码", 64);
			if (code.contains(":")) throw new IllegalArgumentException("请填写校内学期编码");
			unique(terms, AcademicTerm::getLocalTermCode, code);
			item.setLocalTermCode(code);
			item.setTermName(requiredText(row.get(1), "学期名称", 128));
			item.setAcademicYear(requiredText(row.get(2), "学年", 16));
			item.setTermNo(number(required(row.get(3), "学期序号"), 1, 1));
			try {
				item.setStartDate(java.time.LocalDate.parse(row.get(4)));
				item.setEndDate(java.time.LocalDate.parse(row.get(5)));
			} catch (java.time.format.DateTimeParseException exception) {
				throw new IllegalArgumentException("起止日期请按 YYYY-MM-DD 填写");
			}
			if (!item.getStartDate().isBefore(item.getEndDate())) {
				throw new IllegalArgumentException("学期开始日期必须早于结束日期");
			}
			item.setWeekCount(number(required(row.get(6), "教学周数"), 20, 1));
			return new Entry(code, () -> academic.saveTerm(null, item));
		};
	}

	private Function<List<String>, Entry> gradeParser(String organizationId, Authentication authentication) {
		var existing = academic.grades();
		var teachers = academic.headTeacherCandidates(organizationId, scopes.resolve(authentication.getName()));
		return row -> {
			var item = new EducationGrade();
			item.setOrganizationId(organizationId);
			item.setGradeCode(requiredText(row.get(0), "年级编码", 64));
			unique(existing, EducationGrade::getGradeCode, item.getGradeCode());
			item.setGradeName(requiredText(row.get(1), "年级名称", 128));
			item.setEnrollmentYear(number(required(row.get(2), "入学年份"), 2000, 2000));
			item.setEducationStage(requiredText(row.get(3), "教育阶段", 32));
			item.setSortOrder(number(row.get(4), 0, 0));
			if (!row.get(5).isBlank()) {
				item.setDirectorTeacherId(match(teachers, TeacherAcademicProfile::getTeacherNo, row.get(5), "年级主任").getId());
			}
			return new Entry(item.getGradeCode(), () -> academic.saveGrade(null, item));
		};
	}

	private Function<List<String>, Entry> teacherProfileParser(String organizationId, Authentication authentication) {
		var scope = scopes.resolve(authentication.getName());
		var teachers = academic.teachers(scope, organizationId);
		return row -> {
			var previous = match(teachers, TeacherAcademicProfile::getTeacherNo, row.get(0), "已有教师档案");
			String specialty = row.get(1).isBlank() ? previous.getSpecialty() : requiredText(row.get(1), "专业方向", 128);
			int hours = number(required(row.get(2), "最大周课时"), 0, 1);
			if (hours > 56) throw new IllegalArgumentException("最大周课时不能超过 56 节");
			var command = new TeacherAcademicProfile();
			command.setEmployeeId(previous.getEmployeeId());
			command.setDepartmentId(previous.getDepartmentId());
			command.setEnabled(previous.getEnabled());
			command.setEmploymentStatus(previous.getEmploymentStatus());
			command.setSpecialty(specialty);
			command.setMaxWeeklyLessons(hours);
			command.setMaxDailyLessons(previous.getMaxDailyLessons());
			command.setMaxConsecutiveLessons(previous.getMaxConsecutiveLessons());
			return new Entry(previous.getId(), () -> academic.saveTeacher(previous.getId(), command));
		};
	}

	private Function<List<String>, Entry> classParser(String organizationId, Authentication authentication) {
		var scope = scopes.resolve(authentication.getName());
		scopes.assertFullAccess(scope);
		var classes = academic.administrativeClasses();
		var majors = academic.visibleMajors(Set.of(organizationId));
		var grades = academic.visibleGrades(Set.of(organizationId));
		var headTeachers = academic.headTeacherCandidates(organizationId, scope);
		return row -> {
			var item = new AdministrativeClass();
			item.setCampusId(organizationId);
			item.setClassCode(requiredText(row.get(0), "班级编码", 64));
			unique(classes, AdministrativeClass::getClassCode, item.getClassCode());
			item.setClassName(requiredText(row.get(1), "班级名称", 128));
			item.setGradeYear(number(required(row.get(2), "入学年份"), 0, 1900));
			if (item.getGradeYear() > 9999) throw new IllegalArgumentException("入学年份必须是四位年份");
			String gradeCode = row.get(3);
			var grade = gradeCode.isBlank()
					? defaultClassGrade(grades, classes, organizationId, item.getGradeYear())
					: match(grades, EducationGrade::getGradeCode, gradeCode, "年级");
			if (!java.util.Objects.equals(grade.getEnrollmentYear(), item.getGradeYear())) {
				throw new IllegalArgumentException("年级与入学年份不一致");
			}
			item.setGradeId(grade.getId());
			if (!row.get(4).isBlank()) {
				item.setMajorId(match(majors, Major::getMajorCode, row.get(4), "专业").getId());
			}
			if (!row.get(5).isBlank()) {
				item.setHeadTeacherId(match(headTeachers, TeacherAcademicProfile::getTeacherNo, row.get(5), "班主任").getId());
			}
			return new Entry(item.getClassCode(), () -> academic.saveAdministrativeClass(null, item));
		};
	}

	private EducationGrade defaultClassGrade(List<EducationGrade> grades, List<AdministrativeClass> classes,
			String organizationId, int enrollmentYear) {
		var enabled = grades.stream()
				.filter(grade -> Boolean.TRUE.equals(grade.getEnabled()))
				.filter(grade -> Integer.valueOf(enrollmentYear).equals(grade.getEnrollmentYear()))
				.toList();
		var usedGradeIds = classes.stream()
				.filter(item -> organizationId.equals(item.getCampusId()) && "ACTIVE".equals(item.getStatus()))
				.filter(item -> Integer.valueOf(enrollmentYear).equals(item.getGradeYear()))
				.map(AdministrativeClass::getGradeId)
				.collect(java.util.stream.Collectors.toSet());
		var local = enabled.stream().filter(grade -> usedGradeIds.contains(grade.getId())).toList();
		var candidates = local.isEmpty() ? enabled : local;
		if (candidates.size() == 1) return candidates.getFirst();
		if (candidates.isEmpty()) {
			throw new IllegalArgumentException("没有 " + enrollmentYear + " 年启用的年级，请先维护年级");
		}
		throw new IllegalArgumentException(enrollmentYear + " 年有多个可用年级，无法自动确定，请指定年级编码");
	}

	private Function<List<String>, Entry> assignmentParser(String organizationId, Authentication auth, List<Subject> subjects,
			boolean canUpdateAssignment) {
		var scope = scopes.resolve(auth.getName());
		var terms = academic.terms(Set.of(organizationId));
		var teachers = academic.teachers(scope, organizationId);
		var classes = academic.administrativeClassesInCampus(organizationId);
		var grades = academic.visibleGrades(Set.of(organizationId));
		var termIds = terms.stream().map(AcademicTerm::getId).toList();
		var courses = academic.visibleCourses(Set.of(organizationId));
		Map<List<String>, List<TeacherTeachingAssignment>> existing = termIds.isEmpty() ? Map.of()
				: assignments.findByAcademicTermIdInOrderByCreateTimeDesc(termIds).stream()
						.collect(Collectors.groupingBy(AcademicCatalogImportService::assignmentKey));
		return row -> {
			var item = new TeacherTeachingAssignment();
			var code = required(row.get(0), "学期编码");
			var term = terms.stream().filter(value -> code.equals(value.getTermCode()) || code.equals(value.getLocalTermCode()))
					.findFirst().orElseThrow(() -> new IllegalArgumentException("所选机构没有该学期：" + code));
			item.setAcademicTermId(term.getId());
			item.setTeacherId(match(teachers, TeacherAcademicProfile::getTeacherNo, row.get(1), "教师").getId());
			item.setSubjectId(match(subjects, Subject::getSubjectCode, row.get(2), "学科").getId());
			if (!row.get(3).isBlank()) {
				var schoolClass = match(classes, AdministrativeClass::getClassCode, row.get(3), "行政班");
				item.setAdministrativeClassId(schoolClass.getId());
				item.setGradeId(schoolClass.getGradeId());
			}
			if (!row.get(4).isBlank()) {
				var grade = match(grades, EducationGrade::getGradeCode, row.get(4), "年级");
				if (item.getAdministrativeClassId() != null && !grade.getId().equals(item.getGradeId())) {
					throw new IllegalArgumentException("年级与行政班不一致");
				}
				item.setGradeId(grade.getId());
			}
			item.setWeeklyLessons(number(required(row.get(5), "周课时"), 0, 0));
			item.setAssignmentRole(option(row.get(6), "TEACHER", Set.of("TEACHER", "LEAD_TEACHER", "ASSISTANT")));
			if (!row.get(7).isBlank()) {
				var course = match(courses, CourseCatalog::getCourseCode, row.get(7), "课程");
				if (!Boolean.TRUE.equals(course.getEnabled()) || !Objects.equals(item.getSubjectId(), course.getSubjectId())) {
					throw new IllegalArgumentException("课程须启用且属于所填学科");
				}
				item.setCourseId(course.getId());
			}
			scopes.assertTeachingAssignmentAccess(scope, item);
			var key = assignmentKey(item);
			var matches = existing.getOrDefault(key, List.of());
			if (item.getCourseId() == null) {
				if (!matches.isEmpty()) throw new IllegalArgumentException("任教关系已存在");
				return new Entry(key, () -> academic.saveTeachingAssignment(null, item));
			}
			var courseKey = new ArrayList<>(key);
			courseKey.add(item.getCourseId());
			if (matches.stream().anyMatch(value -> item.getCourseId().equals(value.getCourseId()))) {
				return new Entry(courseKey, () -> {});
			}
			var unbound = matches.stream().filter(value -> value.getCourseId() == null).toList();
			if (unbound.isEmpty()) return new Entry(courseKey, () -> academic.saveTeachingAssignment(null, item));
			if (unbound.size() != 1) throw new IllegalArgumentException("存在多条同键的学科任教关系，请先核对原记录");
			var previous = unbound.getFirst();
			if (!canUpdateAssignment) throw new AccessDeniedException("补录已有任教关系的课程需要修改权限");
			return new Entry(List.of("backfill", previous.getId()), () -> {
				if (assignments.bindMissingCourse(previous.getId(), item.getCourseId()) != 1) {
					throw new IllegalStateException("原任教关系已变更，请重新校验后导入");
				}
			});
		};
	}

	private static List<String> assignmentKey(TeacherTeachingAssignment item) {
		return java.util.Arrays.asList(item.getAcademicTermId(), item.getTeacherId(), item.getSubjectId(),
				item.getAdministrativeClassId(), item.getGradeId(), item.getAssignmentRole());
	}

	private static List<String> cells(Row row, DataFormatter formatter, int size) {
		var values = new ArrayList<String>(size);
		for (int i = 0; i < size; i++) values.add(row == null ? "" : formatter.formatCellValue(row.getCell(i)).strip());
		return values;
	}

	private static String required(String value, String field) {
		if (value.isBlank()) throw new IllegalArgumentException(field + "不能为空");
		return value;
	}

	private static String requiredText(String value, String field, int length) {
		if (value.length() > length) throw new IllegalArgumentException(field + "最多 " + length + " 个字符");
		return required(value, field);
	}

	private static int number(String value, int fallback, int min) {
		try {
			int result = value.isBlank() ? fallback : Integer.parseInt(value);
			if (result >= min) return result;
		} catch (NumberFormatException ignored) {
			// 按模板列校验整数，避免将小数或格式错误的值写入档案。
		}
		throw new IllegalArgumentException("数值必须是大于等于 " + min + " 的整数：" + value);
	}

	private static String option(String value, String fallback, Set<String> allowed) {
		if (value.isBlank()) return fallback;
		if (!allowed.contains(value)) throw new IllegalArgumentException("无效字典值：" + value);
		return value;
	}

	private static <T> T match(List<T> values, Function<T, String> code, String key, String label) {
		required(key, label + "编码");
		return values.stream().filter(value -> key.equals(code.apply(value))).findFirst()
				.orElseThrow(() -> new IllegalArgumentException(label + "不存在或不可用：" + key));
	}

	private static <T> void unique(List<T> values, Function<T, String> code, String key) {
		if (values.stream().anyMatch(value -> key.equals(code.apply(value)))) throw new IllegalArgumentException("编码已存在：" + key);
	}
}
