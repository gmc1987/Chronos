package com.chronos.education.supervision.service;

import com.chronos.education.grade.service.DomainEventOutboxService;
import com.chronos.education.supervision.dao.*;
import com.chronos.education.supervision.model.*;
import com.chronos.service.iService.IAuditLogService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
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
	private final DomainEventOutboxService events;
	private final IAuditLogService audit;

	public SupervisionCenterService(
			SupervisionPlanRepository plans,
			SupervisionAssignmentRepository assignments,
			SupervisionRecordRepository records,
			SupervisionIssueRepository issues,
			SupervisionRectificationRepository rectifications,
			DomainEventOutboxService events,
			IAuditLogService audit) {
		this.plans = plans;
		this.assignments = assignments;
		this.records = records;
		this.issues = issues;
		this.rectifications = rectifications;
		this.events = events;
		this.audit = audit;
	}

	@Transactional
	public SupervisionPlan publishPlan(String id, String actor) {
		SupervisionPlan plan = plans.findById(id).orElseThrow();
		requireState(plan.getStatus(), "DRAFT");
		plan.setStatus("PUBLISHED");
		audit.log(actor, "EDU_SUPERVISION_PLAN_PUBLISH", "planId=" + id);
		return plans.save(plan);
	}

	@Transactional(readOnly = true)
	public List<SupervisionPlan> listPlans(String schoolId) {
		return plans.findBySchoolIdOrderByCreateTimeDesc(schoolId);
	}

	@Transactional
	public SupervisionPlan createPlan(String schoolId, String actor, String name,
			java.time.LocalDate startDate, java.time.LocalDate endDate, String campusId) {
		if (endDate.isBefore(startDate)) {
			throw new IllegalArgumentException("督导计划结束日期不能早于开始日期");
		}
		SupervisionPlan plan = new SupervisionPlan();
		plan.setSchoolId(schoolId);
		plan.setName(name);
		plan.setStartDate(startDate);
		plan.setEndDate(endDate);
		plan.setCampusId(campusId);
		audit.log(actor, "EDU_SUPERVISION_PLAN_CREATE", "name=" + name);
		return plans.save(plan);
	}

	@Transactional
	public SupervisionAssignment createAssignment(String schoolId, String actor, String planId,
			String supervisorId, String teacherId, String scheduleEntryId, String campusId) {
		SupervisionPlan plan = plans.findById(planId).orElseThrow();
		if (!schoolId.equals(plan.getSchoolId()) || !"PUBLISHED".equals(plan.getStatus())) {
			throw new IllegalStateException("仅可向本校已发布计划分配任务");
		}
		SupervisionAssignment assignment = new SupervisionAssignment();
		assignment.setPlanId(planId);
		assignment.setSchoolId(schoolId);
		assignment.setCampusId(campusId);
		assignment.setSupervisorId(supervisorId);
		assignment.setTeacherId(teacherId);
		assignment.setScheduleEntryId(scheduleEntryId);
		audit.log(actor, "EDU_SUPERVISION_ASSIGNMENT_CREATE", "planId=" + planId);
		return assignments.save(assignment);
	}

	@Transactional(readOnly = true)
	public List<SupervisionAssignment> myAssignments(String supervisorId) {
		return assignments.findBySupervisorIdOrderByCreateTimeDesc(supervisorId);
	}

	@Transactional(readOnly = true)
	public SupervisionRecord getRecordForSupervisor(String assignmentId, String supervisorId) {
		assigned(assignmentId, supervisorId);
		return records.findByAssignmentId(assignmentId).orElseThrow();
	}

	@Transactional
	public SupervisionPlan startPlan(String id, String actor) {
		SupervisionPlan plan = plans.findById(id).orElseThrow();
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
		return assignments.save(assignment);
	}

	@Transactional
	public SupervisionAssignment checkIn(String id, String supervisorId) {
		SupervisionAssignment assignment = assigned(id, supervisorId);
		if (!Set.of("ACCEPTED", "PENDING").contains(assignment.getStatus())) {
			throw new IllegalStateException("当前任务不可签到");
		}
		assignment.setStatus("CHECKED_IN");
		// 签到时间只能由服务端产生，忽略客户端传入时间。
		assignment.setCheckedInAt(LocalDateTime.now());
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
		SupervisionRecord record = new SupervisionRecord();
		record.setAssignmentId(assignmentId);
		record.setSchoolId(assignment.getSchoolId());
		record.setSupervisorId(supervisorId);
		record.setTeacherId(assignment.getTeacherId());
		record.setFormTemplateId(formTemplateId);
		record.setFormSnapshotJson(formSnapshotJson);
		record.setScheduleContextSnapshotJson(scheduleContextSnapshotJson);
		record.setSubmittedAt(LocalDateTime.now());
		assignment.setStatus("SUBMITTED");
		assignment.setSubmittedAt(record.getSubmittedAt());
		records.save(record);
		assignments.save(assignment);
		return record;
	}

	@Transactional
	public SupervisionIssue createIssue(String recordId, String schoolId, String severity,
			String title, String description, String ownerId, LocalDateTime dueAt) {
		if ("CRITICAL".equals(severity) && (ownerId == null || ownerId.isBlank() || dueAt == null)) {
			throw new IllegalArgumentException("严重问题必须指定负责人和截止日期");
		}
		SupervisionIssue issue = new SupervisionIssue();
		issue.setRecordId(recordId);
		issue.setSchoolId(schoolId);
		issue.setSeverity(severity);
		issue.setTitle(title);
		issue.setDescription(description);
		issue.setOwnerId(ownerId);
		issue.setDueAt(dueAt);
		return issues.save(issue);
	}

	@Transactional
	public SupervisionRectification rectify(String issueId, String actor, String content) {
		SupervisionIssue issue = issues.findById(issueId).orElseThrow();
		requireState(issue.getStatus(), "OPEN", "RECTIFYING");
		if (issue.getOwnerId() != null && !issue.getOwnerId().equals(actor)) {
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
		return rectifications.save(rectification);
	}

	@Transactional
	public SupervisionIssue review(String issueId, String reviewerId, boolean approved, String comment) {
		SupervisionIssue issue = issues.findById(issueId).orElseThrow();
		requireState(issue.getStatus(), "REVIEWING");
		SupervisionRectification rectification = rectifications.findByIssueId(issueId).orElseThrow();
		if (reviewerId.equals(rectification.getRectifierId())) {
			throw new AccessDeniedException("整改人不得审批自己的整改");
		}
		issue.setStatus(approved ? "CLOSED" : "RECTIFYING");
		issues.save(issue);
		return issue;
	}

	@Transactional(readOnly = true)
	public SupervisionIssue getIssue(String issueId) {
		return issues.findById(issueId).orElseThrow();
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
		return assignments.findByIdAndSupervisorId(id, supervisorId)
				.orElseThrow(() -> new AccessDeniedException("任务未分配给当前督导"));
	}

	private void requireState(String actual, String... allowed) {
		for (String state : allowed) {
			if (state.equals(actual)) {
				return;
			}
		}
		throw new IllegalStateException("非法状态转换: " + actual);
	}

	public record OverdueIssueReminder(String eventId, String issueId, String ownerId, LocalDateTime occurredAt) {
	}
}
