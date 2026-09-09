package com.chronos.message.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.IEmployeeAssignmentRepository;
import com.chronos.Idao.IOrganizationUnitRepository;
import com.chronos.commons.utils.DocToMarkdownUtil;
import com.chronos.message.Idao.IPublicationAttachmentRepository;
import com.chronos.message.Idao.IPublicationAudienceRepository;
import com.chronos.message.Idao.IPublicationReadRepository;
import com.chronos.message.Idao.IPublicationRecipientRepository;
import com.chronos.message.Idao.IPublicationRepository;
import com.chronos.message.Idao.IPublicationDeliveryRepository;
import com.chronos.message.Idao.IPublicationVersionRepository;
import com.chronos.message.model.PublicationDelivery;
import com.chronos.message.model.PublicationRecipient;
import com.chronos.message.model.PublicationVersion;
import com.chronos.message.model.Publication;
import com.chronos.message.model.PublicationAttachment;
import com.chronos.message.model.PublicationAudience;
import com.chronos.message.model.PublicationCommand;
import com.chronos.message.model.PublicationCommand.AudienceCommand;
import com.chronos.message.model.PublicationRead;
import com.chronos.message.model.PublicationStatistics;
import com.chronos.message.model.PublicationView;
import com.chronos.message.model.PublicationView.AttachmentView;
import com.chronos.message.service.iService.IPublicationService;
import com.chronos.message.service.iService.IPublicationService.DownloadFile;
import com.chronos.message.security.PublicationFileScanner;
import com.chronos.message.security.PublicationHtmlSanitizer;
import com.chronos.message.storage.PublicationFileStorage;
import com.chronos.model.pojo.AdminUser;
import com.chronos.model.pojo.EmployeeAssignment;
import com.chronos.model.pojo.OrganizationUnit;
import com.chronos.model.vo.DataScopeContext;
import com.chronos.service.iService.IAuditLogService;
import com.chronos.service.iService.IDataScopeService;
import com.chronos.workflow.WorkflowService;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class PublicationServiceImpl implements IPublicationService {
	private static final Set<String> TYPES = Set.of("NOTICE", "ANNOUNCEMENT");
	private static final Set<String> CONTENT_TYPES = Set.of("RICH_TEXT", "WORD", "PDF", "MIXED");
	private static final Set<String> SUBJECT_TYPES = Set.of(
			"ALL", "ORGANIZATION", "DEPARTMENT", "POSITION", "ROLE", "USER");
	private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx");

	private final IPublicationRepository publications;
	private final IPublicationAudienceRepository audiences;
	private final IPublicationAttachmentRepository attachments;
	private final IPublicationReadRepository reads;
	private final IPublicationRecipientRepository recipients;
	private final IPublicationVersionRepository versions;
	private final IPublicationDeliveryRepository deliveries;
	private final IAdminUserRepository users;
	private final IEmployeeAssignmentRepository assignments;
	private final IOrganizationUnitRepository units;
	private final IDataScopeService dataScopes;
	private final IAuditLogService auditLogs;
	private final WorkflowService workflows;
	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
	private final PublicationFileStorage fileStorage;
	private final List<PublicationFileScanner> fileScanners;
	private final PublicationHtmlSanitizer htmlSanitizer;
	private final int archiveAfterDays;

	public PublicationServiceImpl(
			IPublicationRepository publications,
			IPublicationAudienceRepository audiences,
			IPublicationAttachmentRepository attachments,
			IPublicationReadRepository reads,
			IPublicationRecipientRepository recipients,
			IPublicationVersionRepository versions,
			IPublicationDeliveryRepository deliveries,
			IAdminUserRepository users,
			IEmployeeAssignmentRepository assignments,
			IOrganizationUnitRepository units,
			IDataScopeService dataScopes,
			IAuditLogService auditLogs,
			WorkflowService workflows,
			PublicationFileStorage fileStorage,
			List<PublicationFileScanner> fileScanners,
			PublicationHtmlSanitizer htmlSanitizer,
			@Value("${chronos.message.archive-after-days:365}") int archiveAfterDays) {
		this.publications = publications;
		this.audiences = audiences;
		this.attachments = attachments;
		this.reads = reads;
		this.recipients = recipients;
		this.versions = versions;
		this.deliveries = deliveries;
		this.users = users;
		this.assignments = assignments;
		this.units = units;
		this.dataScopes = dataScopes;
		this.auditLogs = auditLogs;
		this.workflows = workflows;
		this.fileStorage = fileStorage;
		this.fileScanners = fileScanners;
		this.htmlSanitizer = htmlSanitizer;
		this.archiveAfterDays = Math.max(archiveAfterDays, 1);
	}

	@Transactional(readOnly = true)
	public Page<PublicationView> adminList(
			String type,
			String status,
			String keyword,
			String username,
			Pageable pageable) {
		DataScopeContext scope = dataScopes.resolve(username);
		return publications.findAll((root, query, cb) -> {
			List<jakarta.persistence.criteria.Predicate> values = new ArrayList<>();
			if (hasText(type)) {
				values.add(cb.equal(root.get("publicationType"), normalize(type)));
			}
			if (hasText(status)) {
				values.add(cb.equal(root.get("status"), normalize(status)));
			}
			if (hasText(keyword)) {
				String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
				values.add(cb.or(
						cb.like(cb.lower(root.get("title")), pattern),
						cb.like(cb.lower(root.get("summary")), pattern)));
			}
			if (!scope.fullAccess()) {
				var own = cb.equal(root.get("createBy"), username);
				if (scope.organizationIds().isEmpty()) {
					values.add(own);
				} else {
					values.add(cb.or(own, root.get("ownerOrganizationId").in(scope.organizationIds())));
				}
			}
			return cb.and(values.toArray(jakarta.persistence.criteria.Predicate[]::new));
		}, pageable).map(value -> view(value, null, true));
	}

	@Transactional(readOnly = true)
	public PublicationView adminDetail(String id, String username) {
		Publication value = required(id);
		assertManage(username, value);
		return view(value, null, true);
	}

	@Transactional
	public PublicationView create(PublicationCommand command, String username) {
		Publication value = new Publication();
		apply(value, command);
		AdminUser creator = requiredUser(username);
		value.setOwnerOrganizationId(creator.getOrganizationId());
		value.setStatus("DRAFT");
		value = publications.save(value);
		replaceAudiences(value.getId(), command.audiences());
		snapshot(value, "CREATE");
		audit(username, "MESSAGE_PUBLICATION_CREATE", value.getId());
		return view(value, null, true);
	}

	@Transactional
	public PublicationView update(String id, PublicationCommand command, String username) {
		Publication value = required(id);
		assertManage(username, value);
		if (!"DRAFT".equals(value.getStatus()) && !"SCHEDULED".equals(value.getStatus())) {
			throw new IllegalStateException("只有草稿或待发布内容可以编辑");
		}
		apply(value, command);
		value.setVersionNo(value.getVersionNo() + 1);
		replaceAudiences(id, command.audiences());
		value = publications.save(value);
		snapshot(value, "UPDATE");
		audit(username, "MESSAGE_PUBLICATION_UPDATE", id);
		return view(value, null, true);
	}

	@Transactional
	public PublicationView publish(String id, String username) {
		Publication value = required(id);
		assertManage(username, value);
		if (Boolean.TRUE.equals(value.getApprovalRequired())
				&& !"APPROVED".equals(value.getApprovalStatus())) {
			throw new IllegalStateException("该内容必须审核通过后才能发布");
		}
		if (!List.of("DRAFT", "SCHEDULED").contains(value.getStatus())) {
			throw new IllegalStateException("当前状态不允许发布");
		}
		if (audiences.findByPublicationId(id).stream().noneMatch(item -> !Boolean.TRUE.equals(item.getExcluded()))) {
			throw new IllegalStateException("发布前至少配置一个正向受众范围");
		}
		LocalDateTime now = LocalDateTime.now();
		if (value.getExpireAt() != null && !value.getExpireAt().isAfter(now)) {
			throw new IllegalStateException("有效期结束时间必须晚于当前时间");
		}
		if (value.getPublishAt() != null && value.getPublishAt().isAfter(now)) {
			value.setStatus("SCHEDULED");
		} else {
			value.setStatus("PUBLISHED");
			value.setPublishedAt(now);
		}
		value = publications.save(value);
		if ("PUBLISHED".equals(value.getStatus())) {
			materializeRecipients(value);
		}
		snapshot(value, "PUBLISH");
		audit(username, "MESSAGE_PUBLICATION_PUBLISH", id);
		return view(value, null, true);
	}

	@Transactional
	public PublicationView submit(String id, String username) {
		Publication value = required(id);
		assertManage(username, value);
		if (!"DRAFT".equals(value.getStatus()) || !Boolean.TRUE.equals(value.getApprovalRequired())) {
			throw new IllegalStateException("只有需要审批的草稿可以提交审核");
		}
		value.setStatus("PENDING_REVIEW");
		value.setApprovalStatus("PENDING");
		value.setSubmittedAt(LocalDateTime.now());
		value.setSubmittedBy(username);
		if (hasText(value.getApprovalWorkflowDefinitionId())) {
			try {
				String variables = objectMapper.writeValueAsString(java.util.Map.of(
						"publicationId", value.getId(),
						"publicationTitle", value.getTitle(),
						"publicationType", value.getPublicationType()));
				var instance = workflows.start(
						value.getApprovalWorkflowDefinitionId(),
						"PUBLICATION:" + value.getId(),
						variables,
						java.util.Map.of(),
						username);
				value.setApprovalInstanceId(instance.getId());
			} catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
				throw new IllegalStateException("审批流程变量序列化失败", exception);
			}
		}
		value = publications.save(value);
		snapshot(value, "SUBMIT");
		audit(username, "MESSAGE_PUBLICATION_SUBMIT", id);
		return view(value, null, true);
	}

	@Transactional
	public PublicationView approve(String id, String comment, String username) {
		Publication value = required(id);
		if (!"PENDING_REVIEW".equals(value.getStatus())) {
			throw new IllegalStateException("当前内容不在待审核状态");
		}
		if (hasText(value.getApprovalInstanceId())) {
			throw new IllegalStateException("该内容已进入流程审批，请在流程中心处理");
		}
		value.setStatus("DRAFT");
		value.setApprovalStatus("APPROVED");
		value.setReviewedAt(LocalDateTime.now());
		value.setReviewedBy(username);
		value.setReviewComment(comment);
		value = publications.save(value);
		snapshot(value, "APPROVE");
		audit(username, "MESSAGE_PUBLICATION_APPROVE", id);
		return view(value, null, true);
	}

	@Transactional
	public PublicationView reject(String id, String comment, String username) {
		Publication value = required(id);
		if (!"PENDING_REVIEW".equals(value.getStatus())) {
			throw new IllegalStateException("当前内容不在待审核状态");
		}
		if (hasText(value.getApprovalInstanceId())) {
			throw new IllegalStateException("该内容已进入流程审批，请在流程中心处理");
		}
		value.setStatus("DRAFT");
		value.setApprovalStatus("REJECTED");
		value.setReviewedAt(LocalDateTime.now());
		value.setReviewedBy(username);
		value.setReviewComment(comment);
		value = publications.save(value);
		snapshot(value, "REJECT");
		audit(username, "MESSAGE_PUBLICATION_REJECT", id);
		return view(value, null, true);
	}

	@Transactional
	public PublicationView withdraw(String id, String username) {
		Publication value = required(id);
		assertManage(username, value);
		if (!List.of("PUBLISHED", "SCHEDULED").contains(value.getStatus())) {
			throw new IllegalStateException("只有已发布或待发布内容可以撤下");
		}
		value.setStatus("WITHDRAWN");
		value.setWithdrawnAt(LocalDateTime.now());
		value.setWithdrawnBy(username);
		value = publications.save(value);
		snapshot(value, "WITHDRAW");
		audit(username, "MESSAGE_PUBLICATION_WITHDRAW", id);
		return view(value, null, true);
	}

	@Transactional
	public void delete(String id, String username) {
		Publication value = required(id);
		assertManage(username, value);
		if (!"DRAFT".equals(value.getStatus()) && !"WITHDRAWN".equals(value.getStatus())) {
			throw new IllegalStateException("仅草稿或已撤下内容允许删除");
		}
		attachments.findByPublicationIdOrderByCreateTimeAsc(id).forEach(this::deleteStoredFile);
		attachments.deleteAll(attachments.findByPublicationIdOrderByCreateTimeAsc(id));
		audiences.deleteByPublicationId(id);
		reads.deleteByPublicationId(id);
		recipients.deleteByPublicationId(id);
		deliveries.deleteByPublicationId(id);
		publications.delete(value);
		audit(username, "MESSAGE_PUBLICATION_DELETE", id);
	}

	@Transactional
	public Page<PublicationView> visible(
			String username,
			String type,
			String keyword,
			boolean unreadOnly,
			Pageable pageable) {
		LocalDateTime now = LocalDateTime.now();
		refreshDynamicMembership(username, now);
		String normalizedType = hasText(type) ? normalize(type) : null;
		String normalizedKeyword = hasText(keyword) ? keyword.trim() : null;
		Page<Publication> result = normalizedKeyword == null
				? publications.findVisibleForUserWithoutKeyword(
						username,
						normalizedType,
						unreadOnly,
						now,
						pageable)
				: publications.findVisibleForUser(
						username,
						normalizedType,
						normalizedKeyword,
						unreadOnly,
						now,
						pageable);
		return result
				.map(value -> view(value, username, false));
	}

	private void refreshDynamicMembership(String username, LocalDateTime now) {
		AdminUser user = requiredUser(username);
		publications.findByStatusIn(List.of("PUBLISHED")).stream()
				.filter(value -> "DYNAMIC".equals(value.getAudienceMode()))
				.filter(value -> value.getPublishedAt() == null || !value.getPublishedAt().isAfter(now))
				.filter(value -> value.getExpireAt() == null || value.getExpireAt().isAfter(now))
				.forEach(value -> {
					var existing = recipients.findByPublicationIdAndUsername(value.getId(), username);
					boolean matches = audienceMatches(audiences.findByPublicationId(value.getId()), user);
					if (matches && existing.isEmpty()) {
						PublicationRecipient recipient = new PublicationRecipient();
						recipient.setPublicationId(value.getId());
						recipient.setUsername(username);
						recipients.save(recipient);
					} else if (!matches && existing.isPresent()) {
						recipients.delete(existing.get());
					}
				});
	}

	@Transactional
	public PublicationView portalDetail(String id, String username) {
		Publication value = required(id);
		assertPortalAccess(value, username);
		if (!Boolean.TRUE.equals(value.getMustRead())) {
			markRead(id, username);
		}
		return view(value, username, false);
	}

	@Transactional
	public void markRead(String id, String username) {
		Publication value = required(id);
		assertPortalAccess(value, username);
		reads.findByPublicationIdAndUsername(id, username).orElseGet(() -> {
			PublicationRead receipt = new PublicationRead();
			receipt.setPublicationId(id);
			receipt.setUsername(username);
			receipt.setReadAt(LocalDateTime.now());
			return reads.save(receipt);
		});
	}

	@Transactional
	public PublicationView attach(
			String publicationId,
			MultipartFile file,
			boolean primaryContent,
			String username) {
		Publication publication = required(publicationId);
		assertManage(username, publication);
		if (!"DRAFT".equals(publication.getStatus()) && !"SCHEDULED".equals(publication.getStatus())) {
			throw new IllegalStateException("已发布内容不能追加附件");
		}
		String originalName = safeName(file.getOriginalFilename());
		String extension = extension(originalName);
		if (!ALLOWED_EXTENSIONS.contains(extension)) {
			throw new IllegalArgumentException("仅支持 doc、docx、pdf 文件");
		}
		if (file.isEmpty() || file.getSize() > 20L * 1024 * 1024) {
			throw new IllegalArgumentException("文件不能为空且不能超过 20MB");
		}
		try {
			byte[] bytes = file.getBytes();
			validateMagic(bytes, extension);
			String storageKey = publicationId + "/" + UUID.randomUUID() + "." + extension;
			String contentType = inferContentType(extension, file.getContentType());
			fileScanners.forEach(scanner -> scanner.scan(bytes, originalName, contentType));
			fileStorage.store(storageKey, bytes, contentType);

			PublicationAttachment attachment = new PublicationAttachment();
			attachment.setPublicationId(publicationId);
			attachment.setOriginalName(originalName);
			attachment.setContentType(contentType);
			attachment.setStorageKey(storageKey);
			attachment.setFileSize(file.getSize());
			attachment.setSha256(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
			attachment.setPrimaryContent(primaryContent);
			attachments.save(attachment);

			if (primaryContent) {
				attachments.findByPublicationIdOrderByCreateTimeAsc(publicationId).stream()
						.filter(item -> Boolean.TRUE.equals(item.getPrimaryContent()))
						.forEach(item -> {
							item.setPrimaryContent(false);
							attachments.save(item);
						});
				String extracted = extractText(bytes, originalName);
				publication.setContent(htmlSanitizer.textAsHtml(extracted));
				publication.setContentType("pdf".equals(extension) ? "PDF" : "WORD");
				publications.save(publication);
			}
			snapshot(publication, "ATTACH");
			audit(username, "MESSAGE_PUBLICATION_ATTACH", publicationId);
			return view(publication, null, true);
		} catch (Exception exception) {
			throw new IllegalStateException("文件保存或内容解析失败", exception);
		}
	}

	@Transactional
	public void deleteAttachment(String attachmentId, String username) {
		PublicationAttachment attachment = attachments.findById(attachmentId)
				.orElseThrow(() -> new IllegalArgumentException("附件不存在"));
		Publication publication = required(attachment.getPublicationId());
		assertManage(username, publication);
		if (!"DRAFT".equals(publication.getStatus())) {
			throw new IllegalStateException("只有草稿允许删除附件");
		}
		deleteStoredFile(attachment);
		attachments.delete(attachment);
		snapshot(publication, "DELETE_ATTACHMENT");
		audit(username, "MESSAGE_PUBLICATION_DELETE_ATTACHMENT", publication.getId());
	}

	@Transactional(readOnly = true)
	public PublicationStatistics statistics(String id, String username) {
		Publication value = required(id);
		assertManage(username, value);
		Set<String> readUsers = reads.findByPublicationId(id).stream()
				.map(PublicationRead::getUsername)
				.collect(java.util.stream.Collectors.toSet());
		List<String> recipientUsers;
		if ("SNAPSHOT".equals(value.getAudienceMode())) {
			recipientUsers = recipients.findByPublicationId(id).stream()
					.map(PublicationRecipient::getUsername)
					.toList();
		} else {
			List<PublicationAudience> configured = audiences.findByPublicationId(id);
			recipientUsers = users.findAll().stream()
					.filter(user -> Integer.valueOf(1).equals(user.getStatus()))
					.filter(user -> audienceMatches(configured, user))
					.map(AdminUser::getUsername)
					.toList();
		}
		List<String> unread = recipientUsers.stream()
				.filter(recipient -> !readUsers.contains(recipient))
				.sorted()
				.toList();
		long recipientCount = recipientUsers.size();
		long readCount = recipientCount - unread.size();
		double rate = recipientCount == 0 ? 0D : (readCount * 100D / recipientCount);
		return new PublicationStatistics(
				id,
				recipientCount,
				readCount,
				unread.size(),
				Math.round(rate * 100D) / 100D,
				value.getReadDeadline(),
				value.getReadDeadline() != null && value.getReadDeadline().isBefore(LocalDateTime.now()),
				unread);
	}

	@Transactional(readOnly = true)
	public List<PublicationVersion> versions(String id, String username) {
		Publication value = required(id);
		assertManage(username, value);
		return versions.findByPublicationIdOrderByVersionNoDesc(id);
	}

	@Transactional
	public PublicationView restoreVersion(String id, Integer versionNo, String username) {
		Publication value = required(id);
		assertManage(username, value);
		if (!List.of("DRAFT", "WITHDRAWN").contains(value.getStatus())) {
			throw new IllegalStateException("只有草稿或已撤下内容可以恢复历史版本");
		}
		PublicationVersion version = versions.findByPublicationIdAndVersionNo(id, versionNo)
				.orElseThrow(() -> new IllegalArgumentException("历史版本不存在"));
		try {
			var root = objectMapper.readTree(version.getSnapshotJson());
			var source = root.path("publication");
			value.setPublicationType(source.path("publicationType").asText(value.getPublicationType()));
			value.setContentType(source.path("contentType").asText(value.getContentType()));
			value.setTitle(source.path("title").asText(value.getTitle()));
			value.setSummary(nullableText(source, "summary"));
			value.setContent(htmlSanitizer.sanitize(nullableText(source, "content")));
			value.setImportance(source.path("importance").asText("NORMAL"));
			value.setPinned(source.path("pinned").asBoolean(false));
			value.setSortOrder(source.path("sortOrder").asInt(0));
			value.setMustRead(source.path("mustRead").asBoolean(false));
			value.setAudienceMode(source.path("audienceMode").asText("SNAPSHOT"));
			value.setReadDeadline(localDateTime(source, "readDeadline"));
			value.setPublishAt(localDateTime(source, "publishAt"));
			value.setExpireAt(localDateTime(source, "expireAt"));
			value.setApprovalRequired(source.path("approvalRequired").asBoolean(false));
			value.setApprovalWorkflowDefinitionId(nullableText(source, "approvalWorkflowDefinitionId"));

			List<AudienceCommand> restoredAudiences = new ArrayList<>();
			root.path("audiences").forEach(item -> restoredAudiences.add(new AudienceCommand(
					item.path("subjectType").asText(),
					item.path("subjectId").asText(),
					item.path("includeChildren").asBoolean(false),
					item.path("excluded").asBoolean(false))));
			replaceAudiences(id, restoredAudiences);

			// 恢复操作产生一个新的可审计草稿，不回退历史主键、审计字段和流程实例。
			value.setStatus("DRAFT");
			value.setApprovalStatus(null);
			value.setApprovalInstanceId(null);
			value.setPublishedAt(null);
			value.setWithdrawnAt(null);
			value.setWithdrawnBy(null);
			value.setVersionNo(value.getVersionNo() + 1);
			value = publications.save(value);
			snapshot(value, "RESTORE");
			audit(username, "MESSAGE_PUBLICATION_RESTORE_VERSION", id);
			return view(value, null, true);
		} catch (IllegalArgumentException | IllegalStateException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new IllegalStateException("历史版本恢复失败", exception);
		}
	}

	@Transactional
	public PublicationView archive(String id, boolean archived, String username) {
		Publication value = required(id);
		assertManage(username, value);
		if (archived && !List.of("WITHDRAWN", "EXPIRED").contains(value.getStatus())) {
			throw new IllegalStateException("只有已撤下或已过期内容可以归档");
		}
		value.setArchived(archived);
		value.setArchivedAt(archived ? LocalDateTime.now() : null);
		value.setArchivedBy(archived ? username : null);
		value = publications.save(value);
		snapshot(value, archived ? "ARCHIVE" : "UNARCHIVE");
		audit(username, archived ? "MESSAGE_PUBLICATION_ARCHIVE" : "MESSAGE_PUBLICATION_UNARCHIVE", id);
		return view(value, null, true);
	}

	private String nullableText(com.fasterxml.jackson.databind.JsonNode node, String field) {
		var value = node.get(field);
		return value == null || value.isNull() ? null : value.asText();
	}

	private LocalDateTime localDateTime(com.fasterxml.jackson.databind.JsonNode node, String field) {
		String value = nullableText(node, field);
		return value == null || value.isBlank() ? null : LocalDateTime.parse(value);
	}

	@Transactional
	public long remindUnread(String id, String username) {
		PublicationStatistics statistics = statistics(id, username);
		LocalDateTime now = LocalDateTime.now();
		statistics.unreadUsers().forEach(recipient -> {
			PublicationDelivery delivery = deliveries
					.findByPublicationIdAndUsernameAndChannel(id, recipient, "IN_APP")
					.orElseGet(() -> {
						PublicationDelivery created = new PublicationDelivery();
						created.setPublicationId(id);
						created.setUsername(recipient);
						created.setChannel("IN_APP");
						return created;
					});
			delivery.setStatus("PENDING");
			delivery.setAttemptCount(0);
			delivery.setNextAttemptAt(now);
			delivery.setDeliveredAt(null);
			delivery.setLastError(null);
			deliveries.save(delivery);
		});
		audit(username, "MESSAGE_PUBLICATION_REMIND_UNREAD", id);
		return statistics.unreadCount();
	}

	@Transactional(readOnly = true)
	public DownloadFile download(String attachmentId, String username, boolean administrator) {
		PublicationAttachment attachment = attachments.findById(attachmentId)
				.orElseThrow(() -> new IllegalArgumentException("附件不存在"));
		Publication publication = required(attachment.getPublicationId());
		if (administrator) {
			assertManage(username, publication);
		} else {
			assertPortalAccess(publication, username);
		}
		return new DownloadFile(
				attachment.getOriginalName(),
				attachment.getContentType(),
				fileStorage.read(attachment.getStorageKey()));
	}

	@Transactional
	public void advanceLifecycle() {
		LocalDateTime now = LocalDateTime.now();
		reconcileWorkflowApprovals();
		publications.findLifecycleCandidates(List.of("SCHEDULED", "PUBLISHED")).forEach(value -> {
			if ("SCHEDULED".equals(value.getStatus())
					&& (value.getPublishAt() == null || !value.getPublishAt().isAfter(now))) {
				value.setStatus("PUBLISHED");
				value.setPublishedAt(now);
				materializeRecipients(value);
			}
			if ("PUBLISHED".equals(value.getStatus())
					&& value.getExpireAt() != null
					&& !value.getExpireAt().isAfter(now)) {
				value.setStatus("EXPIRED");
			}
			if ("PUBLISHED".equals(value.getStatus())
					&& "SNAPSHOT".equals(value.getAudienceMode())
					&& recipients.countByPublicationId(value.getId()) == 0) {
				// 兼容生产增强前已发布的数据，首次扫描时补齐收件人快照。
				materializeRecipients(value);
			}
			publications.save(value);
		});
		// 已过期内容按保留策略自动归档；数据仍保留，管理员可手工解除归档。
		publications.findByStatusAndArchivedFalseAndExpireAtBefore(
				"EXPIRED",
				now.minusDays(archiveAfterDays)).forEach(value -> {
			value.setArchived(true);
			value.setArchivedAt(now);
			value.setArchivedBy("SYSTEM");
			publications.save(value);
		});
	}

	private void reconcileWorkflowApprovals() {
		publications.findByApprovalStatusAndApprovalInstanceIdIsNotNull("PENDING").forEach(value -> {
			var instance = workflows.instance(value.getApprovalInstanceId());
			if ("COMPLETED".equals(instance.getStatus())) {
				value.setStatus("DRAFT");
				value.setApprovalStatus("APPROVED");
				value.setReviewedAt(LocalDateTime.now());
				value.setReviewedBy("WORKFLOW");
				value.setReviewComment("审批流程已通过");
				publications.save(value);
				snapshot(value, "WORKFLOW_APPROVE");
			} else if (Set.of("REJECTED", "TERMINATED", "WITHDRAWN").contains(instance.getStatus())) {
				value.setStatus("DRAFT");
				value.setApprovalStatus("REJECTED");
				value.setReviewedAt(LocalDateTime.now());
				value.setReviewedBy("WORKFLOW");
				value.setReviewComment("审批流程未通过: " + instance.getStatus());
				publications.save(value);
				snapshot(value, "WORKFLOW_REJECT");
			}
		});
	}

	private boolean canRead(String username, String publicationId) {
		Publication publication = required(publicationId);
		if ("SNAPSHOT".equals(publication.getAudienceMode())) {
			return recipients.existsByPublicationIdAndUsername(publicationId, username);
		}
		AdminUser user = users.findByUsername(username);
		if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
			return false;
		}
		List<PublicationAudience> configured = audiences.findByPublicationId(publicationId);
		boolean included = configured.stream()
				.filter(item -> !Boolean.TRUE.equals(item.getExcluded()))
				.anyMatch(item -> matches(item, user));
		boolean excluded = configured.stream()
				.filter(item -> Boolean.TRUE.equals(item.getExcluded()))
				.anyMatch(item -> matches(item, user));
		return included && !excluded;
	}

	private boolean matches(PublicationAudience audience, AdminUser user) {
		String type = audience.getSubjectType();
		String subjectId = audience.getSubjectId();
		if ("ALL".equals(type)) {
			return true;
		}
		if ("USER".equals(type)) {
			return subjectId.equals(user.getUsername()) || subjectId.equals(user.getId());
		}
		if ("ORGANIZATION".equals(type)) {
			return subjectId.equals(user.getOrganizationId());
		}
		if ("ROLE".equals(type)) {
			return user.getRoles().stream()
					.anyMatch(role -> subjectId.equals(role.getId()) || subjectId.equals(role.getRoleCode()));
		}
		if (user.getEmployeeId() == null) {
			return false;
		}
		List<EmployeeAssignment> current = assignments.findCurrentAssignments(user.getEmployeeId(), LocalDate.now());
		if ("POSITION".equals(type)) {
			return current.stream().anyMatch(item -> subjectId.equals(item.getPositionId()));
		}
		if ("DEPARTMENT".equals(type)) {
			return current.stream().anyMatch(item -> departmentMatches(
					item.getOrganizationUnitId(), subjectId, Boolean.TRUE.equals(audience.getIncludeChildren())));
		}
		return false;
	}

	private boolean departmentMatches(String userUnitId, String targetUnitId, boolean includeChildren) {
		if (targetUnitId.equals(userUnitId)) {
			return true;
		}
		if (!includeChildren) {
			return false;
		}
		OrganizationUnit unit = units.findById(userUnitId).orElse(null);
		return unit != null
				&& unit.getTreePath() != null
				&& (unit.getTreePath().contains("/" + targetUnitId + "/")
						|| unit.getTreePath().endsWith("/" + targetUnitId));
	}

	private void apply(Publication value, PublicationCommand command) {
		String type = normalize(command.publicationType());
		String contentType = normalize(command.contentType());
		if (!TYPES.contains(type)) {
			throw new IllegalArgumentException("业务类型必须为 NOTICE 或 ANNOUNCEMENT");
		}
		if (!CONTENT_TYPES.contains(contentType)) {
			throw new IllegalArgumentException("不支持的内容类型");
		}
		if (!hasText(command.title())) {
			throw new IllegalArgumentException("标题不能为空");
		}
		if (command.expireAt() != null
				&& command.publishAt() != null
				&& !command.expireAt().isAfter(command.publishAt())) {
			throw new IllegalArgumentException("有效期结束时间必须晚于发布时间");
		}
		value.setPublicationType(type);
		value.setContentType(contentType);
		value.setTitle(command.title().trim());
		value.setSummary(command.summary());
		// 正文统一以清洗后的 HTML 保存；即使客户端绕过编辑器也不能写入危险标签。
		value.setContent(htmlSanitizer.sanitize(command.content()));
		value.setImportance(normalizeOr(command.importance(), "NORMAL"));
		value.setPinned(Boolean.TRUE.equals(command.pinned()));
		value.setSortOrder(command.sortOrder() == null ? 0 : command.sortOrder());
		value.setMustRead(Boolean.TRUE.equals(command.mustRead()));
		value.setAudienceMode(normalizeOr(command.audienceMode(), "SNAPSHOT"));
		if (!Set.of("SNAPSHOT", "DYNAMIC").contains(value.getAudienceMode())) {
			throw new IllegalArgumentException("受众模式必须为 SNAPSHOT 或 DYNAMIC");
		}
		value.setReadDeadline(command.readDeadline());
		value.setApprovalRequired(Boolean.TRUE.equals(command.approvalRequired()));
		value.setApprovalWorkflowDefinitionId(command.approvalWorkflowDefinitionId());
		if (!Boolean.TRUE.equals(value.getApprovalRequired())) {
			value.setApprovalStatus(null);
		}
		value.setPublishAt(command.publishAt());
		value.setExpireAt(command.expireAt());
	}

	private void replaceAudiences(String publicationId, List<AudienceCommand> commands) {
		audiences.deleteByPublicationId(publicationId);
		if (commands == null) {
			return;
		}
		List<PublicationAudience> values = commands.stream().map(command -> {
			String type = normalize(command.subjectType());
			if (!SUBJECT_TYPES.contains(type)) {
				throw new IllegalArgumentException("不支持的受众类型: " + type);
			}
			PublicationAudience value = new PublicationAudience();
			value.setPublicationId(publicationId);
			value.setSubjectType(type);
			value.setSubjectId("ALL".equals(type) ? "*" : command.subjectId());
			if (!hasText(value.getSubjectId())) {
				throw new IllegalArgumentException("受众标识不能为空");
			}
			value.setIncludeChildren(Boolean.TRUE.equals(command.includeChildren()));
			value.setExcluded(Boolean.TRUE.equals(command.excluded()));
			return value;
		}).toList();
		audiences.saveAll(values);
	}

	private PublicationView view(Publication value, String username, boolean administrator) {
		List<AudienceCommand> audienceViews = administrator
				? audiences.findByPublicationId(value.getId()).stream()
						.map(item -> new AudienceCommand(
								item.getSubjectType(),
								item.getSubjectId(),
								item.getIncludeChildren(),
								item.getExcluded()))
						.toList()
				: List.of();
		List<AttachmentView> attachmentViews = attachments.findByPublicationIdOrderByCreateTimeAsc(value.getId())
				.stream()
				.map(item -> new AttachmentView(
						item.getId(),
						item.getOriginalName(),
						item.getContentType(),
						item.getFileSize(),
						item.getSha256(),
						item.getPrimaryContent()))
				.toList();
		boolean read = username != null && reads.findByPublicationIdAndUsername(value.getId(), username).isPresent();
		return new PublicationView(
				value.getId(),
				value.getPublicationType(),
				value.getContentType(),
				value.getTitle(),
				value.getSummary(),
				value.getContent(),
				value.getStatus(),
				value.getImportance(),
				value.getPinned(),
				value.getSortOrder(),
				value.getMustRead(),
				value.getAudienceMode(),
				value.getReadDeadline(),
				value.getApprovalRequired(),
				value.getApprovalStatus(),
				value.getApprovalWorkflowDefinitionId(),
				value.getApprovalInstanceId(),
				value.getReviewComment(),
				value.getPublishAt(),
				value.getExpireAt(),
				value.getPublishedAt(),
				value.getArchived(),
				value.getArchivedAt(),
				value.getArchivedBy(),
				value.getVersionNo(),
				value.getCreateBy(),
				value.getCreateTime(),
				read,
				administrator ? reads.countByPublicationId(value.getId()) : 0,
				administrator ? recipientCount(value) : 0,
				audienceViews,
				attachmentViews);
	}

	private void assertPortalAccess(Publication value, String username) {
		LocalDateTime now = LocalDateTime.now();
		boolean active = "PUBLISHED".equals(value.getStatus())
				&& (value.getPublishedAt() == null || !value.getPublishedAt().isAfter(now))
				&& (value.getExpireAt() == null || value.getExpireAt().isAfter(now));
		if (!active || !canRead(username, value.getId())) {
			throw new AccessDeniedException("无权访问该内容");
		}
	}

	private void assertManage(String username, Publication value) {
		DataScopeContext scope = dataScopes.resolve(username);
		boolean allowed = scope.fullAccess()
				|| username.equals(value.getCreateBy())
				|| (value.getOwnerOrganizationId() != null
						&& scope.organizationIds().contains(value.getOwnerOrganizationId()));
		if (!allowed) {
			throw new AccessDeniedException("无权管理该通知公告");
		}
	}

	private AdminUser requiredUser(String username) {
		AdminUser user = users.findByUsername(username);
		if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
			throw new IllegalArgumentException("当前用户不存在或已停用");
		}
		return user;
	}

	private void materializeRecipients(Publication publication) {
		recipients.deleteByPublicationId(publication.getId());
		List<PublicationAudience> configured = audiences.findByPublicationId(publication.getId());
		List<PublicationRecipient> values = users.findAll().stream()
				.filter(user -> Integer.valueOf(1).equals(user.getStatus()))
				.filter(user -> audienceMatches(configured, user))
				.map(user -> {
					PublicationRecipient recipient = new PublicationRecipient();
					recipient.setPublicationId(publication.getId());
					recipient.setUsername(user.getUsername());
					return recipient;
				})
				.toList();
		recipients.saveAll(values);
		List<PublicationDelivery> deliveryValues = values.stream().map(recipient -> {
			PublicationDelivery delivery = new PublicationDelivery();
			delivery.setPublicationId(publication.getId());
			delivery.setUsername(recipient.getUsername());
			delivery.setChannel("IN_APP");
			delivery.setStatus("PENDING");
			delivery.setNextAttemptAt(LocalDateTime.now());
			return delivery;
		}).toList();
		deliveries.saveAll(deliveryValues);
	}

	private boolean audienceMatches(List<PublicationAudience> configured, AdminUser user) {
		boolean included = configured.stream()
				.filter(item -> !Boolean.TRUE.equals(item.getExcluded()))
				.anyMatch(item -> matches(item, user));
		boolean excluded = configured.stream()
				.filter(item -> Boolean.TRUE.equals(item.getExcluded()))
				.anyMatch(item -> matches(item, user));
		return included && !excluded;
	}

	private long recipientCount(Publication value) {
		if ("SNAPSHOT".equals(value.getAudienceMode())) {
			return recipients.countByPublicationId(value.getId());
		}
		List<PublicationAudience> configured = audiences.findByPublicationId(value.getId());
		return users.findAll().stream()
				.filter(user -> Integer.valueOf(1).equals(user.getStatus()))
				.filter(user -> audienceMatches(configured, user))
				.count();
	}

	private void snapshot(Publication value, String operation) {
		try {
			PublicationVersion version = new PublicationVersion();
			version.setPublicationId(value.getId());
			version.setVersionNo(Math.toIntExact(versions.countByPublicationId(value.getId()) + 1));
			version.setOperation(operation);
			version.setSnapshotJson(objectMapper.writeValueAsString(java.util.Map.of(
					"publication", value,
					"audiences", audiences.findByPublicationId(value.getId()),
					"attachments", attachments.findByPublicationIdOrderByCreateTimeAsc(value.getId()))));
			versions.save(version);
		} catch (Exception exception) {
			throw new IllegalStateException("保存通知公告版本快照失败", exception);
		}
	}

	private void audit(String username, String action, String publicationId) {
		auditLogs.log(username, action, "publicationId=" + publicationId);
	}

	private void validateMagic(byte[] bytes, String extension) {
		boolean valid = switch (extension) {
			case "pdf" -> bytes.length >= 5
					&& bytes[0] == '%'
					&& bytes[1] == 'P'
					&& bytes[2] == 'D'
					&& bytes[3] == 'F'
					&& bytes[4] == '-';
			case "docx" -> bytes.length >= 4
					&& bytes[0] == 'P'
					&& bytes[1] == 'K';
			case "doc" -> bytes.length >= 8
					&& (bytes[0] & 0xff) == 0xD0
					&& (bytes[1] & 0xff) == 0xCF
					&& (bytes[2] & 0xff) == 0x11
					&& (bytes[3] & 0xff) == 0xE0;
			default -> false;
		};
		if (!valid) {
			throw new IllegalArgumentException("文件实际格式与扩展名不一致");
		}
	}

	private String extractText(byte[] bytes, String filename) throws IOException {
		if (filename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
			// PDF 保留原始版式，通过鉴权附件接口预览或下载；不把二进制内容误存入正文。
			return "";
		}
		try (InputStream input = new java.io.ByteArrayInputStream(bytes)) {
			return DocToMarkdownUtil.convert(input, filename);
		}
	}

	private Publication required(String id) {
		return publications.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("通知公告不存在"));
	}

	private void deleteStoredFile(PublicationAttachment attachment) {
		try {
			fileStorage.delete(attachment.getStorageKey());
		} catch (RuntimeException ignored) {
			// 数据删除不因外部存储清理失败而回滚，生产环境可由文件巡检任务再次清理。
		}
	}

	private String safeName(String value) {
		String filename = value == null ? "document" : Path.of(value).getFileName().toString();
		return filename.replaceAll("[\\r\\n]", "_");
	}

	private String extension(String value) {
		int index = value.lastIndexOf('.');
		return index < 0 ? "" : value.substring(index + 1).toLowerCase(Locale.ROOT);
	}

	private String inferContentType(String extension, String provided) {
		if (hasText(provided) && !MediaType.APPLICATION_OCTET_STREAM_VALUE.equals(provided)) {
			return provided;
		}
		return switch (extension) {
			case "pdf" -> MediaType.APPLICATION_PDF_VALUE;
			case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
			case "doc" -> "application/msword";
			default -> MediaType.APPLICATION_OCTET_STREAM_VALUE;
		};
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
	}

	private String normalizeOr(String value, String fallback) {
		return hasText(value) ? normalize(value) : fallback;
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

}
