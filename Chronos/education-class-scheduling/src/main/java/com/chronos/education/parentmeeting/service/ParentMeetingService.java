package com.chronos.education.parentmeeting.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.education.meeting.model.MeetingCommands;
import com.chronos.education.meeting.model.MeetingView;
import com.chronos.education.meeting.service.MeetingCenterService;
import com.chronos.education.parentmeeting.dao.ParentMeetingScopeRepository;
import com.chronos.education.parentmeeting.model.ParentMeetingCommands;
import com.chronos.education.parentmeeting.model.ParentMeetingScope;
import com.chronos.education.scheduling.dao.StudentGuardianRepository;
import com.chronos.education.scheduling.dao.StudentProfileRepository;
import com.chronos.education.scheduling.dao.EducationUserBindingRepository;
import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.education.scheduling.model.StudentGuardianRelation;
import com.chronos.education.scheduling.model.StudentProfile;
import com.chronos.education.scheduling.model.EducationUserBinding;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.service.iService.IAuditLogService;
import com.chronos.Idao.IAdminUserRepository;
import com.chronos.model.pojo.AdminUser;

@Service
public class ParentMeetingService {
	private final MeetingCenterService meetings;
	private final ParentMeetingScopeRepository scopes;
	private final StudentProfileRepository students;
	private final StudentGuardianRepository guardians;
	private final EducationUserBindingRepository bindings;
	private final EducationDataScopeService dataScope;
	private final IAuditLogService audit;
	private final IAdminUserRepository accounts;

	public ParentMeetingService(MeetingCenterService meetings, ParentMeetingScopeRepository scopes,
			StudentProfileRepository students, StudentGuardianRepository guardians,
			EducationUserBindingRepository bindings, EducationDataScopeService dataScope,
			IAuditLogService audit, IAdminUserRepository accounts) {
		this.meetings = meetings; this.scopes = scopes; this.students = students;
		this.guardians = guardians; this.bindings = bindings; this.dataScope = dataScope; this.audit = audit;
		this.accounts = accounts;
	}

	@Transactional
	public MeetingView create(ParentMeetingCommands.Create command, String organizer) {
		String scopeType = normalizeScope(command.scopeType());
		EducationDataScope scope = dataScope.resolve(organizer);
		List<StudentProfile> targets = resolveStudents(scopeType, command.scopeId(), scope);
		Set<String> studentIds = targets.stream().map(StudentProfile::getId).collect(Collectors.toSet());
		List<StudentGuardianRelation> relations = guardians.findByStudentIdIn(studentIds.stream().toList());
		List<String> parentIds = relations.stream().map(StudentGuardianRelation::getParentId).distinct().toList();
		List<String> boundUsernames = bindings.findByProfileTypeAndProfileIdInAndStatus("PARENT", parentIds, "ACTIVE").stream()
				.map(EducationUserBinding::getUsername).distinct().toList();
		List<String> usernames = boundUsernames.isEmpty() ? List.of() : accounts.findByUsernameIn(boundUsernames).stream()
				.filter(account -> Integer.valueOf(1).equals(account.getStatus())
						&& !Boolean.TRUE.equals(account.getAccountLocked()))
				.map(AdminUser::getUsername).toList();
		if (usernames.isEmpty()) throw new IllegalStateException(
				"范围内没有可邀请的有效家长账号；请在家长管理核对监护关系、门户账号绑定及账号可用性");
		MeetingCommands.Save save = new MeetingCommands.Save(command.title(), command.agenda(),
				command.meetingType(), command.startTime(), command.endTime(), command.roomId(),
				command.meetingProvider(), command.externalMeetingId(), command.joinUrl(),
				command.onlineAccessCode(), usernames, null);
		MeetingView view = meetings.saveMeeting(null, save, organizer);
		ParentMeetingScope association = new ParentMeetingScope();
		association.setMeetingId(view.meeting().getId());
		association.setScopeType(scopeType);
		association.setScopeId(command.scopeId());
		scopes.save(association);
		audit.log(organizer, "EDU_PARENT_MEETING_CREATE",
				"meetingId=" + view.meeting().getId() + ",scopeType=" + scopeType + ",scopeId=" + command.scopeId());
		return view;
	}

	@Transactional(readOnly = true)
	public List<MeetingView> all(String username, boolean manage) {
		Set<String> ids = scopes.findAll().stream().map(ParentMeetingScope::getMeetingId).collect(Collectors.toSet());
		return meetings.allMeetings().stream()
				.filter(view -> ids.contains(view.meeting().getId()))
				.filter(view -> manage || username.equals(view.meeting().getOrganizerUsername()))
				.toList();
	}

	public MeetingView publish(String id, String username) {
		assertParentMeeting(id);
		return meetings.publish(id, username);
	}

	@Transactional(readOnly = true)
	public List<MeetingView> mine(String username) {
		Set<String> ids = scopes.findAll().stream().map(ParentMeetingScope::getMeetingId).collect(Collectors.toSet());
		return meetings.myMeetings(username).stream().filter(view -> ids.contains(view.meeting().getId())).toList();
	}

	public MeetingView respond(String id, MeetingCommands.Response response, String username) {
		assertParentMeeting(id);
		return meetings.respond(id, response, username);
	}

	private List<StudentProfile> resolveStudents(String type, String id, EducationDataScope scope) {
		if ("CLASS".equals(type)) {
			dataScope.assertClassAccess(scope, id);
			return students.findByAdministrativeClassId(id).stream().filter(s -> active(s) && dataScope.canAccessStudent(scope, s)).toList();
		}
		if ("GRADE".equals(type)) {
			List<StudentProfile> result = students.findAll().stream()
					.filter(s -> id.equals(s.getGradeId()) && active(s)).toList();
			if (result.isEmpty() || result.stream().anyMatch(s -> !dataScope.canAccessStudent(scope, s)))
				throw new AccessDeniedException("无权访问该年级范围");
			return result;
		}
		if ("STUDENT".equals(type)) {
			dataScope.assertStudentAccess(scope, id);
			return students.findById(id).filter(this::active).stream().toList();
		}
		throw new IllegalArgumentException("家长会范围只允许 CLASS、GRADE 或 STUDENT");
	}

	private boolean active(StudentProfile student) { return "ACTIVE".equals(student.getEnrollmentStatus()); }
	private void assertParentMeeting(String id) {
		if (scopes.findByMeetingId(id).isEmpty()) throw new AccessDeniedException("不是家长会");
	}
	private String normalizeScope(String value) { return value == null ? "" : value.trim().toUpperCase(); }
}
