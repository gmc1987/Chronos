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
		if (records.findByResourceTypeAndResourceId(type, id).filter(r -> "SUBMITTED".equals(r.getStatus())
				|| "REVIEWING".equals(r.getStatus())).isPresent())
			throw new IllegalStateException("该资源已在审核中");
		if (offeringId != null && !offeringId.isBlank()) scopes.assertOfferingAccess(scopes.resolve(auth.getName()), offeringId);
		String key = "EDU_TEACHING:" + type + ":" + id;
		Map<String,Object> data = form == null ? Map.of() : new java.util.HashMap<>(form);
		data.put("resourceType", type); data.put("resourceId", id); data.put("offeringId", offeringId == null ? "" : offeringId);
		var instance = workflows.startByCode(FLOW, key, data, auth.getName());
		TeachingReviewRecord record = records.findByResourceTypeAndResourceId(type,id).orElseGet(TeachingReviewRecord::new);
		record.setResourceType(type); record.setResourceId(id); record.setOfferingId(offeringId);
		record.setBusinessKey(key); record.setWorkflowInstanceId(instance.getId()); record.setStatus("SUBMITTED");
		record.setDecision(null); record.setComment(null);
		return records.save(record);
	}

	@Transactional(readOnly=true)
	public TeachingReviewRecord status(String type, String id, Authentication auth) {
		TeachingReviewRecord record = records.findByResourceTypeAndResourceId(type,id)
				.orElseThrow(() -> new IllegalArgumentException("尚未提交审核"));
		if (record.getOfferingId()!=null && !record.getOfferingId().isBlank())
			scopes.assertOfferingAccess(scopes.resolve(auth.getName()), record.getOfferingId());
		return record;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT) public void completed(WorkflowCompletedEvent event) {
		if (!FLOW.equals(event.flowCode())) return;
		writeBack(event.businessKey(), "PUBLISHED", "APPROVED", "");
	}
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT) public void rejected(WorkflowRejectedEvent event) {
		if (!FLOW.equals(event.flowCode())) return;
		writeBack(event.businessKey(), "DRAFT", "REJECTED", event.comment());
	}
	private void writeBack(String key, String resourceStatus, String decision, String comment) {
		records.findByBusinessKey(key).ifPresent(r -> {
			if ("PUBLISHED".equals(r.getStatus()) || "REJECTED".equals(r.getDecision())) return;
			r.setStatus(resourceStatus); r.setDecision(decision); r.setComment(comment); records.save(r);
			String[] parts=key.split(":",3);
			if (parts.length==3) {
				Object entity = entity(parts[1], parts[2]);
				if (entity != null) {
					try {
						var m = entity.getClass().getMethod("setStatus", String.class);
						m.invoke(entity, resourceStatus);
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
			default -> null;
		};
	}
}
