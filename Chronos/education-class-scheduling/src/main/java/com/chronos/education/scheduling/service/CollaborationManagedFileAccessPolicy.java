package com.chronos.education.scheduling.service;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.IEmployeeAssignmentRepository;
import com.chronos.education.scheduling.repository.CollaborationFileShareRepository;
import com.chronos.education.scheduling.repository.OfficeApprovalRequestRepository;
import com.chronos.education.scheduling.repository.OfficialDocumentRepository;
import com.chronos.file.service.ManagedFileAccessPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;

@Component
@Transactional(readOnly = true)
public class CollaborationManagedFileAccessPolicy implements ManagedFileAccessPolicy {
	public static final String SHARE_TYPE = "COLLABORATION_SHARE";
	public static final String DOCUMENT_TYPE = "OFFICIAL_DOCUMENT";
	public static final String REQUEST_TYPE = "COLLABORATION_OFFICE_REQUEST";

	private final CollaborationFileShareRepository shares;
	private final OfficialDocumentRepository documents;
	private final OfficeApprovalRequestRepository requests;
	private final IAdminUserRepository accounts;
	private final IEmployeeAssignmentRepository assignments;
	@Value("${chronos.delivery.mode:standard}")
	private String deliveryMode;
	private org.springframework.beans.factory.ObjectProvider<com.chronos.workflow.WorkflowSecurityService> workflowSecurity;

	@org.springframework.beans.factory.annotation.Autowired
	void setWorkflowSecurity(org.springframework.beans.factory.ObjectProvider<com.chronos.workflow.WorkflowSecurityService> workflowSecurity) {
		this.workflowSecurity = workflowSecurity;
	}

	public CollaborationManagedFileAccessPolicy(
			CollaborationFileShareRepository shares,
			OfficialDocumentRepository documents,
			OfficeApprovalRequestRepository requests,
			IAdminUserRepository accounts,
			IEmployeeAssignmentRepository assignments) {
		this.shares = shares;
		this.documents = documents;
		this.requests = requests;
		this.accounts = accounts;
		this.assignments = assignments;
	}

	@Override
	public boolean canRead(String username, String businessType, String businessId) {
		if (SHARE_TYPE.equals(businessType)) {
			if ("customer".equals(deliveryMode)) {
				var account = accounts.findByUsername(username);
				return account != null && shares.findById(businessId)
						.map(value -> CollaborationShareAudience.canReadInCustomerDeployment(value, username, account.getOrganizationId()))
						.orElse(false);
			}
			String organizationId = organizationId(username);
			return shares.findById(businessId)
					.map(value -> CollaborationShareAudience.canRead(value, username, organizationId))
					.orElse(false);
		}
		if (DOCUMENT_TYPE.equals(businessType)) {
			return documents.findById(businessId).map(value -> {
				if (username.equals(value.getDrafterUsername())) return true;
				if (!"customer".equals(deliveryMode)) {
					return "ISSUED".equals(value.getStatus()) || "ARCHIVED".equals(value.getStatus());
				}
				if (!sameSchool(username, value.getDrafterUsername())) return false;
				if (value.getIssuedAt() != null && ("ISSUED".equals(value.getStatus()) || "ARCHIVED".equals(value.getStatus()))) {
					return true;
				}
				return "IN_REVIEW".equals(value.getStatus()) && value.getWorkflowInstanceId() != null
						&& workflowSecurity != null && workflowSecurity.getObject()
								.canViewInstance(username, value.getWorkflowInstanceId());
			}).orElse(false);
		}
		if (REQUEST_TYPE.equals(businessType)) {
			return requests.findById(businessId)
					.map(value -> {
						var account = accounts.findByUsername(username);
						return account != null && value.getEmployeeId().equals(account.getEmployeeId());
					})
					.orElse(false);
		}
		return false;
	}

	@Override
	public boolean canWrite(String username, String businessType, String businessId) {
		if (SHARE_TYPE.equals(businessType)) {
			return shares.findById(businessId)
					.map(value -> username.equals(value.getOwnerUsername())
							&& (!"customer".equals(deliveryMode) || canRead(username, businessType, businessId)))
					.orElse(false);
		}
		if (DOCUMENT_TYPE.equals(businessType)) {
			return documents.findById(businessId)
					.map(value -> username.equals(value.getDrafterUsername())
							&& ("DRAFT".equals(value.getStatus()) || "RECEIVED".equals(value.getStatus())))
					.orElse(false);
		}
		if (REQUEST_TYPE.equals(businessType)) {
			return canRead(username, businessType, businessId);
		}
		return false;
	}

	private boolean sameSchool(String username, String owner) {
		var viewer = accounts.findByUsername(username);
		var drafter = accounts.findByUsername(owner);
		return viewer != null && drafter != null && viewer.getOrganizationId() != null
				&& viewer.getOrganizationId().equals(drafter.getOrganizationId());
	}

	private String organizationId(String username) {
		var account = accounts.findByUsername(username);
		if (account == null || account.getEmployeeId() == null) {
			return null;
		}
		return assignments.findCurrentPrimaryAssignment(account.getEmployeeId(), LocalDate.now())
				.map(value -> value.getOrganizationUnitId())
				.orElse(null);
	}
}
