package com.chronos.education.homeschool.service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.chronos.education.homeschool.dao.*;
import com.chronos.education.homeschool.dto.HomeSchoolDtos.*;
import com.chronos.education.homeschool.dto.HomeNoticeEventContracts.HomeNoticePublishedV1;
import com.chronos.education.homeschool.model.*;
import com.chronos.education.scheduling.dao.*;
import com.chronos.education.scheduling.model.*;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.education.grade.service.DomainEventOutboxService;
import com.chronos.education.grade.service.GradeCenterService;
import com.chronos.education.grade.model.CourseGrade;
import com.chronos.Idao.IAdminUserRepository;
import com.chronos.model.pojo.AdminUser;
import com.chronos.service.iService.IAuditLogService;

@Service
public class HomeSchoolService {
	private final EducationUserBindingRepository bindings;
	private final HomeNoticeRepository notices;
	private final HomeNoticeTargetRepository targets;
	private final ParentProfileRepository parents;
	private final StudentProfileRepository students;
	private final StudentGuardianRepository guardians;
	private final AdministrativeClassRepository classes;
	private final EducationDataScopeService scopeService;
	private final IAuditLogService audit;
	private final DomainEventOutboxService domainEvents;
	private final GradeCenterService gradeCenter;
	private final IAdminUserRepository accounts;

	@Autowired
	public HomeSchoolService(EducationUserBindingRepository bindings, HomeNoticeRepository notices,
			HomeNoticeTargetRepository targets, ParentProfileRepository parents,
			StudentProfileRepository students, StudentGuardianRepository guardians,
			AdministrativeClassRepository classes, EducationDataScopeService scopeService,
			IAuditLogService audit, DomainEventOutboxService domainEvents, GradeCenterService gradeCenter,
			IAdminUserRepository accounts) {
		this.bindings = bindings; this.notices = notices; this.targets = targets; this.parents = parents;
		this.students = students; this.guardians = guardians; this.classes = classes;
		this.scopeService = scopeService; this.audit = audit; this.domainEvents = domainEvents;
		this.gradeCenter = gradeCenter;
		this.accounts = accounts;
	}

	public List<ParentBindingResponse> listBindings() {
		return bindingResponses(bindings.findAllByProfileTypeOrderByCreateTimeDesc("PARENT"));
	}

	public Page<ParentBindingResponse> listBindings(int page, int size) {
		PageRequest request = PageRequest.of(Math.max(0, page), boundedSize(size));
		Page<EducationUserBinding> result = bindings.findByProfileType("PARENT", request);
		return new PageImpl<>(bindingResponses(result.getContent()), request, result.getTotalElements());
	}

	@Transactional
	public ParentBindingResponse bind(ParentBindingCommand command) {
		if (command == null || command.parentId() == null || command.username() == null
				|| command.username().isBlank()) throw new IllegalArgumentException("家长和账号不能为空");
		ParentProfile parent = parents.findById(command.parentId())
				.orElseThrow(() -> new IllegalArgumentException("家长档案不存在"));
		if (!"ACTIVE".equals(parent.getStatus())) throw new IllegalStateException("家长档案已失效");
		AdminUser account = accounts.findByUsername(command.username().trim());
		if (account == null || !Integer.valueOf(1).equals(account.getStatus())
				|| Boolean.TRUE.equals(account.getAccountLocked())) {
			throw new IllegalArgumentException("门户登录账号不存在或不可用");
		}
		EducationUserBinding accountBinding = bindings.findByUsername(account.getUsername()).orElse(null);
		if (accountBinding != null && !"PARENT".equals(accountBinding.getProfileType()))
			throw new IllegalStateException("登录账号已绑定其他教务档案");
		EducationUserBinding existingParent = bindings.findByProfileTypeAndProfileId("PARENT", parent.getId())
				.orElse(null);
		EducationUserBinding value = accountBinding != null ? accountBinding
				: existingParent != null && !"ACTIVE".equals(existingParent.getStatus())
					? existingParent : new EducationUserBinding();
		if (value.getId() != null && "ACTIVE".equals(value.getStatus())
				&& !Objects.equals(value.getProfileId(), parent.getId())) {
			throw new IllegalStateException("登录账号已绑定其他家长");
		}
		if (existingParent != null && "ACTIVE".equals(existingParent.getStatus())
				&& !existingParent.getUsername().equals(account.getUsername()))
			throw new IllegalStateException("家长档案已绑定其他登录账号，请先解除原绑定");
		if (accountBinding != null && existingParent != null && !accountBinding.getId().equals(existingParent.getId()))
			throw new IllegalStateException("账号与家长档案存在不同的历史绑定，请先处理历史记录");
		value.setProfileType("PARENT"); value.setProfileId(parent.getId());
		value.setUsername(account.getUsername());
		value.setVerifiedAt(LocalDateTime.now()); value.setInvalidatedAt(null); value.setStatus("ACTIVE");
		value = bindings.save(value);
		ParentBindingResponse response = binding(value);
		if (audit != null) audit.log(command.username(), "EDU_HOME_PARENT_BINDING_CREATE",
				"bindingId=" + value.getId() + ",parentId=" + value.getProfileId());
		return response;
	}

	@Transactional
	public ParentBindingResponse invalidate(String id) {
		EducationUserBinding value = bindings.findById(id)
				.filter(binding -> "PARENT".equals(binding.getProfileType()))
				.orElseThrow(() -> new IllegalArgumentException("家长账号绑定不存在"));
		value.setStatus("INVALIDATED"); value.setInvalidatedAt(LocalDateTime.now());
		ParentBindingResponse response = binding(bindings.save(value));
		if (audit != null) audit.log(value.getUsername(), "EDU_HOME_PARENT_BINDING_INVALIDATE",
				"bindingId=" + value.getId() + ",parentId=" + value.getProfileId());
		return response;
	}

	public List<NoticeResponse> listNotices(String username) {
		EducationDataScope scope = scopeService.resolve(username);
		return notices.findAllByOrderByCreateTimeDesc().stream()
				.filter(n -> scope.fullAccess() || scope.administrativeClassIds().contains(n.getClassId())
						|| canClass(scope, n.getClassId()))
				.map(this::notice).toList();
	}

	public Page<NoticeResponse> listNotices(String username, int page, int size) {
		List<NoticeResponse> visible = listNotices(username);
		PageRequest request = PageRequest.of(Math.max(0, page), boundedSize(size));
		int from = Math.min((int) request.getOffset(), visible.size());
		int to = Math.min(from + request.getPageSize(), visible.size());
		return new PageImpl<>(visible.subList(from, to), request, visible.size());
	}

	@Transactional
	public NoticeResponse createNotice(NoticeCommand command, String username) {
		if (command == null || command.classId() == null || command.title() == null
				|| command.content() == null || command.title().isBlank()
				|| command.content().isBlank()) throw new IllegalArgumentException("通知内容不完整");
		if (command.expireAt() != null && !command.expireAt().isAfter(LocalDateTime.now())) {
			throw new IllegalArgumentException("通知过期时间必须晚于当前时间");
		}
		EducationDataScope scope = scopeService.resolve(username);
		scopeService.assertClassAccess(scope, command.classId());
		AdministrativeClass administrativeClass = classes.findById(command.classId())
				.orElseThrow(() -> new IllegalArgumentException("行政班不存在"));
		String schoolId = scopeService.requireSchoolForCampus(scope, administrativeClass.getCampusId());
		HomeNotice value = new HomeNotice();
		value.setSchoolId(schoolId); value.setClassId(command.classId());
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
		if (!"DRAFT".equals(notice.getStatus())) {
			throw new IllegalStateException("通知当前状态不可发布");
		}
		if (notice.getExpireAt() != null && !notice.getExpireAt().isAfter(LocalDateTime.now())) {
			throw new IllegalStateException("通知已过期，无法发布");
		}
		List<String> studentIds = students.findByAdministrativeClassId(notice.getClassId()).stream()
				.filter(s -> "ACTIVE".equals(s.getEnrollmentStatus())).map(StudentProfile::getId).toList();
		List<StudentGuardianRelation> relations = studentIds.stream()
				.flatMap(studentId -> guardians.findByStudentIdOrderByCreateTime(studentId).stream())
				.toList();
		Set<String> parentIds = relations.stream().map(StudentGuardianRelation::getParentId).collect(Collectors.toSet());
		Set<String> bound = bindings.findByProfileTypeAndProfileIdInAndStatus("PARENT", new ArrayList<>(parentIds), "ACTIVE").stream()
				.map(EducationUserBinding::getProfileId).collect(Collectors.toSet());
		int targetCount = 0;
		for (StudentGuardianRelation relation : relations) {
			if (bound.contains(relation.getParentId())) {
				if (targets.findByNoticeIdAndStudentIdAndParentId(notice.getId(),
						relation.getStudentId(), relation.getParentId()).isEmpty()) {
					HomeNoticeTarget target = new HomeNoticeTarget();
					target.setNoticeId(notice.getId()); target.setStudentId(relation.getStudentId());
					target.setParentId(relation.getParentId()); targets.save(target);
					targetCount++;
				}
			}
		}
		notice.setStatus("PUBLISHED"); notice.setPublishAt(LocalDateTime.now()); notice.setPublisherUsername(username);
		NoticeResponse response = notice(notices.save(notice));
		if (audit != null) audit.log(username, "EDU_HOME_NOTICE_PUBLISH",
				"noticeId=" + notice.getId() + ",targetCount=" + targetCount);
		if (domainEvents != null) {
			HomeNoticePublishedV1 event = new HomeNoticePublishedV1(
					"HOME_NOTICE_PUBLISHED:" + notice.getId(),
					"HomeNoticePublishedV1", java.time.OffsetDateTime.now(), 1,
					notice.getId(), notice.getClassId(), targetCount, username);
			domainEvents.enqueue(event.eventType(), notice.getId(), event.eventId(), event);
		}
		return response;
	}

	@Transactional(readOnly = true)
	public List<NoticeTargetResponse> receipts(String id, String username) {
		HomeNotice notice = notices.findById(id).orElseThrow(() -> new IllegalArgumentException("通知不存在"));
		scopeService.assertClassAccess(scopeService.resolve(username), notice.getClassId());
		return targets.findByNoticeIdOrderByCreateTime(id).stream().map(this::target).toList();
	}

	public List<ChildResponse> children(String username) {
		String parentId = activeParent(username).getProfileId();
		return guardians.findByParentIdOrderByCreateTime(parentId).stream()
				.map(r -> students.findById(r.getStudentId()).map(s ->
						new ChildResponse(s.getId(), s.getStudentNo(), s.getStudentName(),
								s.getAdministrativeClassId(), r.getRelationship(), r.getPrimaryGuardian())).orElse(null))
				.filter(Objects::nonNull).toList();
	}

	@Transactional(readOnly = true)
	public List<CourseGrade> familyGrades(String username) {
		String parentId = activeParent(username).getProfileId();
		List<String> activeChildren = guardians.findByParentIdOrderByCreateTime(parentId).stream()
				.map(StudentGuardianRelation::getStudentId)
				.map(students::findById)
				.flatMap(Optional::stream)
				.filter(s -> "ACTIVE".equals(s.getEnrollmentStatus()))
				.map(StudentProfile::getId)
				.toList();
		List<CourseGrade> result = activeChildren.stream()
				.flatMap(studentId -> gradeCenter.studentGrades(studentId).stream())
				.toList();
		if (audit != null) audit.log(username, "EDU_HOME_PARENT_GRADE_VIEW",
				"parentId=" + parentId + ",studentCount=" + activeChildren.size() + ",gradeCount=" + result.size());
		return result;
	}

	@Transactional
	public List<FamilyNoticeResponse> familyNotices(String username) {
		String parentId = activeParent(username).getProfileId();
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
		String parentId = activeParent(username).getProfileId();
		HomeNotice notice = notices.findById(id).orElseThrow(() -> new IllegalArgumentException("通知不存在"));
		Set<String> currentStudents = studentIdsFor(parentId);
		List<HomeNoticeTarget> matchingTargets = targets.findByNoticeIdOrderByCreateTime(id).stream()
				.filter(t -> parentId.equals(t.getParentId()) && currentStudents.contains(t.getStudentId()))
				.toList();
		HomeNoticeTarget target = matchingTargets.stream().findFirst()
				.orElseThrow(() -> new AccessDeniedException("无权回执该通知"));
		if (!"PUBLISHED".equals(notice.getStatus())) {
			throw new IllegalStateException("通知尚未发布");
		}
		if (!Boolean.TRUE.equals(notice.getReceiptRequired())) {
			throw new IllegalStateException("该通知无需回执");
		}
		if (notice.getExpireAt() != null && notice.getExpireAt().isBefore(LocalDateTime.now()))
			throw new IllegalStateException("通知已过期，只读不可回执");
		for (HomeNoticeTarget current : matchingTargets) {
			if (!"RECEIVED".equals(current.getReceiptStatus())) {
				current.setReceiptStatus("RECEIVED");
				current.setReceiptAt(LocalDateTime.now());
				current.setReadAt(current.getReadAt() == null ? LocalDateTime.now() : current.getReadAt());
				current.setReceiptComment(command == null ? null : command.comment());
				targets.save(current);
			}
		}
		if (audit != null) audit.log(username, "EDU_HOME_NOTICE_RECEIPT",
				"noticeId=" + id + ",parentId=" + parentId + ",targetCount=" + matchingTargets.size());
		return target(target);
	}

	private Set<String> studentIdsFor(String parentId) {
		return guardians.findByParentIdOrderByCreateTime(parentId).stream()
				.map(StudentGuardianRelation::getStudentId).collect(Collectors.toSet());
	}
	private EducationUserBinding activeParent(String username) {
		EducationUserBinding b = bindings.findByUsernameAndProfileTypeAndStatus(username, "PARENT", "ACTIVE")
				.orElseThrow(() -> new AccessDeniedException("当前账号不是有效家长账号"));
		ParentProfile p = parents.findById(b.getProfileId()).orElseThrow(() -> new AccessDeniedException("家长档案不存在"));
		if (!"ACTIVE".equals(p.getStatus())) throw new AccessDeniedException("家长档案已失效");
		return b;
	}
	private int boundedSize(int size) {
		return Math.min(Math.max(size, 1), 100);
	}
	private boolean canClass(EducationDataScope scope, String classId) {
		try { scopeService.assertClassAccess(scope, classId); return true; } catch (AccessDeniedException ex) { return false; }
	}
	private ParentBindingResponse binding(EducationUserBinding v) {
		return bindingResponses(List.of(v)).get(0);
	}
	private List<ParentBindingResponse> bindingResponses(List<EducationUserBinding> values) {
		if (values.isEmpty()) return List.of();
		Map<String, ParentProfile> parentById = parents.findAllById(values.stream()
				.map(EducationUserBinding::getProfileId).distinct().toList()).stream()
				.collect(Collectors.toMap(ParentProfile::getId, Function.identity()));
		Map<String, AdminUser> accountByUsername = accounts.findByUsernameIn(values.stream()
				.map(EducationUserBinding::getUsername).distinct().toList()).stream()
				.collect(Collectors.toMap(AdminUser::getUsername, Function.identity()));
		return values.stream().map(value -> {
			ParentProfile parent = parentById.get(value.getProfileId());
			AdminUser account = accountByUsername.get(value.getUsername());
			boolean accountAvailable = account != null && Integer.valueOf(1).equals(account.getStatus())
					&& !Boolean.TRUE.equals(account.getAccountLocked());
			return new ParentBindingResponse(value.getId(), value.getProfileId(),
					parent == null ? null : parent.getParentName(), value.getUsername(), value.getStatus(),
					value.getVerifiedAt(), value.getInvalidatedAt(), accountAvailable);
		}).toList();
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
