package com.chronos.education.homeschool.service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.chronos.education.homeschool.dao.*;
import com.chronos.education.homeschool.dto.HomeSchoolDtos.*;
import com.chronos.education.homeschool.model.*;
import com.chronos.education.scheduling.dao.*;
import com.chronos.education.scheduling.model.*;
import com.chronos.education.scheduling.service.EducationDataScopeService;

@Service
public class HomeSchoolService {
	private final ParentAccountBindingRepository bindings;
	private final HomeNoticeRepository notices;
	private final HomeNoticeTargetRepository targets;
	private final ParentProfileRepository parents;
	private final StudentProfileRepository students;
	private final StudentGuardianRepository guardians;
	private final AdministrativeClassRepository classes;
	private final EducationDataScopeService scopeService;

	public HomeSchoolService(ParentAccountBindingRepository bindings, HomeNoticeRepository notices,
			HomeNoticeTargetRepository targets, ParentProfileRepository parents,
			StudentProfileRepository students, StudentGuardianRepository guardians,
			AdministrativeClassRepository classes, EducationDataScopeService scopeService) {
		this.bindings = bindings; this.notices = notices; this.targets = targets; this.parents = parents;
		this.students = students; this.guardians = guardians; this.classes = classes;
		this.scopeService = scopeService;
	}

	public List<ParentBindingResponse> listBindings() {
		return bindings.findAllByOrderByCreateTimeDesc().stream().map(this::binding).toList();
	}

	@Transactional
	public ParentBindingResponse bind(ParentBindingCommand command) {
		if (command == null || command.parentId() == null || command.username() == null
				|| command.username().isBlank()) throw new IllegalArgumentException("家长和账号不能为空");
		ParentProfile parent = parents.findById(command.parentId())
				.orElseThrow(() -> new IllegalArgumentException("家长档案不存在"));
		if (!"ACTIVE".equals(parent.getStatus())) throw new IllegalStateException("家长档案已失效");
		ParentAccountBinding value = new ParentAccountBinding();
		value.setParentId(parent.getId()); value.setUsername(command.username());
		value.setVerifiedAt(LocalDateTime.now()); value.setStatus("ACTIVE");
		return binding(bindings.save(value));
	}

	@Transactional
	public ParentBindingResponse invalidate(String id) {
		ParentAccountBinding value = bindings.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("家长账号绑定不存在"));
		value.setStatus("INVALIDATED"); value.setInvalidatedAt(LocalDateTime.now());
		return binding(bindings.save(value));
	}

	public List<NoticeResponse> listNotices(String username) {
		EducationDataScope scope = scopeService.resolve(username);
		return notices.findAllByOrderByCreateTimeDesc().stream()
				.filter(n -> scope.fullAccess() || scope.administrativeClassIds().contains(n.getClassId())
						|| canClass(scope, n.getClassId()))
				.map(this::notice).toList();
	}

	@Transactional
	public NoticeResponse createNotice(NoticeCommand command, String username) {
		if (command == null || command.classId() == null || command.title() == null
				|| command.content() == null) throw new IllegalArgumentException("通知内容不完整");
		EducationDataScope scope = scopeService.resolve(username);
		scopeService.assertClassAccess(scope, command.classId());
		HomeNotice value = new HomeNotice();
		value.setSchoolId(command.schoolId()); value.setClassId(command.classId());
		value.setTitle(command.title()); value.setContent(command.content());
		value.setReceiptRequired(Boolean.TRUE.equals(command.receiptRequired()));
		value.setExpireAt(command.expireAt()); value.setPublisherUsername(username);
		return notice(notices.save(value));
	}

	@Transactional
	public NoticeResponse publish(String id, String username) {
		HomeNotice notice = notices.findById(id).orElseThrow(() -> new IllegalArgumentException("通知不存在"));
		EducationDataScope scope = scopeService.resolve(username);
		scopeService.assertClassAccess(scope, notice.getClassId());
		if ("PUBLISHED".equals(notice.getStatus())) return notice(notice);
		List<String> studentIds = students.findByAdministrativeClassId(notice.getClassId()).stream()
				.filter(s -> "ACTIVE".equals(s.getEnrollmentStatus())).map(StudentProfile::getId).toList();
		List<StudentGuardianRelation> relations = studentIds.stream()
				.flatMap(studentId -> guardians.findByStudentIdOrderByCreateTime(studentId).stream())
				.toList();
		Set<String> parentIds = relations.stream().map(StudentGuardianRelation::getParentId).collect(Collectors.toSet());
		Set<String> bound = bindings.findByParentIdInAndStatus(new ArrayList<>(parentIds), "ACTIVE").stream()
				.map(ParentAccountBinding::getParentId).collect(Collectors.toSet());
		for (StudentGuardianRelation relation : relations) {
			if (bound.contains(relation.getParentId())) {
				HomeNoticeTarget target = new HomeNoticeTarget();
				target.setNoticeId(notice.getId()); target.setStudentId(relation.getStudentId());
				target.setParentId(relation.getParentId()); targets.save(target);
			}
		}
		notice.setStatus("PUBLISHED"); notice.setPublishAt(LocalDateTime.now()); notice.setPublisherUsername(username);
		return notice(notices.save(notice));
	}

	@Transactional(readOnly = true)
	public List<NoticeTargetResponse> receipts(String id, String username) {
		HomeNotice notice = notices.findById(id).orElseThrow(() -> new IllegalArgumentException("通知不存在"));
		scopeService.assertClassAccess(scopeService.resolve(username), notice.getClassId());
		return targets.findByNoticeIdOrderByCreateTime(id).stream().map(this::target).toList();
	}

	public List<ChildResponse> children(String username) {
		String parentId = activeParent(username).getParentId();
		return guardians.findByParentIdOrderByCreateTime(parentId).stream()
				.map(r -> students.findById(r.getStudentId()).map(s ->
						new ChildResponse(s.getId(), s.getStudentNo(), s.getStudentName(),
								s.getAdministrativeClassId(), r.getRelationship(), r.getPrimaryGuardian())).orElse(null))
				.filter(Objects::nonNull).toList();
	}

	@Transactional
	public List<FamilyNoticeResponse> familyNotices(String username) {
		String parentId = activeParent(username).getParentId();
		Set<String> currentStudents = guardians.findByParentIdOrderByCreateTime(parentId).stream()
				.map(StudentGuardianRelation::getStudentId).collect(Collectors.toSet());
		List<FamilyNoticeResponse> result = new ArrayList<>();
		for (HomeNoticeTarget target : targets.findByParentIdOrderByCreateTimeDesc(parentId)) {
			if (!currentStudents.contains(target.getStudentId())) continue;
			HomeNotice notice = notices.findById(target.getNoticeId()).orElse(null);
			if (notice == null || !"PUBLISHED".equals(notice.getStatus())) continue;
			if (target.getReadAt() == null) { target.setReadAt(LocalDateTime.now()); targets.save(target); }
			result.add(new FamilyNoticeResponse(notice.getId(), target.getStudentId(), notice.getTitle(),
					notice.getContent(), notice.getReceiptRequired(), notice.getPublishAt(), notice.getExpireAt(),
					notice.getExpireAt() != null && notice.getExpireAt().isBefore(LocalDateTime.now()),
					target.getReadAt(), target.getReceiptStatus(), target.getReceiptAt(), target.getReceiptComment()));
		}
		return result;
	}

	@Transactional
	public NoticeTargetResponse receipt(String id, ReceiptCommand command, String username) {
		String parentId = activeParent(username).getParentId();
		HomeNotice notice = notices.findById(id).orElseThrow(() -> new IllegalArgumentException("通知不存在"));
		HomeNoticeTarget target = targets.findByNoticeIdAndStudentIdAndParentId(id,
				studentIdsFor(parentId).stream().findFirst().orElse(""), parentId).orElse(null);
		if (target == null) {
			target = targets.findByNoticeIdOrderByCreateTime(id).stream()
					.filter(t -> parentId.equals(t.getParentId()) && studentIdsFor(parentId).contains(t.getStudentId()))
					.findFirst().orElseThrow(() -> new AccessDeniedException("无权回执该通知"));
		}
		if (notice.getExpireAt() != null && notice.getExpireAt().isBefore(LocalDateTime.now()))
			throw new IllegalStateException("通知已过期，只读不可回执");
		if (!"RECEIVED".equals(target.getReceiptStatus())) {
			target.setReceiptStatus("RECEIVED"); target.setReceiptAt(LocalDateTime.now());
			target.setReceiptComment(command == null ? null : command.comment());
			target = targets.save(target);
		}
		return target(target);
	}

	private Set<String> studentIdsFor(String parentId) {
		return guardians.findByParentIdOrderByCreateTime(parentId).stream()
				.map(StudentGuardianRelation::getStudentId).collect(Collectors.toSet());
	}
	private ParentAccountBinding activeParent(String username) {
		ParentAccountBinding b = bindings.findByUsernameAndStatus(username, "ACTIVE")
				.orElseThrow(() -> new AccessDeniedException("当前账号不是有效家长账号"));
		ParentProfile p = parents.findById(b.getParentId()).orElseThrow(() -> new AccessDeniedException("家长档案不存在"));
		if (!"ACTIVE".equals(p.getStatus())) throw new AccessDeniedException("家长档案已失效");
		return b;
	}
	private boolean canClass(EducationDataScope scope, String classId) {
		try { scopeService.assertClassAccess(scope, classId); return true; } catch (AccessDeniedException ex) { return false; }
	}
	private ParentBindingResponse binding(ParentAccountBinding v) {
		String parentName = parents.findById(v.getParentId()).map(ParentProfile::getParentName).orElse(null);
		return new ParentBindingResponse(v.getId(), v.getParentId(), parentName, v.getUsername(), v.getStatus(), v.getVerifiedAt(), v.getInvalidatedAt());
	}
	private NoticeResponse notice(HomeNotice v) {
		String className = classes.findById(v.getClassId()).map(AdministrativeClass::getClassName).orElse(null);
		boolean expired = v.getExpireAt() != null && v.getExpireAt().isBefore(LocalDateTime.now());
		return new NoticeResponse(v.getId(), v.getSchoolId(), v.getClassId(), className, v.getTitle(), v.getContent(),
				v.getReceiptRequired(), v.getPublishAt(), v.getExpireAt(), v.getStatus(), expired, v.getPublisherUsername());
	}
	private NoticeTargetResponse target(HomeNoticeTarget v) {
		String parentName = parents.findById(v.getParentId()).map(ParentProfile::getParentName).orElse(null);
		String studentName = students.findById(v.getStudentId()).map(StudentProfile::getStudentName).orElse(null);
		return new NoticeTargetResponse(v.getId(), v.getNoticeId(), v.getStudentId(), v.getParentId(), parentName,
				studentName, v.getDeliveryStatus(), v.getReadAt(), v.getReceiptStatus(), v.getReceiptAt(), v.getReceiptComment());
	}
}
