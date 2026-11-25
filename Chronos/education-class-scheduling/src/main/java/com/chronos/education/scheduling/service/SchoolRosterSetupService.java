package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.AdministrativeClassRepository;
import com.chronos.education.scheduling.dao.StudentProfileRepository;
import com.chronos.education.scheduling.model.AcademicTerm;
import com.chronos.education.scheduling.model.AdministrativeClass;
import com.chronos.education.scheduling.model.StudentProfile;
import com.chronos.service.iService.IAuditLogService;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchoolRosterSetupService {
	private final AcademicDataService academic;
	private final AdministrativeClassRepository classes;
	private final StudentProfileRepository students;
	private final JdbcTemplate jdbc;
	private final IAuditLogService audit;

	public SchoolRosterSetupService(AcademicDataService academic, AdministrativeClassRepository classes,
			StudentProfileRepository students, JdbcTemplate jdbc, IAuditLogService audit) {
		this.academic = academic;
		this.classes = classes;
		this.students = students;
		this.jdbc = jdbc;
		this.audit = audit;
	}

	@Transactional
	public AcademicTerm createTerm(String school, TermCommand body) {
		lockSchool(school);
		String year = required(body.academicYear(), "学年", 9);
		if (!year.matches("[0-9]{4}-[0-9]{4}")
				|| Integer.parseInt(year.substring(0, 4)) + 1 != Integer.parseInt(year.substring(5))
				|| body.termNo() == null || body.termNo() < 1 || body.termNo() > 3
				|| body.weeks() == null || body.weeks() < 1 || body.weeks() > 53) {
			throw new IllegalArgumentException("请填写连续学年、1 至 3 的学期序号和 1 至 53 的教学周数");
		}
		var term = new AcademicTerm();
		term.setOrganizationId(school);
		term.setAcademicYear(year);
		term.setTermNo(body.termNo());
		term.setLocalTermCode(year + "-" + body.termNo());
		term.setTermName(required(body.name(), "学期名称", 128));
		term.setStartDate(body.startDate());
		term.setEndDate(body.endDate());
		term.setWeekCount(body.weeks());
		term = academic.saveTerm(null, term);
		audit.log("SCHOOL_SETUP_TERM_CREATE", "ACADEMIC_TERM", term.getId());
		return term;
	}

	@Transactional
	public RosterResult importRoster(String school, List<RosterRow> rows) {
		String schoolCode = lockSchool(school);
		return importRows(school, rows, row -> findOrCreateClass(school, schoolCode, row));
	}

	@Transactional
	public RosterResult importExistingClassRoster(String school, String classId, List<StudentRow> students) {
		lockSchool(school);
		var target = classes.findById(classId)
				.filter(item -> school.equals(item.getCampusId()) && item.getMajorId() != null
						&& item.getGradeId() != null && "ACTIVE".equals(item.getStatus()))
				.orElseThrow(() -> new IllegalArgumentException("请选择已维护年级和专业的本校启用行政班"));
		var rows = students.stream().map(item -> new RosterRow(target.getClassCode(), target.getClassName(),
				target.getGradeYear(), item.studentNo(), item.studentName(), item.gender())).toList();
		return importRows(school, rows, row -> target);
	}

	private RosterResult importRows(String school, List<RosterRow> rows, Function<RosterRow, AdministrativeClass> classForRow) {
		if (rows == null || rows.isEmpty() || rows.size() > 500) {
			throw new IllegalArgumentException("每批请提供 1 至 500 名学生");
		}
		var studentNumbers = new HashSet<String>();
		int added = 0;
		for (int index = 0; index < rows.size(); index++) {
			try {
				var row = rows.get(index);
				if (row == null || row.enrollmentYear() == null || row.enrollmentYear() < 1900
						|| row.enrollmentYear() > 2200) {
					throw new IllegalArgumentException("入学年份无效");
				}
				String number = required(row.studentNo(), "学号", 64);
				if (!studentNumbers.add(number)) {
					throw new IllegalArgumentException("批次内有重复学号");
				}
				String name = required(row.studentName(), "学生姓名", 128);
				String gender = gender(row.gender());
				var target = classForRow.apply(row);
				var existing = students.findByStudentNo(number);
				if (existing.isPresent()) {
					var person = existing.get();
					if (!target.getId().equals(person.getAdministrativeClassId())
							|| !name.equals(person.getStudentName()) || !"ACTIVE".equals(person.getEnrollmentStatus())
							|| (gender != null && !Objects.equals(gender, gender(person.getGender())))) {
						throw new IllegalArgumentException("学号与现有在读档案不一致，请核对来源数据");
					}
					continue;
				}
				var student = new StudentProfile();
				student.setStudentNo(number);
				student.setStudentName(name);
				student.setGender(gender);
				student.setAdministrativeClassId(target.getId());
				student.setGradeYear(target.getGradeYear());
				student.setGradeId(target.getGradeId());
				student.setMajorId(target.getMajorId());
				academic.saveStudent(null, student);
				added++;
			} catch (IllegalArgumentException error) {
				throw new IllegalArgumentException("第 " + (index + 1) + " 行：" + error.getMessage(), error);
			}
		}
		audit.log("SCHOOL_SETUP_ROSTER_IMPORT", "ORGANIZATION", school + ", added=" + added + ", rows=" + rows.size());
		return new RosterResult(added, rows.size() - added);
	}

	private AdministrativeClass findOrCreateClass(String school, String schoolCode, RosterRow row) {
		String code = required(row.classCode(), "班级编码", 64);
		String name = required(row.className(), "班级名称", 128);
		var exact = classes.findByClassCode(code).filter(c -> school.equals(c.getCampusId()));
		String qualified = schoolCode + ":" + code;
		var existing = exact.or(() -> classes.findByClassCode(qualified));
		if (existing.isPresent()) {
			var target = existing.get();
			if (!school.equals(target.getCampusId()) || !name.equals(target.getClassName())
					|| !row.enrollmentYear().equals(target.getGradeYear()) || !"ACTIVE".equals(target.getStatus())) {
				throw new IllegalArgumentException("班级编码与现有在用班级不一致");
			}
			return target;
		}
		var target = new AdministrativeClass();
		target.setClassCode(required(qualified, "机构与班级组合编码", 64));
		target.setClassName(name);
		target.setGradeYear(row.enrollmentYear());
		target.setCampusId(school);
		return academic.saveAdministrativeClass(null, target);
	}

	private String lockSchool(String school) {
		// Serialize imports for a school while resolving its existing classes and roster.
		return jdbc.queryForObject("SELECT org_code FROM t_organization WHERE id=? FOR UPDATE", String.class, school);
	}

	private static String gender(String raw) {
		if (raw == null || raw.isBlank()) return null;
		return switch (raw.trim().toUpperCase(Locale.ROOT)) {
			case "M", "MALE", "男" -> "MALE";
			case "F", "FEMALE", "女" -> "FEMALE";
			default -> throw new IllegalArgumentException("性别请填写男或女，也可留空");
		};
	}

	private static String required(String value, String field, int length) {
		if (value == null || value.isBlank() || value.strip().length() > length) {
			throw new IllegalArgumentException(field + "不能为空或超过 " + length + " 个字符");
		}
		return value.strip();
	}

	public record TermCommand(String academicYear, Integer termNo, String name, LocalDate startDate, LocalDate endDate,
			Integer weeks) {}
	public record RosterRow(String classCode, String className, Integer enrollmentYear, String studentNo,
			String studentName, String gender) {}
	public record StudentRow(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 64) String studentNo,
			@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 128) String studentName,
			@jakarta.validation.constraints.Size(max = 8) String gender) {}
	public record RosterResult(int added, int existing) {}
}
