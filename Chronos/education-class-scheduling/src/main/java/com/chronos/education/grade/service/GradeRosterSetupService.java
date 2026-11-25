package com.chronos.education.grade.service;

import com.chronos.Idao.IOrganizationRepository;
import com.chronos.Idao.IDictRepository;
import com.chronos.education.scheduling.service.SchoolAssessmentIdentityService;
import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.StudentProfileRepository;
import com.chronos.education.scheduling.dao.TeacherAcademicProfileRepository;
import com.chronos.education.scheduling.dao.TeachingClassMemberRepository;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.TeachingClassMember;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.education.scheduling.service.EducationResourceTransactionLock;
import com.chronos.education.scheduling.service.SchoolRosterSetupService;
import com.chronos.service.iService.IAuditLogService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GradeRosterSetupService {
	private final IOrganizationRepository organizations;
	private final AcademicTermRepository terms;
	private final TeacherAcademicProfileRepository teachers;
	private final CourseOfferingRepository offerings;
	private final TeachingClassMemberRepository members;
	private final StudentProfileRepository students;
	private final SchoolRosterSetupService roster;
	private final EducationDataScopeService scopes;
	private final EducationResourceTransactionLock locks;
	private final IAuditLogService audit;

	private final SchoolAssessmentIdentityService identities;
	private final IDictRepository dictionaries;

	public Option registerTeacher(String schoolId, String code, String name, String actor) {
		var teacher = identities.registerTeacher(schoolId, code, name, actor);
		return new Option(teacher.getId(), teacher.getTeacherName() + "（" + teacher.getTeacherNo() + "）");
	}

	public void linkStudent(String schoolId, String studentNo, String username, String actor) {
		identities.linkAccount(schoolId, new SchoolAssessmentIdentityService.AccountLink("STUDENT", studentNo, username), actor);
	}

	public List<Option> specialStatuses() {
		return dictionaries.findByDictCode("EDU_GRADE_SPECIAL_STATUS").stream()
				.filter(item -> Integer.valueOf(1).equals(item.getStatus()))
				.filter(item -> item.getDictValue() != null && !item.getDictValue().isBlank())
				.map(item -> new Option(item.getDictValue(), item.getDictName())).toList();
	}

	public record Option(String value, String label) {}
	public record Options(List<Option> terms, List<Option> teachers) {}
	public record Command(SchoolRosterSetupService.TermCommand newTerm, String semesterCode, String classCode,
			String className, String courseCode, String courseName, String teacherId,
			List<SchoolRosterSetupService.RosterRow> students) {}
	public record Result(String offeringId, int count) {}

	@Transactional(readOnly = true)
	public Options options(String schoolId, String actor) {
		requireSchool(schoolId, actor);
		return new Options(terms.findByOrganizationIdOrderByStartDateDesc(schoolId).stream()
				.filter(term -> "ACTIVE".equals(term.getStatus()))
				.map(term -> new Option(term.getTermCode(), term.getTermName())).toList(),
				teachers.findByDepartmentIdAndEnabledTrueOrderByTeacherNo(schoolId).stream()
						.map(teacher -> new Option(teacher.getId(), teacher.getTeacherName() + "（" + teacher.getTeacherNo() + "）"))
						.toList());
	}

	@Transactional
	public Result create(String schoolId, Command command, String actor) {
		return create(schoolId, command, actor, CourseOffering.GRADE_ONLY, "EDU_GRADE_ROSTER_CREATED");
	}

	@Transactional
	public Result createHomeworkRoster(String schoolId, Command command, String actor) {
		return create(schoolId, command, actor, CourseOffering.HOMEWORK_ONLY, "EDU_HOMEWORK_ROSTER_CREATED");
	}

	private Result create(String schoolId, Command command, String actor, String status, String auditEvent) {
		requireSchool(schoolId, actor);
		String classCode = required(command.classCode(), "教学班编码", 64);
		String className = required(command.className(), "教学班名称", 128);
		String courseCode = required(command.courseCode(), "课程编码", 64);
		String courseName = required(command.courseName(), "课程名称", 128);
		var teacher = teachers.findById(required(command.teacherId(), "任课教师", 64))
				.filter(item -> schoolId.equals(item.getDepartmentId()) && Boolean.TRUE.equals(item.getEnabled()))
				.orElseThrow(() -> new IllegalArgumentException("请选择本校启用的任课教师"));
		String semesterCode = command.newTerm() == null
				? required(command.semesterCode(), "学期", 128)
				: roster.createTerm(schoolId, command.newTerm()).getTermCode();
		terms.findByTermCode(semesterCode).filter(term -> schoolId.equals(term.getOrganizationId())
				&& "ACTIVE".equals(term.getStatus()))
				.orElseThrow(() -> new IllegalArgumentException("请选择本校启用的学期"));
		// 与同校名册导入使用相同的事务顺序，任一行失败时学期、教学班和名册一起回滚。
		roster.importRoster(schoolId, command.students());
		locks.lockSemester(semesterCode);
		if (offerings.existsBySemesterCodeAndOfferingCode(semesterCode, classCode)) {
			throw new IllegalStateException("该学期已存在此教学班编码");
		}
		var offering = new CourseOffering();
		offering.setSemesterCode(semesterCode);
		offering.setOfferingCode(classCode);
		offering.setTeachingClassName(className);
		offering.setCourseCode(courseCode);
		offering.setCourseName(courseName);
		offering.setCampusId(schoolId);
		offering.setTeacherId(teacher.getId());
		offering.setTeacherName(teacher.getTeacherName());
		offering.setStudentCount(command.students().size());
		offering.setStatus(status);
		offering = offerings.save(offering);
		for (var row : command.students()) {
			var profile = students.findByStudentNo(row.studentNo().strip()).orElseThrow();
			var member = new TeachingClassMember();
			member.setOfferingId(offering.getId());
			member.setStudentId(profile.getId());
			member.setEnrolledAt(LocalDateTime.now());
			members.save(member);
		}
		audit.log(actor, auditEvent, "offeringId=" + offering.getId() + ",schoolId=" + schoolId);
		return new Result(offering.getId(), offering.getStudentCount());
	}

	private void requireSchool(String schoolId, String actor) {
		var scope = scopes.resolve(actor);
		if (!scope.fullAccess() && !scope.campusIds().contains(schoolId)) {
			throw new AccessDeniedException("无权维护该学校的教学班名册");
		}
		organizations.findById(schoolId).filter(org -> "SCHOOL".equals(org.getOrganizationType())
				&& Integer.valueOf(1).equals(org.getStatus()))
				.orElseThrow(() -> new IllegalArgumentException("请选择启用的学校"));
	}

	private String required(String value, String label, int max) {
		if (value == null || value.isBlank() || value.strip().length() > max) {
			throw new IllegalArgumentException(label + "不能为空且最多 " + max + " 个字符");
		}
		return value.strip();
	}
}
