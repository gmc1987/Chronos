package com.chronos.education.scheduling.service;

import com.chronos.Idao.IDictRepository;
import com.chronos.Idao.IOrganizationRepository;
import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.dao.ClassroomRepository;
import com.chronos.education.scheduling.dao.StudentProfileRepository;
import com.chronos.education.scheduling.dao.TeacherAcademicProfileRepository;
import com.chronos.education.scheduling.model.AcademicTerm;
import com.chronos.education.scheduling.model.Classroom;
import com.chronos.education.scheduling.model.Subject;
import com.chronos.service.iService.IAuditLogService;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExamSetupService {
	private final IOrganizationRepository organizations;
	private final EducationDataScopeService scopes;
	private final AcademicTermRepository terms;
	private final StudentProfileRepository students;
	private final ClassroomRepository rooms;
	private final TeacherAcademicProfileRepository teachers;
	private final IDictRepository dictionaries;
	private final AcademicDataService academic;
	private final ClassSchedulingService scheduling;
	private final SchoolRosterSetupService roster;
	private final EducationResourceTransactionLock locks;
	private final IAuditLogService audit;
	private final SchoolAssessmentIdentityService identities;

	public record Option(String id, String name, String code) {}
	public record StudentOption(String id, String studentName, String studentNo) {}
	public record StudentPage(List<StudentOption> content, long totalElements) {}
	public record Resource(String code, String name, Integer capacity) {}
	public record AccountLink(String type, String code, String username) {}
	public record Lookups(List<AcademicTerm> terms, List<Subject> subjects, List<Classroom> classrooms,
			List<Option> teachers, List<Option> classes, List<Option> examTypes) {}

	@Transactional(readOnly = true)
	public Lookups lookups(String schoolId, String actor) {
		var campuses = campuses(schoolId, actor);
		var teachersForSchool = campuses.stream().flatMap(id -> teachers
				.findByDepartmentIdAndEnabledTrueOrderByTeacherNo(id).stream())
				.map(item -> new Option(item.getId(), item.getTeacherName(), item.getTeacherNo()))
				.toList();
		var classes = academic.administrativeClasses(scopes.resolve(actor)).stream()
				.filter(item -> campuses.contains(item.getCampusId()))
				.map(item -> new Option(item.getId(), item.getClassName(), item.getClassCode()))
				.toList();
		return new Lookups(terms.findByOrganizationIdOrderByStartDateDesc(schoolId).stream()
				.filter(item -> "ACTIVE".equals(item.getStatus())).toList(),
				academic.visibleSubjects(Set.of(schoolId)).stream().filter(Subject::getEnabled).toList(),
				campuses.stream().flatMap(id -> rooms.findByCampusIdOrderByRoomCode(id).stream())
						.filter(Classroom::getEnabled).toList(),
				teachersForSchool, classes, dictionaries.findByDictCode("EDU_EXAM_TYPE").stream()
						.filter(item -> Integer.valueOf(1).equals(item.getStatus()))
						.filter(item -> item.getDictValue() != null && !item.getDictValue().isBlank())
						.map(item -> new Option(item.getId(), item.getDictName(), item.getDictValue()))
						.toList());
	}

	@Transactional(readOnly = true)
	public StudentPage students(String schoolId, String keyword, int page, String actor) {
		var campuses = campuses(schoolId, actor);
		var data = academic.scheduleStudentOptions(scopes.resolve(actor), keyword, Math.max(0, page), 10, campuses);
		return new StudentPage(data.map(item -> new StudentOption(item.getId(), item.getStudentName(),
				item.getStudentNo())).getContent(), data.getTotalElements());
	}

	@Transactional(readOnly = true)
	public List<StudentOption> studentNames(String schoolId, List<String> ids, String actor) {
		if (ids.size() > 100) {
			throw new IllegalArgumentException("每次最多查询100名考生");
		}
		campuses(schoolId, actor);
		var scope = scopes.resolve(actor);
		return students.findAllById(ids).stream().map(item -> {
			scopes.assertStudentInSchool(scope, schoolId, item);
			scopes.assertStudentAccess(scope, item.getId());
			return new StudentOption(item.getId(), item.getStudentName(), item.getStudentNo());
		}).toList();
	}

	public AcademicTerm createTerm(String schoolId, SchoolRosterSetupService.TermCommand command, String actor) {
		campuses(schoolId, actor);
		return roster.createTerm(schoolId, command);
	}

	public SchoolRosterSetupService.RosterResult importStudents(String schoolId,
			List<SchoolRosterSetupService.RosterRow> rows, String actor) {
		campuses(schoolId, actor);
		return roster.importRoster(schoolId, rows);
	}

	@Transactional
	public Option createResource(String schoolId, String kind, Resource command, String actor) {
		campuses(schoolId, actor);
		var school = organizations.findById(schoolId).orElseThrow();
		String code = required(school.getOrgCode() + ":" + required(command.code(), "编码", 32), "机构组合编码", 64);
		String name = required(command.name(), "名称", 100);
		locks.lockSemester("exam-setup:" + schoolId);
		Option result = switch (kind) {
			case "subjects" -> {
				var subject = new Subject();
				subject.setOrganizationId(schoolId);
				subject.setSubjectCode(code);
				subject.setSubjectName(name);
				subject.setSubjectCategory("GENERAL");
				subject = academic.saveSubject(null, subject);
				yield new Option(subject.getId(), name, code);
			}
			case "rooms" -> {
				var room = new Classroom();
				room.setCampusId(schoolId);
				room.setRoomCode(code);
				room.setRoomName(name);
				room.setCapacity(command.capacity());
				room = scheduling.saveClassroom(null, room);
				yield new Option(room.getId(), name, code);
			}
			case "teachers" -> {
				var teacher = identities.registerTeacher(schoolId, command.code(), command.name(), actor);
				yield new Option(teacher.getId(), name, code);
			}
			default -> throw new IllegalArgumentException("无效的考务资源");
		};
		audit.log(actor, "EXAM_SETUP_RESOURCE_CREATED", "schoolId=" + schoolId + ",kind=" + kind + ",id=" + result.id());
		return result;
	}

	public void linkAccount(String schoolId, AccountLink command, String actor) {
		identities.linkAccount(schoolId, new SchoolAssessmentIdentityService.AccountLink(
				command.type(), command.code(), command.username()), actor);
	}

	private Set<String> campuses(String schoolId, String actor) {
		var scope = scopes.resolve(actor);
		if (!scope.fullAccess() && !scope.campusIds().contains(schoolId)) {
			throw new AccessDeniedException("无权访问该学校的考务资源");
		}
		if (organizations.findById(schoolId).filter(item -> "SCHOOL".equals(item.getOrganizationType())
				&& Integer.valueOf(1).equals(item.getStatus())).isEmpty()) {
			throw new IllegalArgumentException("请选择启用的学校");
		}
		return scopes.scheduleOrganizationCampuses(scope, schoolId);
	}

	private static String required(String value, String label, int max) {
		if (value == null || value.isBlank() || value.strip().length() > max) {
			throw new IllegalArgumentException(label + "不能为空或超过 " + max + " 个字符");
		}
		return value.strip();
	}
}
