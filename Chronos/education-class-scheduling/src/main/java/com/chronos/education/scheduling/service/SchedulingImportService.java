package com.chronos.education.scheduling.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.dao.BellPeriodRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.StudentProfileRepository;
import com.chronos.education.scheduling.dao.TeacherAcademicProfileRepository;
import com.chronos.education.scheduling.model.AcademicCalendarDay;
import com.chronos.education.scheduling.model.BellPeriod;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.education.scheduling.model.TeacherTimeConstraint;
import com.chronos.file.service.ManagedFileService.FileContent;
import com.chronos.security.DirectoryDataScopeGuard;
import com.chronos.service.iService.IAuditLogService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class SchedulingImportService {
	public static final String VERSION = "1.0";

	public enum Kind {
		CALENDAR_DAYS("教学日历特殊日期", List.of("日期", "名称", "日期类型", "是否教学日", "参照课表日期", "备注")),
		BELL_PERIODS("作息节次", List.of("节次序号", "节次名称", "所属时段", "开始时间", "结束时间", "允许排课")),
		TEACHING_MEMBERS("教学班成员", List.of("学号")),
		TEACHER_CONSTRAINTS("教师时间约束", List.of("教师工号", "星期", "节次", "约束类型", "权重", "原因"));

		private final String label;
		private final List<String> headers;

		Kind(String label, List<String> headers) {
			this.label = label;
			this.headers = headers;
		}
	}

	public record Scope(String organizationId, String termId, String targetId) {}
	public record ImportRow(int line, List<String> values) {}
	public record Preview(String id, String templateVersion, String fileDigest, String managedFileId, List<ImportRow> rows, List<String> errors) {}
	public record Batch(Preview preview, Instant expiresAt, Instant committedAt) {}
	public record BatchSummary(String id, int rowCount, int errorCount, Instant expiresAt, Instant committedAt, boolean sourceAvailable) {}
	public record BatchPage(List<BatchSummary> content, boolean hasMore) {}
	private record BatchState(String snapshot, Instant expiresAt, Instant committedAt) {}
	private record Validation(List<AcademicCalendarDay> dates, List<BellPeriod> periods,
			List<TeacherTimeConstraint> constraints, List<String> students, List<String> errors) {}

	private final EducationImportArchive archive;
	private final JdbcTemplate jdbc;
	private final ObjectMapper json;
	private final DirectoryDataScopeGuard directory;
	private final EducationDataScopeService scopes;
	private final AcademicTermRepository terms;
	private final AcademicCalendarService calendars;
	private final BellPeriodRepository periods;
	private final ClassSchedulingService scheduling;
	private final TeacherAcademicProfileRepository teachers;
	private final IAuditLogService audit;
	private final AcademicDataService academic;
	private final CourseOfferingRepository offerings;
	private final StudentProfileRepository students;

	public SchedulingImportService(EducationImportArchive archive, JdbcTemplate jdbc, ObjectMapper json, DirectoryDataScopeGuard directory,
			EducationDataScopeService scopes, AcademicTermRepository terms, AcademicCalendarService calendars,
			BellPeriodRepository periods, ClassSchedulingService scheduling, TeacherAcademicProfileRepository teachers,
			IAuditLogService audit, AcademicDataService academic, CourseOfferingRepository offerings, StudentProfileRepository students) {
		this.archive = archive;
		this.jdbc = jdbc;
		this.json = json;
		this.directory = directory;
		this.scopes = scopes;
		this.terms = terms;
		this.calendars = calendars;
		this.periods = periods;
		this.scheduling = scheduling;
		this.teachers = teachers;
		this.audit = audit;
		this.academic = academic;
		this.offerings = offerings;
		this.students = students;
	}

	public byte[] template(Kind kind) throws IOException {
		try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
			var sheet = workbook.createSheet(kind.label);
			var header = sheet.createRow(0);
			var style = workbook.createCellStyle();
			style.setDataFormat(workbook.createDataFormat().getFormat("@"));
			for (int index = 0; index < kind.headers.size(); index++) {
				header.createCell(index).setCellValue(kind.headers.get(index));
				sheet.setColumnWidth(index, 20 * 256);
				sheet.setDefaultColumnStyle(index, style);
			}
			var guide = workbook.createSheet("填写说明");
			guide.createRow(0).createCell(0).setCellValue("模板版本：" + VERSION);
			String format = switch (kind) {
				case CALENDAR_DAYS -> "日期格式 YYYY-MM-DD；日期类型填节假日、调休补课日、补课日、考试日或学校活动。周末教学日要填参照课表日期。";
				case BELL_PERIODS -> "所属时段填上午、下午或晚上；时间格式 HH:mm；允许排课填是或否。";
				case TEACHING_MEMBERS -> "学号按文本填写。合班公共课请通过来源行政班同步。";
				case TEACHER_CONSTRAINTS -> "星期填数字 1 至 7；约束类型填禁止排课或优先安排；权重填 1 至 100。";
			};
			guide.createRow(1).createCell(0).setCellValue(format);
			guide.createRow(2).createCell(0).setCellValue("仅新增记录，存在重复项或无效记录时整批不写入。");
			workbook.write(output);
			return output.toByteArray();
		}
	}

	@Transactional
	public Preview preview(Kind kind, Scope scope, String version, MultipartFile file, Authentication actor)
			throws IOException {
		String code = requireTarget(kind, scope, actor);
		if (!VERSION.equals(version)) throw new IllegalArgumentException("模板版本不匹配，请重新下载");
		byte[] content = archive.validate(file);
		List<ImportRow> rows = parse(kind, content);
		List<String> errors = validate(kind, scope, code, rows, actor).errors();
		String id = UUID.randomUUID().toString();
		var source = archive.store(id, file, actor);
		Preview result = new Preview(id, VERSION, source.sha256(), source.id(), rows, errors);
		jdbc.update("""
				insert into edu_scheduling_import_batch
				(id, kind, organization_id, term_id, target_id, operator_name, expires_at, snapshot)
				values (?, ?, ?, ?, ?, ?, ?, cast(? as jsonb))
				""", result.id(), kind.name(), scope.organizationId(), scope.termId(), targetId(kind, scope),
				actor.getName(), java.sql.Timestamp.from(Instant.now().plus(30, ChronoUnit.MINUTES)), json.writeValueAsString(result));
		return result;
	}

	@Transactional
	public Batch commit(Kind kind, Scope scope, String id, String version, Authentication actor) throws JsonProcessingException {
		String code = requireTarget(kind, scope, actor);
		if (!VERSION.equals(version)) throw new IllegalArgumentException("模板版本不匹配，请重新校验");
		BatchState stored = findBatch(kind, scope, id, actor, true);
		Preview preview = json.readValue(stored.snapshot(), Preview.class);
		if (stored.committedAt() != null) return new Batch(preview, stored.expiresAt(), stored.committedAt());
		if (Instant.now().isAfter(stored.expiresAt()) || !preview.errors().isEmpty()) {
			throw new IllegalArgumentException("预检未通过或已过期，请重新校验");
		}
		if (preview.managedFileId() == null) throw new IllegalArgumentException("请重新预检以归档原文件");
		archive.read(id, preview.managedFileId(), preview.fileDigest(), actor);
		// All commits for the same term serialize before checking live data again.
		terms.findForUpdateByTermCode(code).orElseThrow(() -> new IllegalArgumentException("学期不存在"));
		requireTarget(kind, scope, actor);
		if (kind == Kind.TEACHING_MEMBERS) {
			offerings.findForCombinedUpdate(scope.targetId())
					.orElseThrow(() -> new IllegalArgumentException("教学班不存在"));
		}
		Validation validation = validate(kind, scope, code, preview.rows(), actor);
		if (!validation.errors().isEmpty()) {
			throw new IllegalArgumentException("数据已发生变化，请重新校验。" + validation.errors().getFirst());
		}
		if (!validation.dates().isEmpty()) {
			var applied = calendars.importCalendarDays(scope.termId(), validation.dates(), false);
			if (!applied.errors().isEmpty()) throw new IllegalArgumentException(applied.errors().getFirst());
		}
		validation.students().forEach(studentId -> academic.enroll(scope.targetId(), studentId));
		validation.periods().forEach(period -> calendars.saveBellPeriod(null, period));
		validation.constraints().forEach(constraint -> scheduling.saveTeacherConstraint(null, constraint));
		Instant committed = Instant.now().truncatedTo(ChronoUnit.MICROS);
		jdbc.update("update edu_scheduling_import_batch set committed_at = ? where id = ?",
				java.sql.Timestamp.from(committed), id);
		audit.log(actor.getName(), "EDUCATION_SCHEDULING_IMPORT",
				"批次 " + id + "，" + kind.label + " " + preview.rows().size() + " 条", scope.organizationId());
		return new Batch(preview, stored.expiresAt(), committed);
	}

	@Transactional(readOnly = true)
	public Batch batch(Kind kind, Scope scope, String id, Authentication actor) throws JsonProcessingException {
		requireTarget(kind, scope, actor);
		BatchState stored = findBatch(kind, scope, id, actor, false);
		return new Batch(json.readValue(stored.snapshot(), Preview.class), stored.expiresAt(), stored.committedAt());
	}

	@Transactional(readOnly = true)
	public BatchPage batches(Kind kind, Scope scope, int page, Authentication actor) {
		requireTarget(kind, scope, actor);
		int pageSize = 20;
		List<BatchSummary> rows = jdbc.query("""
				select id, jsonb_array_length(snapshot->'rows') as row_count,
				       jsonb_array_length(snapshot->'errors') as error_count, expires_at, committed_at,
				       snapshot->>'managedFileId' is not null as source_available
				from edu_scheduling_import_batch
				where kind = ? and organization_id = ? and term_id = ? and target_id = ? and operator_name = ?
				order by expires_at desc, id
				limit ? offset ?
				""", (row, index) -> {
			var committed = row.getTimestamp("committed_at");
			return new BatchSummary(row.getString("id"), row.getInt("row_count"), row.getInt("error_count"),
					row.getTimestamp("expires_at").toInstant(), committed == null ? null : committed.toInstant(),
					row.getBoolean("source_available"));
		}, kind.name(), scope.organizationId(), scope.termId(), targetId(kind, scope), actor.getName(),
				pageSize + 1, Math.max(0, page) * (long) pageSize);
		return new BatchPage(rows.stream().limit(pageSize).toList(), rows.size() > pageSize);
	}

	@Transactional(readOnly = true)
	public FileContent source(Kind kind, Scope scope, String id, Authentication actor) throws JsonProcessingException {
		requireTarget(kind, scope, actor);
		Preview preview = json.readValue(findBatch(kind, scope, id, actor, false).snapshot(), Preview.class);
		return archive.read(id, preview.managedFileId(), preview.fileDigest(), actor);
	}

	private BatchState findBatch(Kind kind, Scope scope, String id, Authentication actor, boolean lock) {
		String query = """
				select snapshot, expires_at, committed_at from edu_scheduling_import_batch
				where id = ? and kind = ? and organization_id = ? and term_id = ? and target_id = ? and operator_name = ?
				""";
		return jdbc.query(query + (lock ? " for update" : ""), (row, index) -> {
			var committed = row.getTimestamp("committed_at");
			return new BatchState(row.getString("snapshot"), row.getTimestamp("expires_at").toInstant(),
					committed == null ? null : committed.toInstant());
		}, id, kind.name(), scope.organizationId(), scope.termId(), targetId(kind, scope), actor.getName())
				.stream().findFirst().orElseThrow(() -> new AccessDeniedException("无权访问该导入批次"));
	}

	private String requireTarget(Kind kind, Scope scope, Authentication actor) {
		directory.requireOrganization(actor, scope.organizationId());
		var term = terms.findById(scope.termId()).orElseThrow(() -> new IllegalArgumentException("学期不存在"));
		if (!scope.organizationId().equals(term.getOrganizationId())) {
			throw new AccessDeniedException("学期不属于当前机构");
		}
		if (kind == Kind.BELL_PERIODS) {
			var schedule = calendars.bellSchedule(targetId(kind, scope));
			var campuses = scopes.scheduleOrganizationCampuses(scopes.resolve(actor.getName()), scope.organizationId());
			if (!term.getId().equals(schedule.getAcademicTermId()) || !campuses.contains(schedule.getCampusId())) {
				throw new AccessDeniedException("作息方案不属于当前机构学期");
			}
		}
		if (kind == Kind.TEACHING_MEMBERS) {
			var offering = offering(scope);
			var educationScope = scopes.resolve(actor.getName());
			scopes.assertOfferingAccess(educationScope, offering);
			var campuses = scopes.scheduleOrganizationCampuses(educationScope, scope.organizationId());
			if (!term.getTermCode().equals(offering.getSemesterCode()) || !campuses.contains(offering.getCampusId())) {
				throw new AccessDeniedException("教学班不属于当前机构学期");
			}
			if ("COMBINED".equals(offering.getOfferingMode())) {
				throw new IllegalArgumentException("合班课成员由来源行政班同步");
			}
		}
		return term.getTermCode();
	}

	private String targetId(Kind kind, Scope scope) {
		if (kind != Kind.BELL_PERIODS && kind != Kind.TEACHING_MEMBERS) return scope.termId();
		if (scope.targetId() == null || scope.targetId().isBlank()) throw new IllegalArgumentException("请选择导入目标");
		return scope.targetId();
	}

	private List<ImportRow> parse(Kind kind, byte[] content) {
		try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
			var sheet = workbook.getSheetAt(0);
			var formatter = new DataFormatter();
			if (sheet.getLastRowNum() > 1000 || sheet.getRow(0) == null) {
				throw new IllegalArgumentException("单批最多 1000 行，请使用下载的模板");
			}
			for (int column = 0; column < kind.headers.size(); column++) {
				if (!kind.headers.get(column).equals(formatter.formatCellValue(sheet.getRow(0).getCell(column)).trim())) {
					throw new IllegalArgumentException("模板列名或顺序不匹配");
				}
			}
			List<ImportRow> rows = new ArrayList<>();
			for (int index = 1; index <= sheet.getLastRowNum(); index++) {
				var row = sheet.getRow(index);
				if (row == null) continue;
				List<String> values = new ArrayList<>();
				for (int column = 0; column < kind.headers.size(); column++) {
					var cell = row.getCell(column);
					if (cell != null && cell.getCellType() == CellType.FORMULA) {
						throw new IllegalArgumentException("第 " + (index + 1) + " 行不能使用公式");
					}
					values.add(formatter.formatCellValue(cell).trim());
				}
				if (values.stream().anyMatch(value -> !value.isEmpty())) rows.add(new ImportRow(index + 1, values));
			}
			if (rows.isEmpty()) throw new IllegalArgumentException("文件中没有可导入的数据");
			return rows;
		} catch (IOException exception) {
			throw new IllegalArgumentException("无法读取 XLSX 文件", exception);
		}
	}

	private Validation validate(Kind kind, Scope scope, String code, List<ImportRow> rows, Authentication actor) {
		if (kind == Kind.TEACHING_MEMBERS) return validateMembers(scope, rows, actor);
		List<String> errors = new ArrayList<>();
		List<AcademicCalendarDay> dates = new ArrayList<>();
		List<BellPeriod> newPeriods = new ArrayList<>();
		List<TeacherTimeConstraint> constraints = new ArrayList<>();
		var existingPeriods = new ArrayList<BellPeriod>();
		Set<ConstraintSlot> occupied = new HashSet<>();
		var actorScope = kind == Kind.TEACHER_CONSTRAINTS ? scopes.resolve(actor.getName()) : null;
		var campuses = actorScope == null ? Set.<String>of()
				: scopes.scheduleOrganizationCampuses(actorScope, scope.organizationId());
		if (kind == Kind.BELL_PERIODS) {
			existingPeriods.addAll(periods.findByBellScheduleIdOrderByPeriodNo(scope.targetId()));
		} else if (kind == Kind.TEACHER_CONSTRAINTS) {
			scheduling.teacherConstraints(code).stream().map(ConstraintSlot::of).forEach(occupied::add);
		}
		for (ImportRow row : rows) {
			try {
				if (kind == Kind.CALENDAR_DAYS) {
					dates.add(calendarDate(row.values()));
				} else if (kind == Kind.BELL_PERIODS) {
					var value = period(scope.targetId(), row.values());
					if (existingPeriods.stream().anyMatch(other -> other.getPeriodNo().equals(value.getPeriodNo())
							|| value.getStartTime().isBefore(other.getEndTime()) && value.getEndTime().isAfter(other.getStartTime()))) {
						throw new IllegalArgumentException("节次序号重复或时间重叠");
					}
					existingPeriods.add(value);
					newPeriods.add(value);
				} else {
					var value = constraint(scope.termId(), code, row.values(), actorScope, campuses);
					if (!occupied.add(ConstraintSlot.of(value))) {
						throw new IllegalArgumentException("该教师在同一星期和节次已有约束");
					}
					constraints.add(value);
				}
			} catch (IllegalArgumentException | java.time.DateTimeException exception) {
				errors.add("第 " + row.line() + " 行：" + exception.getMessage());
			}
		}
		if (kind == Kind.CALENDAR_DAYS && errors.isEmpty()) {
			errors.addAll(calendars.importCalendarDays(scope.termId(), dates, true).errors());
		}
		return new Validation(dates, newPeriods, constraints, List.of(), errors);
	}

	private CourseOffering offering(Scope scope) {
		return offerings.findById(targetId(Kind.TEACHING_MEMBERS, scope))
				.orElseThrow(() -> new IllegalArgumentException("教学班不存在"));
	}

	private Validation validateMembers(Scope scope, List<ImportRow> rows, Authentication actor) {
		var offering = offering(scope);
		var educationScope = scopes.resolve(actor.getName());
		var campuses = scopes.scheduleOrganizationCampuses(educationScope, scope.organizationId());
		Set<String> enrolled = new HashSet<>();
		academic.members(offering.getId()).stream().filter(member -> "ENROLLED".equals(member.getEnrollmentStatus()))
				.forEach(member -> enrolled.add(member.getStudentId()));
		List<String> candidates = new ArrayList<>();
		List<String> errors = new ArrayList<>();
		for (ImportRow row : rows) {
			try {
				var student = students.findByStudentNo(row.values().getFirst())
						.filter(item -> "ACTIVE".equals(item.getEnrollmentStatus()))
						.filter(item -> scopes.canAccessStudent(educationScope, item))
						.filter(item -> scopes.studentInCampuses(item, campuses))
						.orElseThrow(() -> new IllegalArgumentException("当前机构无可用的学号"));
				if (enrolled.contains(student.getId())) throw new IllegalArgumentException("学生已选课或在文件中重复");
				if (enrolled.size() >= offering.getStudentCount()) throw new IllegalArgumentException("教学班人数超出容量");
				academic.assertStudentScheduleAvailable(student.getId(), offering.getId());
				enrolled.add(student.getId());
				candidates.add(student.getId());
			} catch (IllegalArgumentException | IllegalStateException exception) {
				errors.add("第 " + row.line() + " 行：" + exception.getMessage());
			}
		}
		return new Validation(List.of(), List.of(), List.of(), candidates, errors);
	}

	private AcademicCalendarDay calendarDate(List<String> row) {
		var day = new AcademicCalendarDay();
		day.setCalendarDate(LocalDate.parse(row.get(0)));
		day.setDayName(row.get(1));
		day.setDayType(switch (row.get(2)) {
			case "节假日", "HOLIDAY" -> "HOLIDAY";
			case "调休补课日", "MAKEUP_WORKDAY" -> "MAKEUP_WORKDAY";
			case "补课日", "MAKEUP" -> "MAKEUP";
			case "考试日", "EXAM" -> "EXAM";
			case "学校活动", "EVENT" -> "EVENT";
			default -> throw new IllegalArgumentException("不支持的日期类型");
		});
		day.setTeachingDay(switch (row.get(3)) {
			case "是" -> true;
			case "否" -> false;
			default -> throw new IllegalArgumentException("是否教学日应填是或否");
		});
		if (!row.get(4).isBlank()) day.setScheduleDate(LocalDate.parse(row.get(4)));
		day.setRemark(row.get(5));
		return day;
	}

	private BellPeriod period(String scheduleId, List<String> row) {
		var period = new BellPeriod();
		period.setBellScheduleId(scheduleId);
		period.setPeriodNo(number(row.get(0), 1, Integer.MAX_VALUE, "节次序号"));
		if (row.get(1).isBlank() || row.get(1).length() > 64) throw new IllegalArgumentException("节次名称应为 1 至 64 字符");
		period.setPeriodName(row.get(1));
		period.setDaySegment(switch (row.get(2)) {
			case "上午", "MORNING" -> "MORNING";
			case "下午", "AFTERNOON" -> "AFTERNOON";
			case "晚上", "EVENING" -> "EVENING";
			default -> throw new IllegalArgumentException("所属时段应填上午、下午或晚上");
		});
		period.setStartTime(LocalTime.parse(row.get(3)));
		period.setEndTime(LocalTime.parse(row.get(4)));
		if (!period.getStartTime().isBefore(period.getEndTime())) throw new IllegalArgumentException("开始时间必须早于结束时间");
		period.setSchedulable(switch (row.get(5)) {
			case "是" -> true;
			case "否" -> false;
			default -> throw new IllegalArgumentException("允许排课应填是或否");
		});
		return period;
	}

	private TeacherTimeConstraint constraint(String termId, String code, List<String> row,
			EducationDataScope actorScope, Set<String> campuses) {
		var teacher = teachers.findByTeacherNo(row.get(0)).filter(value -> Boolean.TRUE.equals(value.getEnabled()))
				.filter(value -> scopes.canAccessTeacher(actorScope, value.getId()))
				.filter(value -> scopes.teacherBelongsToTerm(value, termId, campuses))
				.orElseThrow(() -> new IllegalArgumentException("当前机构无可用的教师工号"));
		var value = new TeacherTimeConstraint();
		value.setSemesterCode(code);
		value.setTeacherId(teacher.getId());
		value.setDayOfWeek(number(row.get(1), 1, 7, "星期"));
		value.setPeriodNo(number(row.get(2), 1, Integer.MAX_VALUE, "节次"));
		value.setConstraintType(switch (row.get(3)) {
			case "禁止排课", "FORBIDDEN" -> "FORBIDDEN";
			case "优先安排", "PREFERRED" -> "PREFERRED";
			default -> throw new IllegalArgumentException("约束类型应填禁止排课或优先安排");
		});
		value.setWeight(row.get(4).isBlank() ? 10 : number(row.get(4), 1, 100, "权重"));
		if (row.get(5).length() > 500) throw new IllegalArgumentException("原因不得超过 500 字符");
		value.setReason(row.get(5));
		return value;
	}

	private record ConstraintSlot(String teacherId, int day, int period) {
		static ConstraintSlot of(TeacherTimeConstraint value) {
			return new ConstraintSlot(value.getTeacherId(), value.getDayOfWeek(), value.getPeriodNo());
		}
	}

	private int number(String value, int min, int max, String label) {
		try {
			int parsed = Integer.parseInt(value);
			if (parsed >= min && parsed <= max) return parsed;
		} catch (NumberFormatException ignored) {
			// Use the same row error for malformed and out-of-range values.
		}
		throw new IllegalArgumentException(label + "必须是有效范围内的整数");
	}

}
