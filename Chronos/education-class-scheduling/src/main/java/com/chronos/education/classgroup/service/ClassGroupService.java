package com.chronos.education.classgroup.service;

import com.chronos.commons.model.PageView;
import com.chronos.education.classgroup.dao.*;
import com.chronos.education.classgroup.dto.ClassGroupDtos.*;
import com.chronos.education.classgroup.model.*;
import com.chronos.education.homeschool.dao.ParentAccountBindingRepository;
import com.chronos.education.homeschool.model.ParentAccountBinding;
import com.chronos.education.scheduling.dao.*;
import com.chronos.education.scheduling.model.*;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.service.iService.IAuditLogService;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClassGroupService {
	private final ClassGroupRepository groups;
	private final ClassGroupMemberRepository members;
	private final ClassGroupAuditRepository audits;
	private final AdministrativeClassRepository classes;
	private final StudentProfileRepository students;
	private final StudentGuardianRepository guardians;
	private final ParentAccountBindingRepository bindings;
	private final EducationDataScopeService scopes;
	private final IAuditLogService audit;

	public ClassGroupService(ClassGroupRepository groups, ClassGroupMemberRepository members,
			ClassGroupAuditRepository audits, AdministrativeClassRepository classes,
			StudentProfileRepository students, StudentGuardianRepository guardians,
			ParentAccountBindingRepository bindings, EducationDataScopeService scopes,
			IAuditLogService audit) {
		this.groups = groups;
		this.members = members;
		this.audits = audits;
		this.classes = classes;
		this.students = students;
		this.guardians = guardians;
		this.bindings = bindings;
		this.scopes = scopes;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public PageView<GroupResponse> page(String actor, int page, int size) {
		var scope = scopes.resolve(actor);
		List<GroupResponse> visible = groups.findAllByOrderByCreateTimeDesc(PageRequest.of(0, 200))
				.getContent().stream().filter(group -> scope.fullAccess() || canAccess(scope, group.getClassId()))
				.map(this::response).toList();
		return PageView.from(visible, page, size);
	}

	@Transactional
	public GroupResponse create(String actor, String classId, String name, String idempotencyKey) {
		if (classId == null || name == null || name.isBlank()) throw new IllegalArgumentException("班级和群名称不能为空");
		assertClass(actor, classId);
		ClassGroup existing = groups.findByClassId(classId).orElse(null);
		if (existing != null) {
			if (Objects.equals(existing.getLastSyncKey(), idempotencyKey) || idempotencyKey == null) return response(existing);
			throw new IllegalStateException("该班级已有班级群");
		}
		AdministrativeClass clazz = classes.findById(classId)
				.orElseThrow(() -> new IllegalArgumentException("行政班不存在"));
		ClassGroup group = new ClassGroup();
		group.setClassId(classId);
		group.setName(name.trim());
		group.setSchoolId(scopes.requireSchoolForCampus(scopes.resolve(actor), clazz.getCampusId()));
		group.setLastSyncKey(idempotencyKey);
		group = groups.save(group);
		writeAudit(group, actor, "GROUP_CREATE", null, null, "status=ACTIVE");
		return response(group);
	}

	@Transactional
	public GroupResponse changeStatus(String actor, String id, StatusCommand command) {
		ClassGroup group = authorized(actor, id);
		if (command == null || !Set.of("ACTIVE", "SUSPENDED", "CLOSED").contains(command.status()))
			throw new IllegalArgumentException("不支持的群状态");
		if (command.rowVersion() != null && !Objects.equals(command.rowVersion(), group.getRowVersion()))
			throw new IllegalStateException("班级群已被其他人修改");
		if ("CLOSED".equals(group.getStatus()) && !"CLOSED".equals(command.status()))
			throw new IllegalStateException("已关闭的班级群不可恢复");
		if (Objects.equals(group.getStatus(), command.status())) return response(group);
		group.setStatus(command.status());
		group = groups.save(group);
		writeAudit(group, actor, "GROUP_STATUS_" + command.status(), null, null, "status=" + command.status());
		return response(group);
	}

	@Transactional
	public GroupResponse sync(String actor, String id, SyncCommand command) {
		ClassGroup group = authorized(actor, id);
		if ("CLOSED".equals(group.getStatus())) throw new IllegalStateException("已关闭的班级群不可同步");
		if (command != null && command.idempotencyKey() != null
				&& command.idempotencyKey().equals(group.getLastSyncKey())) return response(group);
		List<ClassGroupMember> desired = derive(group);
		Set<String> desiredKeys = desired.stream().map(this::key).collect(Collectors.toSet());
		for (ClassGroupMember current : members.findByGroupIdAndStatus(group.getId(), "ACTIVE")) {
			if (!desiredKeys.contains(key(current))) {
				current.setStatus("REMOVED");
				members.save(current);
				writeAudit(group, actor, "MEMBER_REMOVE", current.getMemberType(), current.getMemberId(), "reason=SYNC");
			}
		}
		for (ClassGroupMember candidate : desired) {
			var existing = members.findByGroupIdAndMemberTypeAndMemberId(
					group.getId(), candidate.getMemberType(), candidate.getMemberId());
			if (existing.isEmpty()) {
				candidate.setGroupId(group.getId());
				ClassGroupMember saved = members.save(candidate);
				writeAudit(group, actor, "MEMBER_ADD", saved.getMemberType(), saved.getMemberId(), "source=DERIVED");
			} else if (!"ACTIVE".equals(existing.get().getStatus())) {
				ClassGroupMember restored = existing.get();
				restored.setStatus("ACTIVE");
				restored.setSource("DERIVED");
				restored.setUsername(candidate.getUsername());
				members.save(restored);
				writeAudit(group, actor, "MEMBER_ADD", restored.getMemberType(), restored.getMemberId(), "source=DERIVED,reason=SYNC");
			}
		}
		group.setLastSyncKey(command == null ? null : command.idempotencyKey());
		groups.save(group);
		writeAudit(group, actor, "GROUP_SYNC", null, null, "memberCount=" + desired.size());
		return response(group);
	}

	@Transactional
	public MemberResponse addMember(String actor, String id, MemberCommand command) {
		ClassGroup group = authorized(actor, id);
		if (!Set.of("STUDENT", "PARENT", "TEACHER").contains(command.memberType()))
			throw new IllegalArgumentException("不支持的成员类型");
		var existing = members.findByGroupIdAndMemberTypeAndMemberId(id, command.memberType(), command.memberId());
		if (existing.isPresent() && "ACTIVE".equals(existing.get().getStatus())) return member(existing.get());
		validateMember(group, command);
		if (existing.isPresent()) {
			ClassGroupMember restored = existing.get();
			restored.setStatus("ACTIVE");
			restored.setSource("EXPLICIT");
			restored.setUsername(command.username());
			return member(members.save(restored));
		}
		ClassGroupMember value = new ClassGroupMember();
		value.setGroupId(id); value.setMemberType(command.memberType()); value.setMemberId(command.memberId());
		value.setUsername(command.username()); value.setRole(command.role() == null ? "MEMBER" : command.role());
		value.setSource("EXPLICIT");
		value = members.save(value);
		writeAudit(group, actor, "MEMBER_ADD", value.getMemberType(), value.getMemberId(), "source=EXPLICIT");
		return member(value);
	}

	@Transactional
	public MemberResponse removeMember(String actor, String id, String memberId) {
		ClassGroup group = authorized(actor, id);
		ClassGroupMember value = members.findByGroupIdAndMemberTypeAndMemberId(id, "PARENT", memberId)
				.or(() -> members.findByGroupIdAndMemberTypeAndMemberId(id, "STUDENT", memberId))
				.or(() -> members.findByGroupIdAndMemberTypeAndMemberId(id, "TEACHER", memberId))
				.orElseThrow(() -> new IllegalArgumentException("群成员不存在"));
		if ("INACTIVE".equals(value.getStatus())) return member(value);
		value.setStatus("REMOVED");
		value = members.save(value);
		writeAudit(group, actor, "MEMBER_REMOVE", value.getMemberType(), value.getMemberId(), "source=" + value.getSource());
		return member(value);
	}

	@Transactional(readOnly = true)
	public PageView<MemberResponse> members(String actor, String id, int page, int size) {
		authorized(actor, id);
		return PageView.from(members.findByGroupIdAndStatusOrderByMemberTypeAscCreateTimeAsc(
				id, "ACTIVE", PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200))).map(this::member));
	}

	@Transactional(readOnly = true)
	public PageView<AuditResponse> audit(String actor, String id, int page, int size) {
		authorized(actor, id);
		return PageView.from(audits.findByGroupIdOrderByCreateTimeDesc(id,
				PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200))).map(a ->
						new AuditResponse(a.getAction(), a.getMemberType(), a.getMemberId(),
								a.getDetail(), a.getActor(), a.getCreateTime())));
	}

	private List<ClassGroupMember> derive(ClassGroup group) {
		List<StudentProfile> activeStudents = students.findByAdministrativeClassId(group.getClassId()).stream()
				.filter(s -> "ACTIVE".equals(s.getEnrollmentStatus())).toList();
		List<ClassGroupMember> result = new ArrayList<>();
		classes.findById(group.getClassId()).map(AdministrativeClass::getHeadTeacherId).ifPresent(teacherId -> {
			if (teacherId != null) {
				ClassGroupMember teacher = new ClassGroupMember();
				teacher.setMemberType("TEACHER"); teacher.setMemberId(teacherId);
				teacher.setRole("OWNER"); teacher.setSource("DERIVED");
				result.add(teacher);
			}
		});
		for (StudentProfile student : activeStudents) {
			ClassGroupMember studentMember = new ClassGroupMember();
			studentMember.setMemberType("STUDENT"); studentMember.setMemberId(student.getId());
			studentMember.setRole("MEMBER"); studentMember.setSource("DERIVED");
			result.add(studentMember);
			for (StudentGuardianRelation relation : guardians.findByStudentIdOrderByCreateTime(student.getId())) {
				ParentAccountBinding binding = bindings.findByParentIdAndStatus(relation.getParentId(), "ACTIVE")
						.stream().findFirst().orElse(null);
				if (binding == null) continue;
				ClassGroupMember parent = new ClassGroupMember();
				parent.setMemberType("PARENT"); parent.setMemberId(relation.getParentId());
				parent.setUsername(binding.getUsername()); parent.setRole("MEMBER"); parent.setSource("DERIVED");
				result.add(parent);
			}
		}
		return result.stream().collect(Collectors.toMap(this::key, x -> x, (a, b) -> a)).values().stream().toList();
	}

	private void validateMember(ClassGroup group, MemberCommand command) {
		if ("STUDENT".equals(command.memberType())) {
			StudentProfile s = students.findById(command.memberId()).orElseThrow(() -> new IllegalArgumentException("学生不存在"));
			if (!group.getClassId().equals(s.getAdministrativeClassId()) || !"ACTIVE".equals(s.getEnrollmentStatus()))
				throw new AccessDeniedException("学生不属于该有效班级");
		} else if ("PARENT".equals(command.memberType())) {
			boolean related = students.findByAdministrativeClassId(group.getClassId()).stream()
					.anyMatch(s -> guardians.findByStudentIdAndParentId(s.getId(), command.memberId()).isPresent());
			if (!related || bindings.findByParentIdAndStatus(command.memberId(), "ACTIVE").isEmpty())
				throw new AccessDeniedException("家长没有该班级的有效监护关系");
		}
	}

	private ClassGroup authorized(String actor, String id) {
		ClassGroup group = groups.findById(id).orElseThrow(() -> new IllegalArgumentException("班级群不存在"));
		assertClass(actor, group.getClassId());
		return group;
	}
	private void assertClass(String actor, String classId) {
		var scope = scopes.resolve(actor);
		if (scope.fullAccess()) return;
		AdministrativeClass clazz = classes.findById(classId)
				.orElseThrow(() -> new IllegalArgumentException("行政班不存在"));
		if (clazz.getHeadTeacherId() == null || !scope.teacherIds().contains(clazz.getHeadTeacherId()))
			throw new AccessDeniedException("仅班主任或管理员可管理班级群");
	}
	private boolean canAccess(EducationDataScope scope, String classId) {
		try { scopes.assertClassAccess(scope, classId); return true; }
		catch (AccessDeniedException ex) { return false; }
	}
	private String key(ClassGroupMember m) { return m.getMemberType() + ":" + m.getMemberId(); }
	private GroupResponse response(ClassGroup g) {
		String className = classes.findById(g.getClassId()).map(AdministrativeClass::getClassName).orElse(null);
		return new GroupResponse(g.getId(), g.getClassId(), className, g.getName(), g.getStatus(),
				members.findByGroupIdAndStatus(g.getId(), "ACTIVE").size(), g.getRowVersion());
	}
	private MemberResponse member(ClassGroupMember m) {
		return new MemberResponse(m.getId(), m.getMemberType(), m.getMemberId(), m.getUsername(),
				m.getRole(), m.getSource(), m.getStatus());
	}
	private void writeAudit(ClassGroup group, String actor, String action, String type, String memberId, String detail) {
		ClassGroupAudit entry = new ClassGroupAudit();
		entry.setGroupId(group.getId()); entry.setAction(action); entry.setMemberType(type);
		entry.setMemberId(memberId); entry.setDetail(detail); entry.setActor(actor);
		audits.save(entry);
		audit.log(actor, "EDU_CLASS_GROUP_" + action, "groupId=" + group.getId() + "," + detail);
	}
}
