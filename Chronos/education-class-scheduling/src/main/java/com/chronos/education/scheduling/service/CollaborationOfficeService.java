package com.chronos.education.scheduling.service;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.IEmployeeRepository;
import com.chronos.education.scheduling.model.CollaborationFileShare;
import com.chronos.education.scheduling.model.OfficeApprovalRequest;
import com.chronos.education.scheduling.model.OfficeResource;
import com.chronos.education.scheduling.model.OfficialDocument;
import com.chronos.education.scheduling.repository.CollaborationFileShareRepository;
import com.chronos.education.scheduling.repository.OfficeApprovalRequestRepository;
import com.chronos.education.scheduling.repository.OfficeResourceRepository;
import com.chronos.education.scheduling.repository.OfficialDocumentRepository;
import com.chronos.file.service.ManagedFileService;
import com.chronos.workflow.WorkflowService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CollaborationOfficeService {
	public static final String VEHICLE_FLOW = "STAFF_VEHICLE_USE_APPROVAL";
	public static final String SEAL_FLOW = "STAFF_SEAL_USE_APPROVAL";
	public static final String DOCUMENT_FLOW = "OFFICIAL_DOCUMENT_ISSUE_APPROVAL";

	private final OfficeResourceRepository resources;
	private final OfficeApprovalRequestRepository requests;
	private final OfficialDocumentRepository documents;
	private final CollaborationFileShareRepository shares;
	private final IAdminUserRepository accounts;
	private final IEmployeeRepository employees;
	private final WorkflowService workflow;
	private final ManagedFileService files;

	public CollaborationOfficeService(
			OfficeResourceRepository resources,
			OfficeApprovalRequestRepository requests,
			OfficialDocumentRepository documents,
			CollaborationFileShareRepository shares,
			IAdminUserRepository accounts,
			IEmployeeRepository employees,
			WorkflowService workflow,
			ManagedFileService files) {
		this.resources = resources;
		this.requests = requests;
		this.documents = documents;
		this.shares = shares;
		this.accounts = accounts;
		this.employees = employees;
		this.workflow = workflow;
		this.files = files;
	}

	@Transactional(readOnly = true)
	public List<OfficeResource> resources(String type) {
		String normalized = normalizeResourceType(type);
		return resources.findByResourceTypeAndEnabledTrueOrderByResourceNameAsc(normalized);
	}

	@Transactional(readOnly = true)
	public List<OfficeResource> managedResources(String type) {
		return resources.findByResourceTypeOrderByResourceNameAsc(normalizeResourceType(type));
	}

	@Transactional
	public OfficeResource saveResource(String id, ResourceCommand command) {
		OfficeResource value = id == null ? new OfficeResource()
				: resources.findById(id)
						.orElseThrow(() -> new ServiceException("COLLAB_RESOURCE_NOT_FOUND", "资源不存在"));
		value.setResourceType(normalizeResourceType(command.resourceType()));
		value.setResourceCode(required(command.resourceCode(), "资源编码不能为空"));
		value.setResourceName(required(command.resourceName(), "资源名称不能为空"));
		value.setLicensePlate(blankToNull(command.licensePlate()));
		value.setCapacity(command.capacity());
		value.setDescription(blankToNull(command.description()));
		value.setEnabled(command.enabled() == null || command.enabled());
		if ("VEHICLE".equals(value.getResourceType())
				&& (value.getCapacity() == null || value.getCapacity() < 1)) {
			throw new ServiceException("COLLAB_VEHICLE_CAPACITY", "车辆座位数必须大于零");
		}
		return resources.save(value);
	}

	@Transactional
	public OfficeResource disableResource(String id) {
		OfficeResource value = resources.findById(id)
				.orElseThrow(() -> new ServiceException("COLLAB_RESOURCE_NOT_FOUND", "资源不存在"));
		value.setEnabled(false);
		return resources.save(value);
	}

	@Transactional(readOnly = true)
	public List<OfficeApprovalRequest> myRequests(String username) {
		return requests.findByEmployeeIdOrderByCreateTimeDesc(requireEmployeeId(username));
	}

	@Transactional(readOnly = true)
	public List<OfficeApprovalRequest> managedRequests() {
		return requests.findAllByOrderByCreateTimeDesc();
	}

	@Transactional
	public OfficeApprovalRequest createRequest(String username, OfficeRequestCommand command) {
		String type = normalizeResourceType(command.requestType());
		String employeeId = requireEmployeeId(username);
		OfficeResource resource = resources.findLockedById(command.resourceId())
				.filter(value -> Boolean.TRUE.equals(value.getEnabled()) && type.equals(value.getResourceType()))
				.orElseThrow(() -> new ServiceException("COLLAB_RESOURCE_NOT_FOUND", "所选资源不存在或已停用"));
		if (command.startAt() == null || command.endAt() == null || !command.endAt().isAfter(command.startAt())) {
			throw new ServiceException("COLLAB_TIME_INVALID", "结束时间必须晚于开始时间");
		}
		if ("VEHICLE".equals(type)) {
			if (command.destination() == null || command.destination().isBlank()) {
				throw new ServiceException("COLLAB_DESTINATION_REQUIRED", "用车目的地不能为空");
			}
			int passengers = command.passengerCount() == null ? 1 : command.passengerCount();
			if (passengers < 1 || (resource.getCapacity() != null && passengers > resource.getCapacity())) {
				throw new ServiceException("COLLAB_VEHICLE_CAPACITY", "乘车人数超出车辆容量");
			}
			if (requests.countConflicts(resource.getId(), command.startAt(), command.endAt()) > 0) {
				throw new ServiceException("COLLAB_VEHICLE_CONFLICT", "车辆在所选时间段已被占用");
			}
		} else {
			if (command.documentTitle() == null || command.documentTitle().isBlank()
					|| command.copyCount() == null || command.copyCount() < 1) {
				throw new ServiceException("COLLAB_SEAL_DOCUMENT_REQUIRED", "用印文件名称和份数不能为空");
			}
		}

		OfficeApprovalRequest value = new OfficeApprovalRequest();
		value.setRequestType(type);
		value.setEmployeeId(employeeId);
		value.setResourceId(resource.getId());
		value.setStartAt(command.startAt());
		value.setEndAt(command.endAt());
		value.setPurpose(required(command.purpose(), "申请事由不能为空"));
		value.setDestination(blankToNull(command.destination()));
		value.setPassengerCount(command.passengerCount());
		value.setDocumentTitle(blankToNull(command.documentTitle()));
		value.setCopyCount(command.copyCount());
		value.setAttachmentFileId(blankToNull(command.attachmentFileId()));
		value.setStatus("PENDING");
		value.setBusinessKey(type.toLowerCase() + ":" + UUID.randomUUID());
		value = requests.save(value);
		if (value.getAttachmentFileId() != null) {
			files.bind(
					List.of(value.getAttachmentFileId()),
					CollaborationManagedFileAccessPolicy.REQUEST_TYPE,
					value.getId(),
					username);
		}
		String businessKey = value.getBusinessKey();
		String flowCode = "VEHICLE".equals(type) ? VEHICLE_FLOW : SEAL_FLOW;
		Map<String, Object> formData = new LinkedHashMap<>();
		formData.put("requestId", value.getId());
		formData.put("requestType", type);
		formData.put("resourceName", resource.getResourceName());
		formData.put("purpose", value.getPurpose());
		formData.put("startAt", value.getStartAt().toString());
		formData.put("endAt", value.getEndAt().toString());
		formData.put("destination", value.getDestination() == null ? "" : value.getDestination());
		formData.put("passengerCount", value.getPassengerCount() == null ? "" : value.getPassengerCount());
		formData.put("documentTitle", value.getDocumentTitle() == null ? "" : value.getDocumentTitle());
		formData.put("copyCount", value.getCopyCount() == null ? "" : value.getCopyCount());
		formData.put(
				"attachmentFileId",
				value.getAttachmentFileId() == null ? "" : value.getAttachmentFileId());
		var started = workflow.startByCode(flowCode, businessKey, formData, username);
		value.setBusinessKey(businessKey);
		value.setWorkflowInstanceId(started.getId());
		return requests.save(value);
	}

	@Transactional
	public OfficeApprovalRequest withdraw(String username, String id) {
		OfficeApprovalRequest value = ownedRequest(username, id);
		if (!"PENDING".equals(value.getStatus())) {
			throw new ServiceException("COLLAB_REQUEST_NOT_PENDING", "只有审批中的申请可以撤回");
		}
		workflow.withdraw(value.getWorkflowInstanceId(), "业务申请撤回", username);
		value.setStatus("WITHDRAWN");
		value.setWithdrawnBy(username);
		value.setWithdrawnAt(LocalDateTime.now());
		return requests.save(value);
	}

	@Transactional
	public OfficeApprovalRequest completeExecution(String username, String id) {
		OfficeApprovalRequest value = requests.findById(id)
				.orElseThrow(() -> new ServiceException("COLLAB_REQUEST_NOT_FOUND", "申请不存在"));
		if (!"APPROVED".equals(value.getStatus()) && !"IN_USE".equals(value.getStatus())) {
			throw new ServiceException("COLLAB_REQUEST_NOT_APPROVED", "申请尚未审批通过或已完成");
		}
		value.setStatus("COMPLETED");
		value.setCompletedBy(username);
		value.setCompletedAt(LocalDateTime.now());
		return requests.save(value);
	}

	@Transactional
	public OfficialDocument createDocument(String username, DocumentCommand command) {
		OfficialDocument value = new OfficialDocument();
		value.setDirection(normalizeDirection(command.direction()));
		value.setTitle(required(command.title(), "公文标题不能为空"));
		value.setDocumentNumber(blankToNull(command.documentNumber()));
		value.setUrgency(command.urgency() == null ? "NORMAL" : command.urgency().trim().toUpperCase());
		value.setSummary(blankToNull(command.summary()));
		value.setDrafterUsername(username);
		value.setStatus("INCOMING".equals(value.getDirection()) ? "RECEIVED" : "DRAFT");
		return documents.save(value);
	}

	@Transactional(readOnly = true)
	public List<OfficialDocument> myDocuments(String username) {
		return documents.findByDrafterUsernameOrderByCreateTimeDesc(username);
	}

	@Transactional(readOnly = true)
	public List<OfficialDocument> issuedDocuments() {
		return documents.findByStatusOrderByCreateTimeDesc("ISSUED");
	}

	@Transactional
	public OfficialDocument attachDocument(String username, String id, String fileId) {
		OfficialDocument value = ownedDocument(username, id);
		if (!List.of("DRAFT", "RECEIVED").contains(value.getStatus())) {
			throw new ServiceException("COLLAB_DOCUMENT_LOCKED", "当前公文状态不可修改附件");
		}
		value.setPrimaryFileId(required(fileId, "文件编号不能为空"));
		return documents.save(value);
	}

	@Transactional
	public OfficialDocument submitDocument(String username, String id) {
		OfficialDocument value = ownedDocument(username, id);
		if (!"OUTGOING".equals(value.getDirection()) || !"DRAFT".equals(value.getStatus())) {
			throw new ServiceException("COLLAB_DOCUMENT_NOT_DRAFT", "只有发文草稿可以送审");
		}
		String businessKey = "document:" + value.getId();
		var started = workflow.startByCode(DOCUMENT_FLOW, businessKey, Map.of(
				"documentId", value.getId(),
				"title", value.getTitle(),
				"urgency", value.getUrgency(),
				"summary", value.getSummary() == null ? "" : value.getSummary()), username);
		value.setBusinessKey(businessKey);
		value.setWorkflowInstanceId(started.getId());
		value.setStatus("IN_REVIEW");
		return documents.save(value);
	}

	@Transactional
	public OfficialDocument archiveDocument(String username, String id) {
		OfficialDocument value = ownedDocument(username, id);
		if (!List.of("ISSUED", "RECEIVED").contains(value.getStatus())) {
			throw new ServiceException("COLLAB_DOCUMENT_NOT_ARCHIVABLE", "只有已发布或已收文公文可以归档");
		}
		value.setStatus("ARCHIVED");
		value.setArchivedAt(LocalDateTime.now());
		return documents.save(value);
	}

	@Transactional
	public CollaborationFileShare createShare(String username, ShareCommand command) {
		String type = command.audienceType() == null ? "PRIVATE" : command.audienceType().trim().toUpperCase();
		if (!List.of("PRIVATE", "USER", "DEPARTMENT", "ALL").contains(type)) {
			throw new ServiceException("COLLAB_SHARE_AUDIENCE_INVALID", "共享范围不合法");
		}
		if (List.of("USER", "DEPARTMENT").contains(type)
				&& (command.audienceValue() == null || command.audienceValue().isBlank())) {
			throw new ServiceException("COLLAB_SHARE_TARGET_REQUIRED", "共享对象不能为空");
		}
		CollaborationFileShare value = new CollaborationFileShare();
		value.setOwnerUsername(username);
		value.setTitle(required(command.title(), "文件标题不能为空"));
		value.setDescription(blankToNull(command.description()));
		value.setAudienceType(type);
		value.setAudienceValue(blankToNull(command.audienceValue()));
		return shares.save(value);
	}

	@Transactional
	public CollaborationFileShare attachShare(String username, String id, String fileId) {
		CollaborationFileShare value = ownedShare(username, id);
		value.setFileId(required(fileId, "文件编号不能为空"));
		return shares.save(value);
	}

	@Transactional(readOnly = true)
	public List<CollaborationFileShare> visibleShares(String username, String organizationId) {
		return shares.findByStatusOrderByCreateTimeDesc("ACTIVE").stream()
				.filter(value -> canReadShare(value, username, organizationId))
				.toList();
	}

	public boolean canReadShare(CollaborationFileShare value, String username, String organizationId) {
		if (username.equals(value.getOwnerUsername())) {
			return true;
		}
		return switch (value.getAudienceType()) {
			case "ALL" -> true;
			case "USER" -> List.of(value.getAudienceValue().split(",")).stream()
					.map(String::trim).anyMatch(username::equals);
			case "DEPARTMENT" -> organizationId != null && organizationId.equals(value.getAudienceValue());
			default -> false;
		};
	}

	@Transactional
	public void approveWorkflow(String flowCode, String instanceId, String completedBy) {
		if (List.of(VEHICLE_FLOW, SEAL_FLOW).contains(flowCode)) {
			requests.findByWorkflowInstanceId(instanceId).ifPresent(value -> {
				if ("PENDING".equals(value.getStatus())) {
					if ("VEHICLE".equals(value.getRequestType())
							&& requests.countConflicts(
									value.getResourceId(), value.getStartAt(), value.getEndAt()) > 1) {
						value.setStatus("CONFLICT");
					} else {
						value.setStatus("APPROVED");
						value.setApprovedBy(completedBy);
					}
					requests.save(value);
				}
			});
		}
		if (DOCUMENT_FLOW.equals(flowCode)) {
			documents.findByWorkflowInstanceId(instanceId).ifPresent(value -> {
				if ("IN_REVIEW".equals(value.getStatus())) {
					value.setStatus("ISSUED");
					value.setIssuedAt(LocalDateTime.now());
					documents.save(value);
				}
			});
		}
	}

	@Transactional
	public void rejectWorkflow(String flowCode, String instanceId) {
		if (List.of(VEHICLE_FLOW, SEAL_FLOW).contains(flowCode)) {
			requests.findByWorkflowInstanceId(instanceId).ifPresent(value -> {
				value.setStatus("REJECTED");
				requests.save(value);
			});
		}
		if (DOCUMENT_FLOW.equals(flowCode)) {
			documents.findByWorkflowInstanceId(instanceId).ifPresent(value -> {
				value.setStatus("REJECTED");
				documents.save(value);
			});
		}
	}

	private OfficeApprovalRequest ownedRequest(String username, String id) {
		String employeeId = requireEmployeeId(username);
		return requests.findById(id)
				.filter(value -> employeeId.equals(value.getEmployeeId()))
				.orElseThrow(() -> new ServiceException("COLLAB_REQUEST_NOT_FOUND", "申请不存在"));
	}

	private OfficialDocument ownedDocument(String username, String id) {
		return documents.findById(id)
				.filter(value -> username.equals(value.getDrafterUsername()))
				.orElseThrow(() -> new ServiceException("COLLAB_DOCUMENT_NOT_FOUND", "公文不存在"));
	}

	private CollaborationFileShare ownedShare(String username, String id) {
		return shares.findById(id)
				.filter(value -> username.equals(value.getOwnerUsername()))
				.orElseThrow(() -> new ServiceException("COLLAB_SHARE_NOT_FOUND", "共享记录不存在"));
	}

	private String normalizeResourceType(String value) {
		String normalized = value == null ? "" : value.trim().toUpperCase();
		if (!List.of("VEHICLE", "SEAL").contains(normalized)) {
			throw new ServiceException("COLLAB_RESOURCE_TYPE_INVALID", "资源类型不合法");
		}
		return normalized;
	}

	private String normalizeDirection(String value) {
		String normalized = value == null ? "" : value.trim().toUpperCase();
		if (!List.of("OUTGOING", "INCOMING").contains(normalized)) {
			throw new ServiceException("COLLAB_DOCUMENT_DIRECTION_INVALID", "公文方向不合法");
		}
		return normalized;
	}

	private String required(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new ServiceException("COLLAB_REQUIRED", message);
		}
		return value.trim();
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private String requireEmployeeId(String username) {
		var account = accounts.findByUsername(username);
		if (account == null || account.getEmployeeId() == null
				|| employees.findById(account.getEmployeeId()).isEmpty()) {
			throw new ServiceException("COLLAB_EMPLOYEE_REQUIRED", "当前账号未绑定有效教职工档案");
		}
		return account.getEmployeeId();
	}

	private static final class ServiceException extends IllegalArgumentException {
		private ServiceException(String code, String message) {
			super(message);
		}
	}

	public record OfficeRequestCommand(
			String requestType,
			String resourceId,
			LocalDateTime startAt,
			LocalDateTime endAt,
			String purpose,
			String destination,
			Integer passengerCount,
			String documentTitle,
			Integer copyCount,
			String attachmentFileId) {}

	public record DocumentCommand(
			String direction,
			String title,
			String documentNumber,
			String urgency,
			String summary) {}

	public record ShareCommand(
			String title,
			String description,
			String audienceType,
			String audienceValue) {}

	public record ResourceCommand(
			String resourceType,
			String resourceCode,
			String resourceName,
			String licensePlate,
			Integer capacity,
			String description,
			Boolean enabled) {}
}
