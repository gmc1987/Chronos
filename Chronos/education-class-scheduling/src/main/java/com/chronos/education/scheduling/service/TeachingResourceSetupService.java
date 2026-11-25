package com.chronos.education.scheduling.service;

import com.chronos.Idao.IOrganizationRepository;
import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.dao.CourseCatalogRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.TeacherAcademicProfileRepository;
import com.chronos.education.scheduling.model.CourseCatalog;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.service.iService.IAuditLogService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TeachingResourceSetupService {
	private final IOrganizationRepository organizations;
	private final AcademicTermRepository terms;
	private final CourseCatalogRepository courses;
	private final CourseOfferingRepository offerings;
	private final TeacherAcademicProfileRepository teachers;
	private final AcademicDataService academic;
	private final SchoolRosterSetupService roster;
	private final SchoolAssessmentIdentityService identities;
	private final EducationDataScopeService scopes;
	private final EducationResourceTransactionLock locks;
	private final IAuditLogService audit;

	public record Option(String value, String label) {}
	public record Options(List<Option> terms, List<Option> teachers) {}
	public record Teacher(String code, String name) {}
	public record TeacherLink(String teacherNo, String username) {}
	public record Command(String semesterCode, SchoolRosterSetupService.TermCommand newTerm,
			String classCode, String className, String courseCode, String courseName, Integer hours, String teacherId) {}

	@Transactional(readOnly = true)
	public Options options(String schoolId, String actor) {
		assertSchool(schoolId, actor);
		return new Options(terms.findByOrganizationIdOrderByStartDateDesc(schoolId).stream()
				.filter(term -> "ACTIVE".equals(term.getStatus()))
				.map(term -> new Option(term.getTermCode(), term.getTermName())).toList(),
				teachers.findByDepartmentIdAndEnabledTrueOrderByTeacherNo(schoolId).stream()
						.map(teacher -> new Option(teacher.getId(), teacher.getTeacherName() + "（" + teacher.getTeacherNo() + "）"))
						.toList());
	}

	@Transactional(readOnly = true)
	public List<Option> preparationTeachers(String offeringId, String actor) {
		var offering = offerings.findById(offeringId)
				.orElseThrow(() -> new IllegalArgumentException("教学班不存在"));
		var scope = scopes.resolve(actor);
		scopes.assertOfferingAccess(scope, offering);
		String schoolId = scopes.requireSchoolForCampus(scope, offering.getCampusId());
		return teachers.findByDepartmentIdAndEnabledTrueOrderByTeacherNo(schoolId).stream()
				.map(teacher -> new Option(teacher.getId(), teacher.getTeacherName())).toList();
	}

	public Option registerTeacher(String schoolId, Teacher command, String actor) {
		var teacher = identities.registerTeacher(schoolId, command.code(), command.name(), actor);
		return new Option(teacher.getId(), teacher.getTeacherName() + "（" + teacher.getTeacherNo() + "）");
	}

	public void linkTeacher(String schoolId, TeacherLink command, String actor) {
		identities.linkAccount(schoolId, new SchoolAssessmentIdentityService.AccountLink(
				"TEACHER", command.teacherNo(), command.username()), actor);
	}

	@Transactional
	public CourseOffering create(String schoolId, Command command, String actor) {
		var school = assertSchool(schoolId, actor);
		String classCode = required(command.classCode(), "教学班编码", 64);
		String className = required(command.className(), "教学班名称", 128);
		String courseCode = required(school.getOrgCode() + ":" + required(command.courseCode(), "课程编码", 32),
				"机构组合课程编码", 64);
		String courseName = required(command.courseName(), "课程名称", 128);
		Integer hours = command.hours();
		if (hours == null || hours < 1 || hours > 1000) {
			throw new IllegalArgumentException("课程学时应为 1 至 1000");
		}
		var teacher = teachers.findById(required(command.teacherId(), "教师", 64))
				.filter(item -> schoolId.equals(item.getDepartmentId()) && Boolean.TRUE.equals(item.getEnabled()))
				.orElseThrow(() -> new IllegalArgumentException("请选择本校启用的教师"));
		String termCode = command.newTerm() != null ? roster.createTerm(schoolId, command.newTerm()).getTermCode()
				: required(command.semesterCode(), "学期", 128);
		terms.findByTermCode(termCode).filter(term -> schoolId.equals(term.getOrganizationId())
				&& "ACTIVE".equals(term.getStatus())).orElseThrow(() -> new IllegalArgumentException("请选择本校启用的学期"));
		// 同校可在不同学期复用同一课程，课程与教学班在一个事务内落库。
		locks.lockSemester("teaching-setup:" + schoolId);
		if (offerings.existsBySemesterCodeAndOfferingCode(termCode, classCode)) {
			throw new IllegalStateException("该学期已存在此教学班编码");
		}
		var course = courses.findByCourseCode(courseCode).orElse(null);
		if (course == null) {
			course = new CourseCatalog();
			course.setOrganizationId(schoolId);
			course.setCourseCode(courseCode);
			course.setCourseName(courseName);
			course.setCourseCategory("GENERAL");
			course.setTotalHours(hours);
			course.setTheoryHours(hours);
			course = academic.saveCourse(null, course);
		} else if (!schoolId.equals(course.getOrganizationId()) || !courseName.equals(course.getCourseName())
				|| !hours.equals(course.getTotalHours()) || !Boolean.TRUE.equals(course.getEnabled())) {
			throw new IllegalArgumentException("课程编码与本校已有课程不一致");
		}
		var offering = new CourseOffering();
		offering.setSemesterCode(termCode);
		offering.setOfferingCode(classCode);
		offering.setTeachingClassName(className);
		offering.setCourseCode(course.getCourseCode());
		offering.setCourseName(course.getCourseName());
		offering.setCampusId(schoolId);
		offering.setTeacherId(teacher.getId());
		offering.setTeacherName(teacher.getTeacherName());
		offering.setStatus(CourseOffering.TEACHING_RESOURCE_ONLY);
		offering = offerings.save(offering);
		audit.log(actor, "EDU_TEACHING_RESOURCE_OFFERING_CREATED", "offeringId=" + offering.getId() + ",schoolId=" + schoolId);
		return offering;
	}

	private com.chronos.model.pojo.Organization assertSchool(String schoolId, String actor) {
		var scope = scopes.resolve(actor);
		if (!scope.fullAccess() && !scope.campusIds().contains(schoolId)) {
			throw new AccessDeniedException("无权维护该学校的教学资源");
		}
		return organizations.findById(schoolId).filter(org -> "SCHOOL".equals(org.getOrganizationType())
				&& Integer.valueOf(1).equals(org.getStatus())).orElseThrow(() -> new IllegalArgumentException("请选择启用的学校"));
	}

	private String required(String value, String label, int max) {
		if (value == null || value.isBlank() || value.strip().length() > max) {
			throw new IllegalArgumentException(label + "不能为空且最多 " + max + " 个字符");
		}
		return value.strip();
	}
}
