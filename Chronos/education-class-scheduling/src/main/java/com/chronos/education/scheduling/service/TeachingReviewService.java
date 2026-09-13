package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.TeachingReviewRecordRepository;
import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.education.scheduling.model.TeachingReviewRecord;
import com.chronos.workflow.WorkflowService;
import com.chronos.workflow.event.WorkflowCompletedEvent;
import com.chronos.workflow.event.WorkflowRejectedEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Map;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional
public class TeachingReviewService {
	private static final String FLOW = "EDU_TEACHING_CONTENT_REVIEW";
	private final TeachingReviewRecordRepository records;
	private final WorkflowService workflows;
	private final EducationDataScopeService scopes;
	@PersistenceContext private EntityManager em;
	public TeachingReviewService(TeachingReviewRecordRepository records, WorkflowService workflows,
			EducationDataScopeService scopes) { this.records=records; this.workflows=workflows; this.scopes=scopes; }

	public TeachingReviewRecord submit(String type, String id, String offeringId, Map<String,Object> form,
			Authentication auth) {
		var latest = records.findByResourceTypeAndResourceId(type, id);
		if (latest.filter(r -> "SUBMITTED".equals(r.getStatus())
				|| "REVIEWING".equals(r.getStatus())).isPresent())
			throw new IllegalStateException("该资源已在审核中");
		authorizeResource(type, id, offeringId, auth);
		int submissionNo = records.findByResourceTypeAndResourceIdOrderBySubmissionNoDesc(type, id)
				.stream().findFirst().map(r -> r.getSubmissionNo() + 1).orElse(1);
		String key = "EDU_TEACHING:" + type + ":" + id + ":" + submissionNo;
		Map<String,Object> data = form == null ? Map.of() : new java.util.HashMap<>(form);
		data.put("resourceType", type); data.put("resourceId", id); data.put("offeringId", offeringId == null ? "" : offeringId);
		// version/snapshot metadata belongs to the education review record, not
		// the workflow's user form. Passing those internal keys as form fields
		// makes WorkflowService reject them when the published flow has a
		// different (or empty) form schema.
		Map<String, Object> workflowForm = new java.util.LinkedHashMap<>();
		workflowForm.put("resourceType", type);
		workflowForm.put("resourceId", id);
		workflowForm.put("offeringId", offeringId == null ? "" : offeringId);
		var instance = workflows.startByCode(FLOW, key, workflowForm, auth.getName());
		TeachingReviewRecord record = new TeachingReviewRecord();
		record.setResourceType(type); record.setResourceId(id); record.setOfferingId(offeringId);
		record.setBusinessKey(key); record.setWorkflowInstanceId(instance.getId()); record.setStatus("SUBMITTED");
		record.setSubmissionNo(submissionNo); record.setSubmitterId(auth.getName());
		record.setSubmittedAt(java.time.Instant.now());
		Object versionId = data.get("versionId");
		if (versionId != null) record.setVersionId(versionId.toString());
		Object snapshotHash = data.get("snapshotHash");
		if (snapshotHash != null) record.setSnapshotHash(snapshotHash.toString());
		record.setDecision(null); record.setComment(null);
		TeachingReviewRecord saved = records.save(record);
		// 审核提交和资源状态在同一事务落库，防止门户继续展示为可编辑草稿。
		Object resource = entity(type, id);
		if (resource == null) {
			resource = em.find(com.chronos.education.scheduling.model.TeachingCenterResource.class, id);
		}
		if (resource instanceof com.chronos.education.scheduling.model.TeachingCenterResource centerResource) {
			centerResource.setStatus("SUBMITTED");
		} else if (resource != null) {
			try {
				resource.getClass().getMethod("setStatus", String.class).invoke(resource, "SUBMITTED");
			} catch (NoSuchMethodException ignored) {
				// 知识点等没有状态列的资源由审核记录承载审核状态。
			} catch (ReflectiveOperationException exception) {
				throw new IllegalStateException("教学资源提交状态回写失败", exception);
			}
		}
		return saved;
	}

	@Transactional(readOnly=true)
	public TeachingReviewRecord status(String type, String id, Authentication auth) {
		TeachingReviewRecord record = records.findByResourceTypeAndResourceId(type,id)
				.orElseThrow(() -> new IllegalArgumentException("尚未提交审核"));
		authorizeResource(record.getResourceType(), record.getResourceId(), record.getOfferingId(), auth);
		return record;
	}

	@Transactional(readOnly = true)
	public java.util.List<TeachingReviewRecord> history(String type, String id, Authentication auth) {
		var values = records.findByResourceTypeAndResourceIdOrderBySubmissionNoDesc(type, id);
		if (values.isEmpty()) throw new IllegalArgumentException("尚未提交审核");
		authorizeResource(type, id, values.get(0).getOfferingId(), auth);
		return values;
	}

	/** 审核待办只返回当前数据范围内的记录，不能凭主键越权读取。 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED, readOnly = true)
	public java.util.List<TeachingReviewRecord> pending(Authentication auth) {
		var result = new java.util.ArrayList<TeachingReviewRecord>();
		for (TeachingReviewRecord record : records.findByStatusInOrderBySubmittedAtAsc(
				java.util.Set.of("SUBMITTED", "REVIEWING"))) {
			try {
				authorizeResource(record.getResourceType(), record.getResourceId(),
						record.getOfferingId(), auth);
				result.add(record);
			} catch (org.springframework.security.access.AccessDeniedException
					| IllegalArgumentException ignored) {
				// Data scope is an intentional filter for reviewer queues.
			}
		}
		return result;
	}

	/**
	 * Rejection/return is a domain command. It only reopens a draft; approval
	 * and rejection decisions themselves remain exclusively Workflow actions.
	 */
	public TeachingReviewRecord revise(String type, String id, Authentication auth) {
		TeachingReviewRecord record = status(type, id, auth);
		if (!"REJECTED".equals(record.getDecision()) && !"DRAFT".equals(record.getStatus()))
			throw new IllegalStateException("只有驳回内容可以修订");
		Object resource = entity(type, id);
		if (resource != null) {
			try {
				resource.getClass().getMethod("setStatus", String.class).invoke(resource, "DRAFT");
			} catch (NoSuchMethodException ignored) {
				// Resources without a status column use the review record projection.
			} catch (ReflectiveOperationException ex) {
				throw new IllegalStateException("无法切换为草稿", ex);
			}
		}
		return record;
	}

	private void authorizeResource(String type, String id, String offeringId, Authentication auth) {
		EducationDataScope scope = scopes.resolve(auth.getName());
		if (offeringId != null && !offeringId.isBlank()) {
			scopes.assertOfferingAccess(scope, offeringId);
			return;
		}
		if ("KNOWLEDGE_POINT".equals(type)) {
			var point = em.find(com.chronos.education.scheduling.model.KnowledgePoint.class, id);
			if (point == null) throw new IllegalArgumentException("知识点不存在");
			scopes.assertCourseAccess(scope, point.getCourseId());
			return;
		}
		if ("MISTAKE".equals(type) || "ERROR_BOOK".equals(type)) {
			var book = em.find(com.chronos.education.scheduling.model.ErrorBook.class, id);
			if (book == null) throw new IllegalArgumentException("错题本不存在");
			scopes.assertStudentAccess(scope, book.getStudentId());
			return;
		}
		scopes.assertFullAccess(scope);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void completed(WorkflowCompletedEvent event) {
		if (!FLOW.equals(event.flowCode())) return;
		writeBack(event.businessKey(), "PUBLISHED", "APPROVED", "");
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void rejected(WorkflowRejectedEvent event) {
		if (!FLOW.equals(event.flowCode())) return;
		writeBack(event.businessKey(), "DRAFT", "REJECTED", event.comment());
	}
	private void writeBack(String key, String resourceStatus, String decision, String comment) {
		records.findByBusinessKey(key).ifPresent(r -> {
			if ("PUBLISHED".equals(r.getStatus()) || "REJECTED".equals(r.getDecision())) return;
			r.setStatus(resourceStatus); r.setDecision(decision); r.setComment(comment);
			r.setCompletedAt(java.time.Instant.now()); records.save(r);
			if (r.getVersionId() != null) {
				if ("PLAN".equals(r.getResourceType())) {
					var version = em.find(com.chronos.education.scheduling.model.TeachingPlanVersion.class, r.getVersionId());
					if (version != null) {
						version.setStatus(resourceStatus); if ("PUBLISHED".equals(resourceStatus)) version.setPublishedAt(java.time.Instant.now());
						var plan = em.find(com.chronos.education.scheduling.model.TeachingPlan.class, r.getResourceId());
						if (plan != null && "PUBLISHED".equals(resourceStatus)) plan.setPublishedVersionNo(version.getVersionNo());
					}
				} else if ("LESSON_PLAN".equals(r.getResourceType())) {
					var version = em.find(com.chronos.education.scheduling.model.LessonPlanVersion.class, r.getVersionId());
					if (version != null) version.setStatus(resourceStatus);
					if ("PUBLISHED".equals(resourceStatus) && version != null) {
						var lesson = em.find(com.chronos.education.scheduling.model.LessonPlan.class, r.getResourceId());
						if (lesson != null) lesson.setPublishedVersionNo(version.getVersionNo());
					}
				} else if ("COURSEWARE".equals(r.getResourceType())) {
					var version = em.find(com.chronos.education.scheduling.model.CoursewareVersion.class, r.getVersionId());
					if (version != null) {
						version.setStatus("PUBLISHED".equals(resourceStatus) ? "APPROVED" : resourceStatus);
						if ("PUBLISHED".equals(resourceStatus)) {
							version.setPublishedAt(java.time.Instant.now());
							var courseware = em.find(com.chronos.education.scheduling.model.Courseware.class, r.getResourceId());
							if (courseware != null) courseware.setPublishedVersionNo(version.getVersionNo());
						}
					}
				} else if ("MATERIAL".equals(r.getResourceType())) {
					var version = em.find(com.chronos.education.scheduling.model.TeachingMaterialVersion.class, r.getVersionId());
					if (version != null) {
						version.setStatus("PUBLISHED".equals(resourceStatus) ? "APPROVED" : resourceStatus);
						if ("PUBLISHED".equals(resourceStatus)) {
							version.setPublishedAt(java.time.Instant.now());
							var material = em.find(com.chronos.education.scheduling.model.TeachingMaterial.class, r.getResourceId());
							if (material != null) material.setPublishedVersionNo(version.getVersionNo());
						}
					}
				}
			}
			String[] parts=key.split(":",4);
			if (parts.length>=3) {
				Object entity = entity(parts[1], parts[2]);
				if (entity == null) {
					entity = em.find(com.chronos.education.scheduling.model.TeachingCenterResource.class, parts[2]);
				}
				if (entity != null) {
					try {
						var m = entity.getClass().getMethod("setStatus", String.class);
						m.invoke(entity, resourceStatus);
						if ("PUBLISHED".equals(resourceStatus)
								&& entity instanceof com.chronos.education.scheduling.model.ResearchResult result) {
							result.setPublishedAt(java.time.LocalDateTime.now());
						}
					} catch (NoSuchMethodException ignored) {
						if (entity instanceof com.chronos.education.scheduling.model.KnowledgePoint point)
							point.setEnabled("PUBLISHED".equals(resourceStatus));
					} catch (ReflectiveOperationException exception) {
						throw new IllegalStateException("教学资源审核状态回写失败", exception);
					}
				}
			}
		});
	}
	private Object entity(String type,String id) {
		return switch(type) {
			case "PLAN" -> em.find(com.chronos.education.scheduling.model.TeachingPlan.class,id);
			case "LESSON_PLAN" -> em.find(com.chronos.education.scheduling.model.LessonPlan.class,id);
			case "PREPARATION" -> em.find(com.chronos.education.scheduling.model.Preparation.class,id);
			case "COURSEWARE" -> em.find(com.chronos.education.scheduling.model.Courseware.class,id);
			case "MATERIAL" -> em.find(com.chronos.education.scheduling.model.TeachingMaterial.class,id);
			case "QUESTION_BANK" -> em.find(com.chronos.education.scheduling.model.QuestionBank.class,id);
			case "QUESTION" -> em.find(com.chronos.education.scheduling.model.Question.class,id);
			case "KNOWLEDGE_POINT" -> em.find(com.chronos.education.scheduling.model.KnowledgePoint.class,id);
			case "MISTAKE", "ERROR_BOOK" -> em.find(com.chronos.education.scheduling.model.ErrorBook.class,id);
			case "RESEARCH" -> em.find(com.chronos.education.scheduling.model.ResearchGroup.class,id);
			case "RESEARCH_ACTIVITY" -> em.find(com.chronos.education.scheduling.model.ResearchActivity.class,id);
			case "RESEARCH_RESULT" -> em.find(com.chronos.education.scheduling.model.ResearchResult.class,id);
			default -> null;
		};
	}
}
