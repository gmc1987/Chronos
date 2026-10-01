package com.chronos.education.grade.service;

import com.chronos.Idao.workflow.IWorkflowTaskRepository;
import com.chronos.education.grade.dao.*;
import com.chronos.education.grade.dto.GradeProductionDtos.*;
import com.chronos.education.grade.event.GradeChangeApplyFailedEvent;
import com.chronos.education.grade.model.*;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.service.iService.IAuditLogService;
import com.chronos.workflow.WorkflowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import com.chronos.workflow.event.WorkflowCompletedEvent;
import com.chronos.workflow.event.WorkflowRejectedEvent;

/** 成绩更正和补考重修的不可变版本链。 */
@Service
public class GradeProductionService {
	private final GradeChangeRequestRepository changes;
	private final MakeupExamRecordRepository makeups;
	private final CourseGradeRepository courseGrades;
	private final GradebookRepository gradebooks;
	private final GradebookStudentRepository gradebookStudents;
	private final AssessmentSchemeRepository schemes;
	private final GradePublishSnapshotRepository snapshots;
	private final CourseOfferingRepository offerings;
	private final EducationDataScopeService dataScopes;
	private final WorkflowService workflows;
	private final IWorkflowTaskRepository workflowTasks;
	private final DomainEventOutboxService domainEvents;
	private final IAuditLogService audit;
	private final ObjectMapper json;
	private final ApplicationEventPublisher events;

	public GradeProductionService(
			GradeChangeRequestRepository changes,
			MakeupExamRecordRepository makeups,
			CourseGradeRepository courseGrades,
			GradebookRepository gradebooks,
			GradebookStudentRepository gradebookStudents,
			AssessmentSchemeRepository schemes,
			GradePublishSnapshotRepository snapshots,
			CourseOfferingRepository offerings,
			EducationDataScopeService dataScopes,
			WorkflowService workflows,
			IWorkflowTaskRepository workflowTasks,
			DomainEventOutboxService domainEvents,
			IAuditLogService audit,
			ObjectMapper json,
			ApplicationEventPublisher events) {
		this.changes = changes;
		this.makeups = makeups;
		this.courseGrades = courseGrades;
		this.gradebooks = gradebooks;
		this.gradebookStudents = gradebookStudents;
		this.schemes = schemes;
		this.snapshots = snapshots;
		this.offerings = offerings;
		this.dataScopes = dataScopes;
		this.workflows = workflows;
		this.workflowTasks = workflowTasks;
		this.domainEvents = domainEvents;
		this.audit = audit;
		this.json = json;
		this.events = events;
	}

	@Transactional(readOnly = true)
	public List<GradeChangeRequest> changes(String gradebookId, String actor) {
		authorizedGradebook(gradebookId, actor);
		return changes.findByGradebookIdOrderByCreateTimeDesc(gradebookId);
	}

	@Transactional(readOnly = true)
	public List<PublishedGradeView> publishedGrades(String gradebookId, String actor) {
		authorizedGradebook(gradebookId, actor);
		Map<String, GradebookStudent> roster = students(gradebookId);
		Map<String, CourseGrade> latest = new LinkedHashMap<>();
		courseGrades.findAllByGradebookIdIn(Set.of(gradebookId)).forEach(value -> latest.merge(
			value.getStudentId(),
			value,
			(first, second) -> first.getVersionNo() >= second.getVersionNo() ? first : second));
		return latest.values().stream().map(value -> {
			GradebookStudent student = roster.get(value.getStudentId());
			return new PublishedGradeView(
				value.getId(), value.getStudentId(),
				student == null ? "" : student.getStudentNo(),
				student == null ? "" : student.getStudentName(),
				value.getTotalScore(), value.getPassed(), value.getVersionNo());
		}).toList();
	}

	@Transactional
	public GradeChangeRequest requestChange(String gradebookId, ChangeRequestCommand command, String actor) {
		Gradebook gradebook = authorizedGradebook(gradebookId, actor);
		CourseGrade source = courseGrades.findById(command.courseGradeId())
			.orElseThrow(() -> new IllegalArgumentException("原成绩不存在"));
		if (!gradebookId.equals(source.getGradebookId())) {
			throw new AccessDeniedException("成绩不属于当前成绩册");
		}
		assertLatestGrade(source);
		if (changes.existsByCourseGradeIdAndStatus(source.getId(), "REVIEWING")) {
			throw new IllegalStateException("该成绩已有待审核的更正申请");
		}
		validateScore(gradebook, command.afterScore());
		if (command.reason() == null || command.reason().isBlank()) {
			throw new IllegalArgumentException("请填写成绩更正原因");
		}
		GradeChangeRequest request = new GradeChangeRequest();
		request.setGradebookId(gradebookId);
		request.setCourseGradeId(source.getId());
		request.setStudentId(source.getStudentId());
		request.setBeforeScore(source.getTotalScore());
		request.setAfterScore(command.afterScore());
		request.setReason(command.reason().trim());
		request.setStatus("REVIEWING");
		request = changes.save(request);
		var instance = workflows.startByCode(
			"EDU_GRADE_CHANGE_REVIEW",
			request.getId(),
			Map.of("changeRequestId", request.getId(), "gradebookId", gradebookId, "submitter", actor),
			actor);
		request.setWorkflowInstanceId(instance.getId());
		audit.log(actor, "EDU_GRADE_CHANGE_REQUEST", "changeRequestId=" + request.getId());
		return changes.save(request);
	}

	@Transactional
	public GradeChangeRequest decideChange(String requestId, ChangeDecisionCommand command, String actor) {
		GradeChangeRequest request = changes.findByIdForUpdate(requestId)
			.orElseThrow(() -> new IllegalArgumentException("成绩更正单不存在"));
		if (!"REVIEWING".equals(request.getStatus())) {
			throw new IllegalStateException("当前更正单不可审核");
		}
		var task = workflowTasks.findById(command.taskId())
			.orElseThrow(() -> new IllegalArgumentException("审核任务不存在"));
		if (!requestId.equals(workflows.instance(task.getInstanceId()).getBusinessKey())) {
			throw new AccessDeniedException("审核任务不属于当前成绩更正单");
		}
		var instance = command.approved()
			? workflows.completeTask(command.taskId(), true, command.comment(), actor)
			: workflows.rejectTask(command.taskId(), "", command.comment(), actor);
		if ("REJECTED".equals(instance.getStatus())) {
			request.setStatus("REJECTED");
		} else if ("COMPLETED".equals(instance.getStatus())) {
			CourseGrade published = publishNewVersion(
				request.getGradebookId(), request.getStudentId(), request.getAfterScore(), "CHANGE", actor);
			request.setStatus("APPROVED");
			request.setApprovedBy(actor);
			request.setApprovedAt(LocalDateTime.now());
			audit.log(actor, "EDU_GRADE_CHANGE_APPROVE",
				"changeRequestId=" + requestId + ",gradeId=" + published.getId());
		}
		return changes.save(request);
	}

	@Transactional(readOnly = true)
	public List<MakeupExamRecord> makeups(String gradebookId, String actor) {
		authorizedGradebook(gradebookId, actor);
		return makeups.findByGradebookIdOrderByCreateTimeDesc(gradebookId);
	}

	@Transactional
	public MakeupExamRecord registerMakeup(String gradebookId, MakeupRegisterCommand command, String actor) {
		Gradebook gradebook = authorizedGradebook(gradebookId, actor);
		CourseGrade source = courseGrades.findById(command.sourceGradeId())
			.orElseThrow(() -> new IllegalArgumentException("原成绩不存在"));
		String type = command.attemptType() == null ? "" : command.attemptType().toUpperCase();
		if (!Set.of("MAKEUP", "RETAKE").contains(type)) {
			throw new IllegalArgumentException("类型只能是补考或重修");
		}
		if (!source.getGradebookId().equals(gradebook.getId())) {
			throw new AccessDeniedException("成绩不属于当前成绩册");
		}
		assertLatestGrade(source);
		if (makeups.existsBySourceGradeIdAndAttemptType(source.getId(), type)) {
			throw new IllegalStateException("该成绩已登记相同类型记录");
		}
		MakeupExamRecord record = new MakeupExamRecord();
		record.setGradebookId(gradebookId);
		record.setSourceGradeId(source.getId());
		record.setStudentId(source.getStudentId());
		record.setAttemptType(type);
		record.setRemark(command.remark());
		record.setStatus("REGISTERED");
		audit.log(actor, "EDU_GRADE_MAKEUP_REGISTER", "gradebookId=" + gradebookId + ",type=" + type);
		return makeups.save(record);
	}

	@Transactional
	public MakeupExamRecord saveMakeupResult(String id, MakeupResultCommand command, String actor) {
		MakeupExamRecord record = makeups.findByIdForUpdate(id)
			.orElseThrow(() -> new IllegalArgumentException("补考重修记录不存在"));
		Gradebook gradebook = authorizedGradebook(record.getGradebookId(), actor);
		validateScore(gradebook, command.resultScore());
		if (!Set.of("REGISTERED", "SCORED").contains(record.getStatus())) {
			throw new IllegalStateException("当前记录不可录入成绩");
		}
		record.setResultScore(command.resultScore());
		record.setRemark(command.remark());
		record.setStatus("SCORED");
		return makeups.save(record);
	}

	@Transactional
	public MakeupExamRecord publishMakeup(String id, String actor) {
		MakeupExamRecord record = makeups.findByIdForUpdate(id)
			.orElseThrow(() -> new IllegalArgumentException("补考重修记录不存在"));
		authorizedGradebook(record.getGradebookId(), actor);
		if (!"SCORED".equals(record.getStatus()) || record.getResultScore() == null) {
			throw new IllegalStateException("请先录入补考重修成绩");
		}
		CourseGrade grade = publishNewVersion(
			record.getGradebookId(), record.getStudentId(), record.getResultScore(), record.getAttemptType(), actor);
		record.setPublishedGradeId(grade.getId());
		record.setStatus("PUBLISHED");
		audit.log(actor, "EDU_GRADE_MAKEUP_PUBLISH", "makeupId=" + id + ",gradeId=" + grade.getId());
		return makeups.save(record);
	}

	/** 门户通用待办完成后自动回写业务，避免审批人必须进入成绩中心专用页面。 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void workflowCompleted(WorkflowCompletedEvent event) {
		if (!"EDU_GRADE_CHANGE_REVIEW".equals(event.flowCode())) {
			return;
		}
		GradeChangeRequest request = changes.findByIdForUpdate(event.businessKey()).orElse(null);
		if (request == null || !"REVIEWING".equals(request.getStatus())) {
			return;
		}
		try {
			applyApprovedChange(request, event.completedBy(), "EDU_GRADE_CHANGE_APPROVE");
		} catch (RuntimeException exception) {
			// 成绩回写必须整体回滚，但事故使用独立事务保留，便于管理员重放。
			TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
			events.publishEvent(new GradeChangeApplyFailedEvent(
				request.getId(),
				request.getWorkflowInstanceId(),
				exception.getMessage()));
		}
	}

	/**
	 * 事故重放只接受已完成的正式工作流实例。已成功回写的申请直接幂等返回，
	 * 避免网络重试或事故状态更新失败导致重复成绩版本。
	 */
	@Transactional
	public GradeChangeRequest replayApprovedChange(String requestId, String actor) {
		GradeChangeRequest request = changes.findByIdForUpdate(requestId)
			.orElseThrow(() -> new IllegalArgumentException("成绩更正单不存在"));
		if ("APPROVED".equals(request.getStatus())) {
			return request;
		}
		if (!"REVIEWING".equals(request.getStatus())) {
			throw new IllegalStateException("当前更正单不可重放");
		}
		if (request.getWorkflowInstanceId() == null
				|| !"COMPLETED".equals(workflows.instance(request.getWorkflowInstanceId()).getStatus())) {
			throw new IllegalStateException("只能重放已完成审批的成绩更正单");
		}
		applyApprovedChange(request, actor, "EDU_GRADE_CHANGE_INCIDENT_REPLAY");
		return request;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void workflowRejected(WorkflowRejectedEvent event) {
		if (!"EDU_GRADE_CHANGE_REVIEW".equals(event.flowCode())) {
			return;
		}
		changes.findById(event.businessKey()).ifPresent(request -> {
			if ("REVIEWING".equals(request.getStatus())) {
				request.setStatus("REJECTED");
				changes.save(request);
				audit.log(event.rejectedBy(), "EDU_GRADE_CHANGE_REJECT",
					"changeRequestId=" + request.getId());
			}
		});
	}

	private CourseGrade publishNewVersion(
			String gradebookId, String studentId, BigDecimal score, String reason, String actor) {
		// 同一成绩册的所有成绩发布共用版本序列，行锁保证“读取最大版本 + 1”的原子性。
		Gradebook gradebook = gradebooks.findByIdForUpdate(gradebookId)
			.orElseThrow(() -> new IllegalArgumentException("成绩册不存在"));
		int version = courseGrades.findAllByGradebookIdIn(Set.of(gradebookId)).stream()
			.map(CourseGrade::getVersionNo).max(Integer::compareTo).orElse(0) + 1;
		Map<String, Object> snapshotValue = new LinkedHashMap<>();
		snapshotValue.put("snapshotVersion", 2);
		snapshotValue.put("gradebookId", gradebookId);
		snapshotValue.put("studentId", studentId);
		snapshotValue.put("score", score);
		snapshotValue.put("reason", reason);
		snapshotValue.put("capturedAt", LocalDateTime.now().toString());
		String snapshot = write(snapshotValue);
		String hash = hash(snapshot);
		AssessmentScheme scheme = schemes.findById(gradebook.getSchemeId()).orElseThrow();
		CourseGrade grade = new CourseGrade();
		grade.setGradebookId(gradebookId);
		grade.setStudentId(studentId);
		grade.setTotalScore(score);
		applyGradeLevel(grade, score);
		grade.setPassed(score.compareTo(scheme.getPassScore()) >= 0);
		grade.setVersionNo(version);
		grade.setSnapshotHash(hash);
		grade = courseGrades.save(grade);
		GradePublishSnapshot publication = new GradePublishSnapshot();
		publication.setGradebookId(gradebookId);
		publication.setVersionNo(version);
		publication.setSnapshotJson(snapshot);
		publication.setSnapshotHash(hash);
		publication.setPublishedBy(actor);
		publication.setPublishedAt(LocalDateTime.now());
		snapshots.save(publication);
		Map<String, Object> event = Map.of(
			"eventId", UUID.randomUUID().toString(),
			"eventType", "CourseGradeChangedV1",
			"occurredAt", OffsetDateTime.now().toString(),
			"gradebookId", gradebookId,
			"studentId", studentId,
			"versionNo", version,
			"snapshotHash", hash,
			"reason", reason);
		domainEvents.enqueue("CourseGradeChangedV1", gradebookId, grade.getId(), event);
		return grade;
	}

	private void applyApprovedChange(
			GradeChangeRequest request,
			String actor,
			String auditAction) {
		CourseGrade grade = publishNewVersion(
			request.getGradebookId(),
			request.getStudentId(),
			request.getAfterScore(),
			"CHANGE",
			actor);
		request.setStatus("APPROVED");
		request.setApprovedBy(actor);
		request.setApprovedAt(LocalDateTime.now());
		changes.save(request);
		audit.log(actor, auditAction,
			"changeRequestId=" + request.getId() + ",gradeId=" + grade.getId());
	}

	private Gradebook authorizedGradebook(String id, String actor) {
		Gradebook gradebook = gradebooks.findById(id)
			.orElseThrow(() -> new IllegalArgumentException("成绩册不存在"));
		var scope = dataScopes.resolve(actor);
		if (!scope.fullAccess() && !scope.teacherIds().contains(gradebook.getTeacherId())) {
			throw new AccessDeniedException("无权访问该成绩册");
		}
		return gradebook;
	}

	private Map<String, GradebookStudent> students(String gradebookId) {
		return gradebookStudents.findByGradebookId(gradebookId).stream()
			.collect(java.util.stream.Collectors.toMap(GradebookStudent::getStudentId, value -> value));
	}

	private void validateScore(Gradebook gradebook, BigDecimal score) {
		BigDecimal total = schemes.findById(gradebook.getSchemeId()).orElseThrow().getTotalScore();
		if (score == null || score.signum() < 0 || score.compareTo(total) > 0) {
			throw new IllegalArgumentException("成绩必须在 0 到方案总分之间");
		}
	}

	private void assertLatestGrade(CourseGrade source) {
		CourseGrade latest = courseGrades
			.findByGradebookIdAndStudentIdOrderByVersionNoDesc(
				source.getGradebookId(),
				source.getStudentId())
			.stream()
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("学生已发布成绩不存在"));
		if (!latest.getId().equals(source.getId())) {
			throw new IllegalStateException("当前成绩已有新版本，请刷新后重新操作");
		}
	}

	private void applyGradeLevel(CourseGrade grade, BigDecimal score) {
		if (score.compareTo(new BigDecimal("90")) >= 0) {
			grade.setGradeLevel("A");
			grade.setGradePoint(new BigDecimal("4.0"));
		} else if (score.compareTo(new BigDecimal("80")) >= 0) {
			grade.setGradeLevel("B");
			grade.setGradePoint(new BigDecimal("3.0"));
		} else if (score.compareTo(new BigDecimal("70")) >= 0) {
			grade.setGradeLevel("C");
			grade.setGradePoint(new BigDecimal("2.0"));
		} else if (score.compareTo(new BigDecimal("60")) >= 0) {
			grade.setGradeLevel("D");
			grade.setGradePoint(new BigDecimal("1.0"));
		} else {
			grade.setGradeLevel("F");
			grade.setGradePoint(BigDecimal.ZERO);
		}
	}

	private String write(Object value) {
		try {
			return json.writeValueAsString(value);
		} catch (Exception exception) {
			throw new IllegalStateException("成绩版本快照序列化失败", exception);
		}
	}

	private String hash(String value) {
		try {
			byte[] bytes = MessageDigest.getInstance("SHA-256")
				.digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(bytes);
		} catch (Exception exception) {
			throw new IllegalStateException("成绩版本哈希失败", exception);
		}
	}
}
