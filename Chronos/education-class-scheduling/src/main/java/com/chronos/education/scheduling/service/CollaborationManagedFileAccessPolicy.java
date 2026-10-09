package com.chronos.education.scheduling.service;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.IEmployeeAssignmentRepository;
import com.chronos.education.scheduling.repository.CollaborationFileShareRepository;
import com.chronos.education.scheduling.repository.OfficeApprovalRequestRepository;
import com.chronos.education.scheduling.repository.OfficialDocumentRepository;
import com.chronos.file.service.ManagedFileAccessPolicy;
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
	private final CollaborationOfficeService collaboration;

	public CollaborationManagedFileAccessPolicy(
			CollaborationFileShareRepository shares,
			OfficialDocumentRepository documents,
			OfficeApprovalRequestRepository requests,
			IAdminUserRepository accounts,
			IEmployeeAssignmentRepository assignments,
			CollaborationOfficeService collaboration) {
		this.shares = shares;
		this.documents = documents;
		this.requests = requests;
		this.accounts = accounts;
		this.assignments = assignments;
		this.collaboration = collaboration;
	}

	@Override
	public boolean canRead(String username, String businessType, String businessId) {
		if (SHARE_TYPE.equals(businessType)) {
			String organizationId = organizationId(username);
			return shares.findById(businessId)
					.map(value -> collaboration.canReadShare(value, username, organizationId))
					.orElse(false);
		}
		if (DOCUMENT_TYPE.equals(businessType)) {
			return documents.findById(businessId)
					.map(value -> username.equals(value.getDrafterUsername())
							|| "ISSUED".equals(value.getStatus())
							|| "ARCHIVED".equals(value.getStatus()))
					.orElse(false);
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
					.map(value -> username.equals(value.getOwnerUsername()))
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
