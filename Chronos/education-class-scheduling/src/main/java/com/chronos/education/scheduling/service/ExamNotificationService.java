package com.chronos.education.scheduling.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.education.scheduling.dao.EducationUserBindingRepository;
import com.chronos.education.scheduling.dao.ExamCandidateRepository;
import com.chronos.education.scheduling.dao.ExamInvigilationRepository;
import com.chronos.education.scheduling.dao.ExamRoomRepository;
import com.chronos.education.scheduling.dao.ExamSessionRepository;
import com.chronos.education.scheduling.model.EducationUserBinding;
import com.chronos.education.scheduling.model.ExamCandidate;
import com.chronos.education.scheduling.model.ExamInvigilation;
import com.chronos.education.scheduling.model.ExamPlan;
import com.chronos.education.scheduling.model.ExamPublishedChange;
import com.chronos.education.scheduling.model.ExamRoom;
import com.chronos.education.scheduling.model.ExamSession;
import com.chronos.workflow.WorkflowNotificationService;

import lombok.RequiredArgsConstructor;

/** 通过已有通知 Outbox 发出考务消息，和排班变更处于同一事务。 */
@Service
@RequiredArgsConstructor
public class ExamNotificationService {
	private final ExamSessionRepository sessions;
	private final ExamRoomRepository rooms;
	private final ExamCandidateRepository candidates;
	private final ExamInvigilationRepository assignments;
	private final EducationUserBindingRepository bindings;
	private final IAdminUserRepository users;
	private final WorkflowNotificationService notifications;

	public void planPublished(ExamPlan plan) {
		for (ExamSession session : sessions
				.findByPlanIdOrderByExamDateAscStartTimeAsc(plan.getId())) {
			for (ExamRoom room : rooms.findBySessionId(session.getId())) {
				String when = session.getExamDate() + " " + session.getStartTime();
				for (ExamCandidate candidate : candidates
						.findByRoomIdOrderBySeatNoAsc(room.getId())) {
					notifyProfile(
							"STUDENT", candidate.getStudentId(),
							"EDUCATION_EXAM_PUBLISHED", candidate.getId(),
							"考试安排已发布",
							plan.getPlanName() + "，考试时间 " + when
									+ "，座位 " + candidate.getSeatNo(),
							"PUBLISHED");
				}
				for (ExamInvigilation assignment : assignments
						.findByRoomIdAndStatus(room.getId(), "ASSIGNED")) {
					notifyProfile(
							"TEACHER", assignment.getTeacherId(),
							"EDUCATION_INVIGILATION_ASSIGNED", assignment.getId(),
							"监考任务已发布",
							plan.getPlanName() + "，监考时间 " + when,
							"PUBLISHED");
				}
				for (ExamInvigilation standby : assignments
						.findByRoomIdAndStatus(room.getId(), "STANDBY")) {
					notifyProfile(
							"TEACHER", standby.getTeacherId(),
							"EDUCATION_INVIGILATION_STANDBY", standby.getId(),
							"机动监考待命",
							plan.getPlanName() + "，待命时间 " + when,
							"PUBLISHED");
				}
			}
		}
	}

	public void standbyActivated(String teacherId, String assignmentId) {
		notifyProfile("TEACHER", teacherId,
				"EDUCATION_INVIGILATION_STANDBY_ACTIVATED", assignmentId,
				"机动监考已启用", "请立即前往最新监考任务对应考场。",
				"ACTIVATED");
	}

	public void missingCheckIn(String assignmentId, String teacherId) {
		for (String recipient : users.findActiveUsernamesByPermissionCode(
				"education:exam:invigilation:manage")) {
			notifications.enqueueUserEvent(
					"EDUCATION_INVIGILATION_MISSING_CHECK_IN",
					assignmentId, recipient, "监考教师未报到",
					"教师 " + teacherId + " 尚未在考前 15 分钟报到，请核实并启用机动监考。",
					"ESCALATED");
		}
	}

	public void suspensionRequested(String requestId, String planName) {
		for (String recipient : users.findActiveUsernamesByPermissionCode(
				"education:exam:suspension:approve")) {
			notifications.enqueueUserEvent(
					"EDUCATION_EXAM_SUSPENSION_REQUESTED",
					requestId, recipient, "考试占课待审批",
					planName + " 提交了批量停课申请，请核对影响课程。",
					"REQUESTED");
		}
	}

	public void suspensionDecided(
			String requestId,
			String applicant,
			boolean approved) {
		notifications.enqueueUserEvent(
				"EDUCATION_EXAM_SUSPENSION_DECIDED",
				requestId, applicant, "考试占课审批结果",
				approved ? "批量停课已批准并写入日期课表。"
						: "批量停课申请已驳回。",
				"DECIDED");
	}

	public void publishedChangeRequested(String changeId, String planName) {
		for (String recipient : users.findActiveUsernamesByPermissionCode(
				"education:exam:change:approve")) {
			notifications.enqueueUserEvent(
					"EDUCATION_EXAM_CHANGE_REQUESTED", changeId,
					recipient, "已发布考试变更待审批",
					planName + " 的改期、换场或取消申请待处理。",
					"REQUESTED");
		}
	}

	public void publishedChangeDecided(
			ExamPublishedChange change,
			String planName) {
		boolean approved = "APPROVED".equals(change.getStatus());
		notifications.enqueueUserEvent(
				"EDUCATION_EXAM_CHANGE_DECIDED", change.getId(),
				change.getRequestedBy(), "考试变更审批结果",
				approved ? "考试变更已批准，版本 " + change.getAppliedPlanVersion()
						: "考试变更申请已驳回。",
				"DECIDED");
		if (!approved) {
			return;
		}
		List<ExamSession> affected = "CANCEL_PLAN".equals(change.getChangeType())
				? sessions.findByPlanIdOrderByExamDateAscStartTimeAsc(change.getPlanId())
				: sessions.findById(change.getSessionId()).stream().toList();
		for (ExamSession session : affected) {
			for (ExamRoom room : rooms.findBySessionId(session.getId())) {
				String content = planName + " 考试安排已变更："
						+ change.getChangeType() + "，请重新核对考试时间和地点。";
				for (ExamCandidate candidate : candidates
						.findByRoomIdOrderBySeatNoAsc(room.getId())) {
					// aggregate_id 只保存主业务聚合，必须满足 Outbox 的 64 字符约束。
					// 具体考生或监考任务放入幂等发生键，仍可避免重复通知。
					notifyProfile("STUDENT", candidate.getStudentId(),
							"EDUCATION_EXAM_CHANGED",
							change.getId(),
							"考试安排变更", content,
							"APPROVED:" + candidate.getId());
				}
				for (ExamInvigilation duty : assignments
						.findByRoomIdAndStatus(room.getId(),
								"CANCELLED".equals(session.getStatus())
										? "CANCELLED" : "ASSIGNED")) {
					notifyProfile("TEACHER", duty.getTeacherId(),
							"EDUCATION_EXAM_CHANGED",
							change.getId(),
							"监考安排变更", content,
							"APPROVED:" + duty.getId());
				}
			}
		}
	}

	public void changeRequested(String requestId, String reason) {
		for (String recipient : users.findActiveUsernamesByPermissionCode(
				"education:exam:invigilation:manage")) {
			notifications.enqueueUserEvent(
					"EDUCATION_INVIGILATION_CHANGE_REQUESTED",
					requestId, recipient, "监考调换待处理", reason,
					"REQUESTED");
		}
	}

	public void changeDecided(
			String changeId,
			String applicant,
			String newTeacherId,
			boolean approved) {
		String content = approved ? "监考调换已批准，请查看最新监考安排"
				: "监考调换申请未通过，请继续履行原监考任务";
		notifications.enqueueUserEvent(
				"EDUCATION_INVIGILATION_CHANGE_DECIDED", changeId,
				applicant, "监考调换处理结果", content,
				"DECIDED");
		if (approved && newTeacherId != null) {
			notifyProfile(
					"TEACHER", newTeacherId,
					"EDUCATION_INVIGILATION_REPLACEMENT", changeId,
					"新增监考任务", "您已被安排接替监考，请查看任务详情",
					"REPLACED");
		}
	}

	private void notifyProfile(
			String type,
			String profileId,
			String eventType,
			String aggregateId,
			String title,
			String content,
			String occurrenceKey) {
		List<EducationUserBinding> recipients = bindings
				.findByProfileTypeAndProfileIdInAndStatus(
						type, List.of(profileId), "ACTIVE");
		for (EducationUserBinding recipient : recipients) {
			notifications.enqueueUserEvent(
					eventType, aggregateId, recipient.getUsername(),
						title, content, occurrenceKey);
		}
	}
}
