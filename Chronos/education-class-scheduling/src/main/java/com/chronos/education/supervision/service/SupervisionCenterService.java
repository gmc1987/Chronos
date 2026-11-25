package com.chronos.education.supervision.service;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.IOrganizationRepository;
import com.chronos.education.grade.service.DomainEventOutboxService;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.EducationUserBindingRepository;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.model.pojo.Organization;
import com.chronos.education.supervision.dao.*;
import com.chronos.education.supervision.dto.SupervisionDtos.ObservationCommand;
import com.chronos.education.supervision.model.*;
import com.chronos.education.scheduling.dao.TeacherAcademicProfileRepository;
import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.education.scheduling.model.ScheduleEntry;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.education.scheduling.service.EducationIdentityService;
import com.chronos.education.scheduling.service.SchedulePlanVersionService;
import com.chronos.form.FormService;
import com.chronos.model.form.FormInstance;
import com.chronos.model.form.FormDefinition;
import com.chronos.model.form.FormField;
import com.chronos.service.iService.IAuditLogService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 督导中心第一切片。服务层维护状态机和任务分配边界，Controller 不得直接修改状态。
 */
@Service
public class SupervisionCenterService {
	private final SupervisionPlanRepository plans;
	private final SupervisionAssignmentRepository assignments;
	private final SupervisionRecordRepository records;
	private final SupervisionIssueRepository issues;
	private final SupervisionRectificationRepository rectifications;
	private final SupervisionReviewRepository reviews;
	private final EducationDataScopeService dataScopes;
	private final EducationIdentityService identities;
	private final DomainEventOutboxService events;
	private final IAuditLogService audit;
	private final FormService forms;
	private final SchedulePlanVersionService scheduleVersions;
	private final SupervisionCheckInProofProvider checkInProofs;
	private TeacherAcademicProfileRepository teacherProfiles;
	private IOrganizationRepository organizations;
	private IAdminUserRepository accounts;
	private EducationUserBindingRepository bindings;
	private CourseOfferingRepository offerings;
	private com.chronos.security.AdminUserDetailsService userDetails;
	private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
	private final Map<String, Instant> consumedProofs = new ConcurrentHashMap<>();

	@Autowired
	void configureObservation(TeacherAcademicProfileRepository teacherProfiles,
			com.chronos.security.AdminUserDetailsService userDetails) {
		this.teacherProfiles = teacherProfiles;
		this.userDetails = userDetails;
	}

	@Autowired
	void configureAssignmentDirectory(IOrganizationRepository organizations, IAdminUserRepository accounts,
			EducationUserBindingRepository bindings, CourseOfferingRepository offerings) {
		this.organizations = organizations;
		this.accounts = accounts;
		this.bindings = bindings;
		this.offerings = offerings;
	}

	@Autowired
	public SupervisionCenterService(
			SupervisionPlanRepository plans,
			SupervisionAssignmentRepository assignments,
			SupervisionRecordRepository records,
			SupervisionIssueRepository issues,
			SupervisionRectificationRepository rectifications,
			SupervisionReviewRepository reviews,
			EducationDataScopeService dataScopes,
			EducationIdentityService identities,
			DomainEventOutboxService events,
			IAuditLogService audit,
			FormService forms,
			SchedulePlanVersionService scheduleVersions,
			@Nullable SupervisionCheckInProofProvider checkInProofs) {
		this.plans = plans;
		this.assignments = assignments;
		this.records = records;
		this.issues = issues;
		this.rectifications = rectifications;
		this.reviews = reviews;
		this.dataScopes = dataScopes;
		this.identities = identities;
		this.events = events;
		this.audit = audit;
		this.forms = forms;
		this.scheduleVersions = scheduleVersions;
		this.checkInProofs = checkInProofs;
	}

	public SupervisionCenterService(
			SupervisionPlanRepository plans,
			SupervisionAssignmentRepository assignments,
			SupervisionRecordRepository records,
			SupervisionIssueRepository issues,
			SupervisionRectificationRepository rectifications,
			DomainEventOutboxService events,
			IAuditLogService audit,
			EducationDataScopeService dataScopes) {
		this(plans, assignments, records, issues, rectifications, null, dataScopes, null, events, audit,
				null, null, null);
	}

	@Transactional
	public SupervisionPlan publishPlan(String id, String actor) {
		SupervisionPlan plan = plans.findById(id).orElseThrow();
		assertPlanAccess(plan, actor);
		requireState(plan.getStatus(), "DRAFT");
		plan.setStatus("PUBLISHED");
		audit.log(actor, "EDU_SUPERVISION_PLAN_PUBLISH", "planId=" + id);
		return plans.save(plan);
	}

	@Transactional
	public SupervisionPlan completePlan(String id, String actor) {
		SupervisionPlan plan = plans.findById(id).orElseThrow();
		assertPlanAccess(plan, actor);
		requireState(plan.getStatus(), "IN_PROGRESS");
		List<SupervisionAssignment> planAssignments = assignments.findByPlanId(id);
		if (planAssignments.isEmpty() || planAssignments.stream().anyMatch(item -> !"COMPLETED".equals(item.getStatus()))) {
			throw new IllegalStateException("督导计划仍有未完成任务");
		}
		for (SupervisionAssignment item : planAssignments) {
			records.findByAssignmentId(item.getId()).ifPresent(record -> {
				if (issues.findByRecordId(record.getId()).stream().anyMatch(issue -> !"CLOSED".equals(issue.getStatus()))) {
					throw new IllegalStateException("督导计划仍有未关闭的整改问题");
				}
			});
		}
		plan.setStatus("COMPLETED");
		audit.log(actor, "EDU_SUPERVISION_PLAN_COMPLETE", "planId=" + id);
		return plans.save(plan);
	}

	@Transactional
	public SupervisionPlan archivePlan(String id, String actor) {
		SupervisionPlan plan = plans.findById(id).orElseThrow();
		assertPlanAccess(plan, actor);
		requireState(plan.getStatus(), "COMPLETED");
		plan.setStatus("ARCHIVED");
		audit.log(actor, "EDU_SUPERVISION_PLAN_ARCHIVE", "planId=" + id);
		return plans.save(plan);
	}

	@Transactional(readOnly = true)
	public List<SupervisionPlan> listPlans(String actor, String schoolId) {
		var scope = dataScopes.resolve(actor);
		if (schoolId != null && !schoolId.isBlank()) {
			return plans.findBySchoolIdOrderByCreateTimeDesc(resolveSchool(scope, schoolId));
		}
		if (scope.fullAccess()) {
			return plans.findAll(org.springframework.data.domain.Sort.by("createTime").descending());
		}
		return plans.findBySchoolIdInOrderByCreateTimeDesc(scope.schoolIds());
	}

	@Transactional
	public SupervisionPlan createPlan(String actor, String name,
			java.time.LocalDate startDate, java.time.LocalDate endDate, String campusId, String schoolId) {
		if (endDate.isBefore(startDate)) {
			throw new IllegalArgumentException("督导计划结束日期不能早于开始日期");
		}
		EducationDataScope scope = dataScopes.resolve(actor);
		String selectedSchool = resolveSchool(scope, schoolId);
		if (campusId != null && !campusId.isBlank()) {
			dataScopes.assertCampusAccess(scope, campusId);
			if (!schoolOrganizations(selectedSchool).contains(campusId)) {
				throw new IllegalArgumentException("校区不属于所选学校");
			}
		}
		SupervisionPlan plan = new SupervisionPlan();
		plan.setSchoolId(selectedSchool);
		plan.setName(name);
		plan.setStartDate(startDate);
		plan.setEndDate(endDate);
		plan.setCampusId(campusId);
		audit.log(actor, "EDU_SUPERVISION_PLAN_CREATE", "name=" + name);
		return plans.save(plan);
	}

	@Transactional
	public SupervisionAssignment createAssignment(String actor, String planId,
			String supervisorId, String teacherId, String scheduleEntryId, String campusId) {
		SupervisionPlan plan = plans.findById(planId)
				.orElseThrow(() -> new IllegalArgumentException("督导计划不存在"));
		EducationDataScope scope = dataScopes.resolve(actor);
		assertPlanAccess(plan, scope);
		if (plan.getCampusId() != null && campusId != null && !plan.getCampusId().equals(campusId)) {
			throw new IllegalArgumentException("任务校区与督导计划不一致");
		}
		if (!"PUBLISHED".equals(plan.getStatus())) {
			throw new IllegalStateException("仅可向本校已发布计划分配任务");
		}
		requireAssignmentSchool(plan);
		dataScopes.assertScheduleEntryAccess(scope, scheduleEntryId);
		if (campusId != null) dataScopes.assertCampusAccess(scope, campusId);
		ScheduleEntry publishedEntry = scheduleVersions == null ? null : scheduleVersions.requirePublishedEntry(scheduleEntryId);
		if (publishedEntry == null) {
			throw new IllegalStateException("SUPERVISION_PUBLISHED_SCHEDULE_PROVIDER_UNAVAILABLE");
		}
		var offering = offerings.findById(publishedEntry.getOfferingId())
				.orElseThrow(() -> new IllegalArgumentException("已发布课次的教学班不存在"));
		if (!schoolOrganizations(plan.getSchoolId()).contains(offering.getCampusId())
				|| (plan.getCampusId() != null && !plan.getCampusId().equals(offering.getCampusId()))
				|| (campusId != null && !campusId.equals(offering.getCampusId()))) {
			throw new AccessDeniedException("已发布课次不属于督导计划的学校或校区");
		}
		if (!teacherId.equals(offering.getTeacherId())) {
			throw new IllegalArgumentException("授课教师与已发布课次不一致");
		}
		dataScopes.assertTeacherAccess(scope, teacherId);
		assertSchoolTeacher(plan.getSchoolId(), teacherId);
		assertAssignableSupervisor(plan, supervisorId, teacherId);
		SupervisionAssignment assignment = new SupervisionAssignment();
		assignment.setPlanId(planId);
		assignment.setSchoolId(plan.getSchoolId());
		assignment.setCampusId(offering.getCampusId());
		assignment.setSupervisorId(supervisorId);
		assignment.setTeacherId(teacherId);
		assignment.setScheduleEntryId(publishedEntry.getId());
		audit.log(actor, "EDU_SUPERVISION_ASSIGNMENT_CREATE", "planId=" + planId);
		return assignments.save(assignment);
	}

	@Transactional
	public SupervisionAssignment assignObservation(String actor, ObservationCommand command) {
		var plan = plans.findById(command.planId())
				.orElseThrow(() -> new IllegalArgumentException("督导计划不存在"));
		var scope = dataScopes.resolve(actor);
		assertPlanAccess(plan, scope);
		requireState(plan.getStatus(), "PUBLISHED");
		if ("ALL".equals(plan.getSchoolId()) || plan.getCampusId() != null) {
			throw new IllegalArgumentException("独立督导课次须使用本校计划");
		}
		if (!command.endsAt().isAfter(command.startsAt())
				|| command.startsAt().toLocalDate().isBefore(plan.getStartDate())
				|| command.endsAt().toLocalDate().isAfter(plan.getEndDate())) {
			throw new IllegalArgumentException("课次起止时间须在督导计划期间内");
		}
		assertSchoolTeacher(plan.getSchoolId(), command.teacherId());
		assertAssignableSupervisor(plan, command.supervisorId(), command.teacherId());
		var assignment = new SupervisionAssignment();
		assignment.setPlanId(plan.getId());
		assignment.setSchoolId(plan.getSchoolId());
		assignment.setSupervisorId(command.supervisorId());
		assignment.setTeacherId(command.teacherId());
		assignment.setObservedCourse(command.course().strip());
		assignment.setObservedLocation(command.location().strip());
		assignment.setObservedStartsAt(command.startsAt());
		assignment.setObservedEndsAt(command.endsAt());
		var saved = assignments.save(assignment);
		audit.log(actor, "EDU_SUPERVISION_OBSERVATION_CREATE", "assignmentId=" + saved.getId());
		return saved;
	}

	private void assertSchoolTeacher(String schoolId, String teacherId) {
		if (teacherProfiles.findById(teacherId).filter(profile ->
				schoolOrganizations(schoolId).contains(profile.getDepartmentId()) && Boolean.TRUE.equals(profile.getEnabled())).isEmpty()) {
			throw new AccessDeniedException("请选择本校启用的教师档案");
		}
	}

	@Transactional(readOnly = true)
	public List<TeacherOption> observationTeachers(String planId, String actor) {
		var plan = plans.findById(planId).orElseThrow(() -> new IllegalArgumentException("督导计划不存在"));
		assertPlanAccess(plan, actor);
		requireAssignmentSchool(plan);
		return schoolOrganizations(plan.getSchoolId()).stream()
				.flatMap(id -> teacherProfiles.findByDepartmentIdAndEnabledTrueOrderByTeacherNo(id).stream())
				.map(profile -> new TeacherOption(profile.getId(), profile.getTeacherName(), profile.getTeacherNo()))
				.toList();
	}

	@Transactional(readOnly = true)
	public List<SupervisorOption> assignmentSupervisors(String planId, String actor) {
		var plan = planForAssignmentOptions(planId, actor);
		var schoolIds = schoolOrganizations(plan.getSchoolId());
		var teacherIds = schoolIds.stream().flatMap(id -> teacherProfiles
				.findByDepartmentIdAndEnabledTrueOrderByTeacherNo(id).stream())
				.map(profile -> profile.getId()).toList();
		if (teacherIds.isEmpty()) return List.of();
		var usernames = bindings.findByProfileTypeAndProfileIdInAndStatus("TEACHER", teacherIds, "ACTIVE").stream()
				.map(binding -> binding.getUsername()).distinct().toList();
		if (usernames.isEmpty()) return List.of();
		return accounts.findByUsernameIn(usernames).stream()
				.filter(account -> account.getOrganizationId() != null && schoolIds.contains(account.getOrganizationId()))
				.filter(account -> hasDuty(account.getUsername(), "education:supervision:assignment:accept"))
				.map(account -> new SupervisorOption(account.getUsername(),
						account.getDisplayName() == null ? account.getUsername() : account.getDisplayName()))
				.sorted(java.util.Comparator.comparing(SupervisorOption::name).thenComparing(SupervisorOption::username))
				.toList();
	}

	@Transactional(readOnly = true)
	public List<CourseOffering> assignmentOfferings(String planId, String actor) {
		var plan = planForAssignmentOptions(planId, actor);
		Set<String> campusIds = plan.getCampusId() == null
				? schoolOrganizations(plan.getSchoolId()) : Set.of(plan.getCampusId());
		return dataScopes.visibleOfferings(dataScopes.resolve(actor),
				offerings.findByCampusIdInOrderBySemesterCodeDescOfferingCodeAsc(campusIds)).stream()
				.filter(offering -> offering.getTeacherId() != null && CourseOffering.isSchedulingOffering(offering.getStatus()))
				.toList();
	}

	@Transactional(readOnly = true)
	public List<ScheduleEntry> assignmentScheduleOptions(String planId, String offeringId, String actor) {
		var plan = planForAssignmentOptions(planId, actor);
		var offering = offerings.findById(offeringId)
				.orElseThrow(() -> new IllegalArgumentException("教学班不存在"));
		var scope = dataScopes.resolve(actor);
		dataScopes.assertOfferingAccess(scope, offeringId);
		if (offering.getCampusId() == null || !schoolOrganizations(plan.getSchoolId()).contains(offering.getCampusId())
				|| (plan.getCampusId() != null && !plan.getCampusId().equals(offering.getCampusId()))) {
			throw new AccessDeniedException("教学班不属于督导计划的学校或校区");
		}
		return scheduleVersions.latestPublishedEntries(offering.getSemesterCode()).stream()
				.filter(entry -> offeringId.equals(entry.getOfferingId()))
				.toList();
	}

	private SupervisionPlan planForAssignmentOptions(String id, String actor) {
		var plan = plans.findById(id).orElseThrow(() -> new IllegalArgumentException("督导计划不存在"));
		assertPlanAccess(plan, actor);
		requireAssignmentSchool(plan);
		return plan;
	}

	private void assertAssignableSupervisor(SupervisionPlan plan, String username, String teacherId) {
		var account = accounts.findByUsername(username);
		if (account == null || account.getOrganizationId() == null
				|| !schoolOrganizations(plan.getSchoolId()).contains(account.getOrganizationId())) {
			throw new AccessDeniedException("请选择督导计划所属学校的账号");
		}
		var supervisor = identities.teacherIds(username);
		if (supervisor.size() != 1) throw new IllegalArgumentException("督导账号未绑定唯一教师档案");
		if (supervisor.contains(teacherId)) throw new IllegalArgumentException("授课教师和督导员须为不同人员");
		assertSchoolTeacher(plan.getSchoolId(), supervisor.iterator().next());
		requireDuty(username, "education:supervision:assignment:accept", "该账号未获督导员权限或已停用");
	}

	public record SupervisorOption(String username, String name) {}

	public record TeacherOption(String id, String name, String code) {}

	@Transactional(readOnly = true)
	public List<SupervisionAssignment> myAssignments(String supervisorId) {
		assertActiveSupervisor(supervisorId);
		return assignments.findBySupervisorIdOrderByCreateTimeDesc(supervisorId);
	}

	@Transactional(readOnly = true)
	public List<SupervisionAssignment> teachingAssignments(String teacherUsername) {
		Set<String> teacherIds = identities.teacherIds(teacherUsername);
		if (teacherIds.isEmpty()) {
			throw new AccessDeniedException("当前账号没有有效教师档案绑定");
		}
		return assignments.findByTeacherIdInOrderByCreateTimeDesc(teacherIds.stream().toList());
	}

	@Transactional(readOnly = true)
	public SupervisionCheckInProofProvider.Proof issueTeacherProof(String assignmentId, String teacherUsername) {
		SupervisionAssignment assignment = assignments.findById(assignmentId)
				.orElseThrow(() -> new IllegalArgumentException("督导任务不存在"));
		if (identities == null || !identities.isTeacher(teacherUsername, assignment.getTeacherId())) {
			throw new AccessDeniedException("仅该课授课教师可签发现场签到凭证");
		}
		requireState(assignment.getStatus(), "ACCEPTED");
		SupervisionPlan plan = plans.findById(assignment.getPlanId())
				.orElseThrow(() -> new IllegalArgumentException("督导计划不存在"));
		requireState(plan.getStatus(), "IN_PROGRESS");
		if (!(checkInProofs instanceof TeacherIssuedSupervisionQrProofProvider provider)) {
			throw new IllegalStateException("督导现场签到服务尚未配置");
		}
		return provider.issue(assignment, Instant.now());
	}

	@Transactional(readOnly = true)
	public SupervisionRecord getRecordForSupervisor(String assignmentId, String supervisorId) {
		assigned(assignmentId, supervisorId);
		return records.findByAssignmentId(assignmentId).orElseThrow();
	}

	@Transactional(readOnly = true)
	public List<FormDefinition> evaluationForms(String supervisorId) {
		assertActiveSupervisor(supervisorId);
		if (forms == null) throw new IllegalStateException("SUPERVISION_FORM_ENGINE_UNAVAILABLE");
		return forms.publishedByKeyPrefix("EDU_SUPERVISION_");
	}

	@Transactional(readOnly = true)
	public List<FormField> evaluationFields(String supervisorId, String formId) {
		assertActiveSupervisor(supervisorId);
		requireEvaluationDefinition(formId);
		return forms.fields(formId);
	}

	@Transactional
	public FormInstance saveEvaluationForm(String assignmentId, String supervisorId,
			String formId, Map<String, Object> values, boolean draft) {
		SupervisionAssignment assignment = assigned(assignmentId, supervisorId);
		requireState(assignment.getStatus(), "CHECKED_IN");
		requireEvaluationDefinition(formId);
		Map<String, String> permissions = new HashMap<>();
		for (FormField field : forms.fields(formId)) {
			permissions.put(formId + "." + field.getFieldKey(), "EDIT");
		}
		return forms.saveRuntime(assignmentId, formId, "SUPERVISION", "INSPECTOR", supervisorId,
				values == null ? Map.of() : values, permissions, Set.of(), draft);
	}

	@Transactional
	public SupervisionPlan startPlan(String id, String actor) {
		SupervisionPlan plan = plans.findById(id).orElseThrow();
		assertPlanAccess(plan, actor);
		requireState(plan.getStatus(), "PUBLISHED");
		plan.setStatus("IN_PROGRESS");
		audit.log(actor, "EDU_SUPERVISION_PLAN_START", "planId=" + id);
		return plans.save(plan);
	}

	@Transactional
	public SupervisionAssignment accept(String id, String supervisorId) {
		SupervisionAssignment assignment = assigned(id, supervisorId);
		requireState(assignment.getStatus(), "PENDING");
		assignment.setStatus("ACCEPTED");
		assignment.setAcceptedAt(LocalDateTime.now());
		audit.log(supervisorId, "EDU_SUPERVISION_ASSIGNMENT_ACCEPT", "assignmentId=" + id);
		return assignments.save(assignment);
	}

	@Transactional
	public SupervisionAssignment checkIn(String id, String supervisorId) {
		return checkIn(id, supervisorId, null);
	}

	@Transactional
	public SupervisionAssignment checkIn(String id, String supervisorId,
			SupervisionCheckInProofProvider.Proof proof) {
		SupervisionAssignment assignment = assigned(id, supervisorId);
		if (!Set.of("ACCEPTED", "PENDING").contains(assignment.getStatus())) {
			throw new IllegalStateException("当前任务不可签到");
		}
		Instant now = Instant.now();
		SupervisionCheckInProofProvider.VerificationResult result = checkInProofs == null
				? new SupervisionCheckInProofProvider.VerificationResult(
						SupervisionCheckInProofProvider.VerificationCode.PROVIDER_UNAVAILABLE, null, null)
				: proof == null || proof.providerId() == null || proof.providerId().isBlank()
						|| proof.signedProof() == null || proof.signedProof().isBlank()
				? new SupervisionCheckInProofProvider.VerificationResult(
						SupervisionCheckInProofProvider.VerificationCode.INVALID_PROOF, null, null)
				: checkInProofs.verify(new SupervisionCheckInProofProvider.VerificationRequest(
						assignment, proof, now));
		if (result == null) {
			result = new SupervisionCheckInProofProvider.VerificationResult(
					SupervisionCheckInProofProvider.VerificationCode.PROVIDER_UNAVAILABLE, null, null);
		}
		audit.log(supervisorId, "EDU_SUPERVISION_CHECK_IN_PROOF",
				"assignmentId=" + id + ",code=" + result.code());
		if (result.code() == null || !result.verified()) {
			throw proofFailure(result.code());
		}
		if (result.proofId() == null || result.proofId().isBlank()
				|| result.expiresAt() == null || !result.expiresAt().isAfter(now)) {
			throw proofFailure(SupervisionCheckInProofProvider.VerificationCode.PROOF_EXPIRED);
		}
		consumedProofs.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
		Instant previous = consumedProofs.putIfAbsent(result.proofId(), result.expiresAt());
		if (previous != null && previous.isAfter(now)) {
			throw proofFailure(SupervisionCheckInProofProvider.VerificationCode.REPLAY_DETECTED);
		}
		assignment.setStatus("CHECKED_IN");
		// 签到时间只能由服务端产生，忽略客户端传入时间。
		assignment.setCheckedInAt(LocalDateTime.now());
		audit.log(supervisorId, "EDU_SUPERVISION_ASSIGNMENT_CHECK_IN", "assignmentId=" + id);
		return assignments.save(assignment);
	}

	private RuntimeException proofFailure(SupervisionCheckInProofProvider.VerificationCode code) {
		if (code == null) {
			return new IllegalArgumentException("SUPERVISION_CHECK_IN_INVALID_PROOF");
		}
		return switch (code) {
			case CONFIGURATION_MISSING, PROVIDER_UNAVAILABLE ->
					new IllegalStateException("SUPERVISION_CHECK_IN_" + code.name());
			default -> new IllegalArgumentException("SUPERVISION_CHECK_IN_" + code.name());
		};
	}

	@Transactional
	public SupervisionAssignment completeAssignment(String id, String supervisorId) {
		SupervisionAssignment assignment = assigned(id, supervisorId);
		requireState(assignment.getStatus(), "SUBMITTED");
		assignment.setStatus("COMPLETED");
		assignment.setCompletedAt(LocalDateTime.now());
		audit.log(supervisorId, "EDU_SUPERVISION_ASSIGNMENT_COMPLETE", "assignmentId=" + id);
		return assignments.save(assignment);
	}

	@Transactional
	public SupervisionRecord submit(String assignmentId, String supervisorId,
			String formSnapshotJson, String scheduleContextSnapshotJson, String formTemplateId) {
		SupervisionAssignment assignment = assigned(assignmentId, supervisorId);
		requireState(assignment.getStatus(), "CHECKED_IN");
		if (records.findByAssignmentId(assignmentId).isPresent()) {
			throw new IllegalStateException("评价已提交且不可修改");
		}
		if (forms == null) {
			throw new IllegalStateException("SUPERVISION_FORM_ENGINE_UNAVAILABLE");
		}
		requireEvaluationDefinition(formTemplateId);
		FormInstance instance = forms.instance(assignmentId, formTemplateId, "SUPERVISION")
				.orElseThrow(() -> new IllegalArgumentException("SUPERVISION_FORM_INSTANCE_REQUIRED"));
		if (!"SUBMITTED".equals(instance.getStatus())) {
			throw new IllegalArgumentException("SUPERVISION_FORM_INSTANCE_NOT_SUBMITTED");
		}
		String formSnapshot = snapshot(instance);
		String scheduleSnapshot = scheduleSnapshot(assignment);
		SupervisionRecord record = new SupervisionRecord();
		record.setAssignmentId(assignmentId);
		record.setSchoolId(assignment.getSchoolId());
		record.setSupervisorId(supervisorId);
		record.setTeacherId(assignment.getTeacherId());
		record.setFormTemplateId(formTemplateId);
		record.setFormSnapshotJson(formSnapshot);
		record.setScheduleContextSnapshotJson(scheduleSnapshot);
		record.setSubmittedAt(LocalDateTime.now());
		assignment.setStatus("SUBMITTED");
		assignment.setSubmittedAt(record.getSubmittedAt());
		records.save(record);
		assignments.save(assignment);
		audit.log(supervisorId, "EDU_SUPERVISION_RECORD_SUBMIT", "assignmentId=" + assignmentId);
		return record;
	}

	@Transactional
	public SupervisionIssue createIssue(String recordId, String actor, String severity,
			String title, String description, String ownerId, LocalDateTime dueAt) {
		if (ownerId == null || ownerId.isBlank()) {
			throw new IllegalArgumentException("督导问题必须指定整改负责人账号");
		}
		if ("CRITICAL".equals(severity) && dueAt == null) {
			throw new IllegalArgumentException("严重问题必须指定截止日期");
		}
		SupervisionRecord record = records.findById(recordId).orElseThrow();
		EducationDataScope scope = dataScopes.resolve(actor);
		assertSchoolAccess(scope, record.getSchoolId());
		Set<String> ownerTeacherIds = identities == null ? Set.of(ownerId) : identities.teacherIds(ownerId);
		if (ownerTeacherIds.isEmpty()) throw new IllegalArgumentException("整改负责人没有有效教师档案绑定");
		var task = assignments.findById(record.getAssignmentId());
		if (task.filter(value -> value.getObservedStartsAt() != null).isPresent()) {
			if (ownerTeacherIds.size() != 1) throw new IllegalArgumentException("整改负责人未绑定唯一教师档案");
			assertSchoolTeacher(record.getSchoolId(), ownerTeacherIds.iterator().next());
			requireDuty(ownerId, "education:supervision:rectification:create", "该账号未获整改权限");
		} else {
			dataScopes.assertTeacherAccess(scope, record.getTeacherId());
			if (ownerTeacherIds.stream().noneMatch(id -> dataScopes.canAccessTeacher(scope, id))) {
				throw new AccessDeniedException("无权指定该整改负责人");
			}
		}
		SupervisionIssue issue = new SupervisionIssue();
		issue.setRecordId(recordId);
		issue.setSchoolId(record.getSchoolId());
		issue.setSeverity(severity);
		issue.setTitle(title);
		issue.setDescription(description);
		issue.setOwnerId(ownerId);
		issue.setDueAt(dueAt);
		SupervisionIssue saved = issues.save(issue);
		audit.log(actor, "EDU_SUPERVISION_ISSUE_CREATE", "issueId=" + saved.getId());
		return saved;
	}

	@Transactional
	public SupervisionRectification rectify(String issueId, String actor, String content) {
		SupervisionIssue issue = issues.findById(issueId).orElseThrow();
		requireState(issue.getStatus(), "OPEN", "RECTIFYING");
		if (issue.getOwnerId() == null || !issue.getOwnerId().equals(actor)
				|| identities != null && identities.teacherIds(actor).isEmpty()) {
			throw new AccessDeniedException("仅问题负责人可以提交整改");
		}
		SupervisionRectification rectification = rectifications.findByIssueId(issueId)
				.orElseGet(SupervisionRectification::new);
		rectification.setIssueId(issueId);
		rectification.setRectifierId(actor);
		rectification.setContent(content);
		rectification.setSubmittedAt(LocalDateTime.now());
		rectification.setStatus("SUBMITTED");
		issue.setStatus("REVIEWING");
		issues.save(issue);
		audit.log(actor, "EDU_SUPERVISION_RECTIFICATION_SUBMIT", "issueId=" + issueId);
		return rectifications.save(rectification);
	}

	@Transactional
	public SupervisionIssue review(String issueId, String reviewerId, boolean approved, String comment) {
		SupervisionIssue issue = issues.findById(issueId).orElseThrow();
		assertSchoolAccess(dataScopes.resolve(reviewerId), issue.getSchoolId());
		requireState(issue.getStatus(), "REVIEWING");
		SupervisionRectification rectification = rectifications.findByIssueId(issueId).orElseThrow();
		if (reviewerId.equals(rectification.getRectifierId())) {
			throw new AccessDeniedException("整改人不得审批自己的整改");
		}
		issue.setStatus(approved ? "CLOSED" : "RECTIFYING");
		issues.save(issue);
		SupervisionReview review = new SupervisionReview();
		review.setIssueId(issueId);
		review.setReviewerId(reviewerId);
		review.setDecision(approved ? "APPROVED" : "RETURNED");
		review.setComment(comment);
		review.setReviewedAt(LocalDateTime.now());
		reviews.save(review);
		audit.log(reviewerId, "EDU_SUPERVISION_ISSUE_REVIEW", "issueId=" + issueId);
		return issue;
	}

	@Transactional(readOnly = true)
	public SupervisionIssue getIssue(String issueId) {
		return issues.findById(issueId).orElseThrow();
	}

	@Transactional(readOnly = true)
	public List<SupervisionRecord> listRecords(String actor, String schoolId) {
		var scope = dataScopes.resolve(actor);
		return records.findBySchoolIdOrderBySubmittedAtDesc(schoolId == null ? scopeSchool(scope) : resolveSchool(scope, schoolId));
	}

	@Transactional(readOnly = true)
	public List<SupervisionIssue> listIssues(String actor, String schoolId) {
		var scope = dataScopes.resolve(actor);
		return issues.findBySchoolIdOrderByCreateTimeDesc(schoolId == null ? scopeSchool(scope) : resolveSchool(scope, schoolId));
	}

	@Transactional(readOnly = true)
	public List<SupervisionIssue> myIssues(String actor) {
		assertActiveSupervisor(actor);
		return issues.findByOwnerIdOrderByCreateTimeDesc(actor);
	}

	@Transactional
	public int enqueueOverdueReminders(LocalDateTime now) {
		List<SupervisionIssue> overdue = issues.findByStatusAndDueAtBefore("RECTIFYING", now);
		for (SupervisionIssue issue : overdue) {
			events.enqueue("EDU_SUPERVISION_ISSUE_OVERDUE", issue.getId(),
					"supervision-overdue:" + issue.getId(),
					new OverdueIssueReminder(UUID.randomUUID().toString(), issue.getId(), issue.getOwnerId(), now));
		}
		return overdue.size();
	}

	private SupervisionAssignment assigned(String id, String supervisorId) {
		assertActiveSupervisor(supervisorId);
		return assignments.findByIdAndSupervisorId(id, supervisorId)
				.orElseThrow(() -> new AccessDeniedException("任务未分配给当前督导"));
	}

	private void assertActiveSupervisor(String supervisorId) {
		if (identities != null && identities.teacherIds(supervisorId).isEmpty()) {
			throw new AccessDeniedException("督导账号没有有效教师档案绑定");
		}
	}

	private void requireDuty(String username, String permission, String error) {
		if (!hasDuty(username, permission)) throw new IllegalArgumentException(error);
	}

	private boolean hasDuty(String username, String permission) {
		try {
			var user = userDetails.loadUserByUsername(username);
			return user.isEnabled() && user.isAccountNonLocked() && user.getAuthorities().stream()
					.anyMatch(authority -> permission.equals(authority.getAuthority()));
		} catch (org.springframework.security.core.userdetails.UsernameNotFoundException ignored) {
			return false;
		}
	}

	private String resolveSchool(EducationDataScope scope, String requestedSchool) {
		if ((requestedSchool == null || requestedSchool.isBlank()) && scope.fullAccess()) {
			throw new IllegalArgumentException("请选择督导计划所属学校");
		}
		String schoolId = requestedSchool == null || requestedSchool.isBlank()
				? dataScopes.requireSingleSchool(scope) : requestedSchool;
		if (organizations.findById(schoolId).filter(school -> "SCHOOL".equals(school.getOrganizationType())
				&& Integer.valueOf(1).equals(school.getStatus())).isEmpty()) {
			throw new IllegalArgumentException("请选择启用的学校");
		}
		dataScopes.assertSchoolAccess(scope, schoolId);
		return schoolId;
	}

	private Set<String> schoolOrganizations(String schoolId) {
		var allowed = new java.util.HashSet<String>();
		allowed.add(schoolId);
		organizations.findByParentOrgId_Id(schoolId).stream()
				.filter(campus -> "CAMPUS".equals(campus.getOrganizationType()) && Integer.valueOf(1).equals(campus.getStatus()))
				.map(Organization::getId).forEach(allowed::add);
		return allowed;
	}

	private void requireAssignmentSchool(SupervisionPlan plan) {
		if ("ALL".equals(plan.getSchoolId())) {
			throw new IllegalArgumentException("历史未指定学校的计划不能分配任务，请在所属学校新建计划");
		}
	}

	private void requireState(String actual, String... allowed) {
		for (String state : allowed) {
			if (state.equals(actual)) {
				return;
			}
		}
		throw new IllegalStateException("非法状态转换: " + actual);
	}

	private String scopeSchool(EducationDataScope scope) {
		return scope.fullAccess() ? "ALL" : dataScopes.requireSingleSchool(scope);
	}

	private void assertSchoolAccess(EducationDataScope scope, String schoolId) {
		if (scope.fullAccess()) return;
		if ("ALL".equals(schoolId)) throw new AccessDeniedException("无权访问该督导数据");
		dataScopes.assertSchoolAccess(scope, schoolId);
	}

	private FormDefinition requireEvaluationDefinition(String formId) {
		if (forms == null) throw new IllegalStateException("SUPERVISION_FORM_ENGINE_UNAVAILABLE");
		FormDefinition definition = forms.definition(formId);
		if (!"PUBLISHED".equals(definition.getStatus())) {
			throw new IllegalStateException("SUPERVISION_FORM_TEMPLATE_NOT_PUBLISHED");
		}
		if (definition.getFormKey() == null
				|| !definition.getFormKey().startsWith("EDU_SUPERVISION_")) {
			throw new IllegalArgumentException("该表单不是督导评价模板");
		}
		return definition;
	}

	private void assertPlanAccess(SupervisionPlan plan, String actor) {
		assertPlanAccess(plan, dataScopes.resolve(actor));
	}

	private void assertPlanAccess(SupervisionPlan plan, EducationDataScope scope) {
		if (scope.fullAccess()) {
			return;
		}
		if ("ALL".equals(plan.getSchoolId())) {
			throw new AccessDeniedException("无权访问该督导计划");
		}
		dataScopes.assertSchoolAccess(scope, plan.getSchoolId());
		if (plan.getCampusId() != null) {
			dataScopes.assertCampusAccess(scope, plan.getCampusId());
		}
	}

	private String snapshot(FormInstance instance) {
		try {
			return json.writeValueAsString(java.util.Map.of(
					"formId", instance.getFormId(),
					"nodeKey", instance.getNodeKey(),
					"status", instance.getStatus(),
					"data", json.readTree(instance.getDataJson())));
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("SUPERVISION_FORM_SNAPSHOT_INVALID", exception);
		}
	}

	private String scheduleSnapshot(SupervisionAssignment assignment) {
		if (assignment.getObservedStartsAt() == null && scheduleVersions == null) {
			throw new IllegalStateException("SUPERVISION_PUBLISHED_SCHEDULE_PROVIDER_UNAVAILABLE");
		}
		try {
			if (assignment.getObservedStartsAt() != null) {
				return json.writeValueAsString(Map.of("source", "OBSERVATION",
						"course", assignment.getObservedCourse(), "location", assignment.getObservedLocation(),
						"startsAt", assignment.getObservedStartsAt().toString(), "endsAt", assignment.getObservedEndsAt().toString(),
						"teacherId", assignment.getTeacherId()));
			}
			return json.writeValueAsString(scheduleVersions.requirePublishedEntry(assignment.getScheduleEntryId()));
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("SUPERVISION_SCHEDULE_SNAPSHOT_INVALID", exception);
		}
	}

	public record OverdueIssueReminder(String eventId, String issueId, String ownerId, LocalDateTime occurredAt) {
	}
}
