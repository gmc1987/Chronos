package com.chronos.education.grade.service;

import com.chronos.education.grade.dao.GradeChangeIncidentRepository;
import com.chronos.education.grade.event.GradeChangeApplyFailedEvent;
import com.chronos.education.grade.model.GradeChangeIncident;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.service.iService.IAuditLogService;
import java.time.LocalDateTime;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 成绩更正事故登记、重试状态维护和人工忽略。 */
@Service
public class GradeChangeIncidentService {
	private static final int MAX_ERROR_LENGTH = 2000;

	private final GradeChangeIncidentRepository incidents;
	private final EducationDataScopeService dataScopes;
	private final IAuditLogService audit;

	public GradeChangeIncidentService(
			GradeChangeIncidentRepository incidents,
			EducationDataScopeService dataScopes,
			IAuditLogService audit) {
		this.incidents = incidents;
		this.dataScopes = dataScopes;
		this.audit = audit;
	}

	/** 使用独立事务保存事故，即使原成绩回写事务回滚，恢复入口仍然存在。 */
	@EventListener
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordFailure(GradeChangeApplyFailedEvent event) {
		GradeChangeIncident incident = incidents.findByChangeRequestId(event.changeRequestId())
			.orElseGet(GradeChangeIncident::new);
		incident.setChangeRequestId(event.changeRequestId());
		incident.setWorkflowInstanceId(event.workflowInstanceId());
		incident.setStatus("OPEN");
		incident.setLastError(safeError(event.errorMessage()));
		incident.setResolutionNote(null);
		incident.setResolvedBy(null);
		incident.setResolvedAt(null);
		incidents.save(incident);
	}

	@Transactional(readOnly = true)
	public Page<GradeChangeIncident> page(String status, int page, int size, String actor) {
		requireAdministrator(actor);
		String normalizedStatus = status == null || status.isBlank()
			? "OPEN"
			: status.trim().toUpperCase();
		return incidents.findByStatusOrderByCreateTimeDesc(
			normalizedStatus,
			PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
	}

	@Transactional(readOnly = true)
	public GradeChangeIncident requireOpen(String id, String actor) {
		requireAdministrator(actor);
		GradeChangeIncident incident = incidents.findById(id)
			.orElseThrow(() -> new IllegalArgumentException("成绩更正事故不存在"));
		if (!"OPEN".equals(incident.getStatus())) {
			throw new IllegalStateException("当前事故已经处理");
		}
		return incident;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public GradeChangeIncident markRetryFailed(String id, String error, String actor) {
		requireAdministrator(actor);
		GradeChangeIncident incident = lockOpen(id);
		incident.setRetryCount(incident.getRetryCount() + 1);
		incident.setLastRetryBy(actor);
		incident.setLastRetryAt(LocalDateTime.now());
		incident.setLastError(safeError(error));
		audit.log(actor, "EDU_GRADE_CHANGE_INCIDENT_RETRY_FAILED", "incidentId=" + id);
		return incidents.save(incident);
	}

	@Transactional
	public GradeChangeIncident resolve(String id, String actor) {
		requireAdministrator(actor);
		GradeChangeIncident incident = lockOpen(id);
		incident.setStatus("RESOLVED");
		incident.setRetryCount(incident.getRetryCount() + 1);
		incident.setLastRetryBy(actor);
		incident.setLastRetryAt(LocalDateTime.now());
		incident.setResolvedBy(actor);
		incident.setResolvedAt(LocalDateTime.now());
		incident.setResolutionNote("业务回写重试成功");
		audit.log(actor, "EDU_GRADE_CHANGE_INCIDENT_RESOLVE", "incidentId=" + id);
		return incidents.save(incident);
	}

	@Transactional
	public GradeChangeIncident ignore(String id, String reason, String actor) {
		requireAdministrator(actor);
		if (reason == null || reason.isBlank()) {
			throw new IllegalArgumentException("请填写忽略原因");
		}
		GradeChangeIncident incident = lockOpen(id);
		incident.setStatus("IGNORED");
		incident.setResolvedBy(actor);
		incident.setResolvedAt(LocalDateTime.now());
		incident.setResolutionNote(reason.trim());
		audit.log(actor, "EDU_GRADE_CHANGE_INCIDENT_IGNORE", "incidentId=" + id);
		return incidents.save(incident);
	}

	private GradeChangeIncident lockOpen(String id) {
		GradeChangeIncident incident = incidents.findByIdForUpdate(id)
			.orElseThrow(() -> new IllegalArgumentException("成绩更正事故不存在"));
		if (!"OPEN".equals(incident.getStatus())) {
			throw new IllegalStateException("当前事故已经处理");
		}
		return incident;
	}

	private void requireAdministrator(String actor) {
		if (!dataScopes.resolve(actor).fullAccess()) {
			throw new AccessDeniedException("仅全校数据范围管理员可以处理成绩更正事故");
		}
	}

	private String safeError(String value) {
		String message = value == null || value.isBlank() ? "未知回写错误" : value.trim();
		return message.length() <= MAX_ERROR_LENGTH
			? message
			: message.substring(0, MAX_ERROR_LENGTH);
	}
}
