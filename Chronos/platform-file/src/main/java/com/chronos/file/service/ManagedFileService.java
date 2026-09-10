package com.chronos.file.service;

import com.chronos.file.dao.ManagedFileRepository;
import com.chronos.file.model.ManagedFile;
import com.chronos.file.model.ManagedFileView;
import com.chronos.file.storage.ManagedFileStorage;
import com.chronos.service.iService.IAuditLogService;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ManagedFileService {
	private static final long MAX_SIZE = 20L * 1024 * 1024;
	private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
			"pdf", "doc", "docx", "xls", "xlsx", "txt", "md", "png", "jpg", "jpeg");

	private final ManagedFileRepository files;
	private final ManagedFileStorage storage;
	private final IAuditLogService audit;
	private final List<ManagedFileAccessPolicy> accessPolicies;
	private final ManagedFileSecurityValidator securityValidator;

	public ManagedFileService(
			ManagedFileRepository files,
			ManagedFileStorage storage,
			IAuditLogService audit,
			List<ManagedFileAccessPolicy> accessPolicies,
			ManagedFileSecurityValidator securityValidator) {
		this.files = files;
		this.storage = storage;
		this.audit = audit;
		this.accessPolicies = accessPolicies;
		this.securityValidator = securityValidator;
	}

	@Transactional
	public ManagedFileView upload(
			MultipartFile upload,
			String businessType,
			String businessId,
			Authentication authentication) {
		String actor = authentication.getName();
		validateDestination(businessType, businessId, actor);
		validate(upload);
		String storedKey = null;
		try {
			byte[] content = upload.getBytes();
			String name = safeName(upload.getOriginalFilename());
			String contentType = securityValidator.validate(name, content);
			String key = LocalDate.now() + "/" + UUID.randomUUID() + ".bin";
			storage.store(key, content, contentType);
			storedKey = key;

			ManagedFile value = new ManagedFile();
			value.setOriginalName(name);
			value.setStorageKey(key);
			value.setContentType(contentType);
			value.setFileSize((long) content.length);
			value.setSha256(sha256(content));
			value.setOwnerUsername(actor);
			value.setBusinessType(required(businessType, "业务类型"));
			value.setBusinessId(blankToNull(businessId));
			value = files.save(value);
			audit.log(actor, "FILE_UPLOAD", "fileId=" + value.getId() + ", type=" + value.getBusinessType());
			return ManagedFileView.from(value);
		} catch (IllegalArgumentException exception) {
			deleteOrphan(storedKey, exception);
			throw exception;
		} catch (Exception exception) {
			deleteOrphan(storedKey, exception);
			throw new IllegalStateException("文件上传失败", exception);
		}
	}

	@Transactional
	public List<ManagedFileView> bind(
			List<String> fileIds,
			String businessType,
			String businessId,
			String actor) {
		String type = required(businessType, "业务类型");
		String targetId = required(businessId, "业务编号");
		List<ManagedFileView> bound = fileIds.stream()
				.filter(id -> id != null && !id.isBlank())
				.distinct()
				.map(id -> {
			ManagedFile file = active(id);
			if (!actor.equals(file.getOwnerUsername())) {
				throw new AccessDeniedException("只能绑定本人上传的文件");
			}
			boolean draft = "WORKFLOW_FORM_DRAFT".equals(file.getBusinessType());
			boolean alreadyBound = type.equals(file.getBusinessType())
					&& targetId.equals(file.getBusinessId());
			if (!draft && !alreadyBound) {
				throw new IllegalArgumentException("文件已绑定到其他业务，不能重复绑定");
			}
			file.setBusinessType(type);
			file.setBusinessId(targetId);
			return ManagedFileView.from(files.save(file));
		}).toList();
		audit.log(actor, "FILE_BIND", "businessType=" + type + ", businessId=" + targetId
				+ ", count=" + bound.size());
		return bound;
	}

	@Transactional(readOnly = true)
	public ManagedFileView metadata(String id, Authentication authentication) {
		ManagedFile file = active(id);
		assertReadable(file, authentication);
		return ManagedFileView.from(file);
	}

	/**
	 * 校验表单 JSON 中的文件引用没有跨业务伪造。
	 *
	 * 浏览器提交的文件名称、下载地址和 businessId 都不可信，服务端只接受
	 * 数据库中仍为 ACTIVE 且已经绑定到同一业务对象的文件标识。
	 */
	@Transactional(readOnly = true)
	public void requireBound(
			List<String> fileIds,
			String businessType,
			String businessId) {
		String type = required(businessType, "业务类型");
		String targetId = required(businessId, "业务编号");
		for (String id : fileIds.stream()
				.filter(value -> value != null && !value.isBlank())
				.distinct()
				.toList()) {
			ManagedFile file = active(id);
			if (!type.equals(file.getBusinessType())
					|| !targetId.equals(file.getBusinessId())) {
				throw new IllegalArgumentException("表单包含不属于当前业务的附件");
			}
		}
	}

	@Transactional(readOnly = true)
	public FileContent read(String id, Authentication authentication) {
		ManagedFile file = active(id);
		assertReadable(file, authentication);
		return new FileContent(file.getOriginalName(), file.getContentType(), storage.read(file.getStorageKey()));
	}

	@Transactional
	public void delete(String id, Authentication authentication) {
		ManagedFile file = active(id);
		boolean manage = authentication.getAuthorities().stream()
				.anyMatch(value -> "file:manage".equals(value.getAuthority()));
		boolean draftOwner = "WORKFLOW_FORM_DRAFT".equals(file.getBusinessType())
				&& authentication.getName().equals(file.getOwnerUsername());
		boolean businessWriter = accessPolicies.stream().anyMatch(policy -> policy.canWrite(
				authentication.getName(),
				file.getBusinessType(),
				file.getBusinessId(),
				file.getId()));
		if (!manage && !draftOwner && !businessWriter) {
			throw new AccessDeniedException("当前业务状态不允许删除该文件");
		}
		storage.delete(file.getStorageKey());
		file.setStatus("DELETED");
		files.save(file);
		audit.log(authentication.getName(), "FILE_DELETE", "fileId=" + id);
	}

	/**
	 * 回收用户离开表单页面后遗留的草稿附件。
	 *
	 * 查询条件同时限定草稿类型、未绑定、ACTIVE 和创建时间，确保定时任务
	 * 永远不会误删已经随流程实例归档的正式附件。单个对象删除失败时保留
	 * ACTIVE 状态供下一轮重试，并继续处理同批其他文件。
	 */
	@Transactional
	public DraftCleanupResult cleanupExpiredDrafts(LocalDateTime cutoff) {
		List<ManagedFile> expired = files
				.findTop100ByBusinessTypeAndBusinessIdIsNullAndStatusAndCreateTimeBeforeOrderByCreateTimeAsc(
						"WORKFLOW_FORM_DRAFT",
						"ACTIVE",
						cutoff);
		int deleted = 0;
		int failed = 0;
		for (ManagedFile file : expired) {
			try {
				storage.delete(file.getStorageKey());
				file.setStatus("DELETED");
				files.save(file);
				deleted += 1;
			} catch (RuntimeException exception) {
				failed += 1;
			}
		}
		if (deleted > 0 || failed > 0) {
			audit.log(
					"system",
					"FILE_DRAFT_CLEANUP",
					"cutoff=" + cutoff + ", deleted=" + deleted + ", failed=" + failed);
		}
		return new DraftCleanupResult(expired.size(), deleted, failed);
	}

	private void assertReadable(ManagedFile file, Authentication authentication) {
		boolean allowed = authentication.getName().equals(file.getOwnerUsername())
				|| authentication.getAuthorities().stream()
						.anyMatch(value -> "file:manage".equals(value.getAuthority()))
				|| accessPolicies.stream().anyMatch(policy -> policy.canRead(
						authentication.getName(),
						file.getBusinessType(),
						file.getBusinessId()));
		if (!allowed) {
			throw new AccessDeniedException("无权访问该文件");
		}
	}

	private void validateDestination(String businessType, String businessId, String actor) {
		if ("WORKFLOW_FORM_DRAFT".equals(businessType)) {
			if (businessId != null && !businessId.isBlank()) {
				throw new IllegalArgumentException("草稿附件不能预先绑定业务编号");
			}
			return;
		}
		if (!"WORKFLOW_FORM".equals(businessType)
				|| businessId == null
				|| businessId.isBlank()) {
			throw new IllegalArgumentException("不支持的文件业务类型");
		}
		boolean writable = accessPolicies.stream()
				.anyMatch(policy -> policy.canWrite(actor, businessType, businessId));
		if (!writable) {
			throw new AccessDeniedException("无权向该业务上传附件");
		}
	}

	private ManagedFile active(String id) {
		ManagedFile file = files.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("文件不存在"));
		if (!"ACTIVE".equals(file.getStatus())) {
			throw new IllegalArgumentException("文件已删除");
		}
		return file;
	}

	private void validate(MultipartFile upload) {
		if (upload == null || upload.isEmpty()) {
			throw new IllegalArgumentException("请选择文件");
		}
		if (upload.getSize() > MAX_SIZE) {
			throw new IllegalArgumentException("单个文件不能超过 20MB");
		}
		String name = safeName(upload.getOriginalFilename());
		int separator = name.lastIndexOf('.');
		String extension = separator < 0 ? "" : name.substring(separator + 1).toLowerCase(Locale.ROOT);
		if (!ALLOWED_EXTENSIONS.contains(extension)) {
			throw new IllegalArgumentException("不支持的文件类型");
		}
	}

	private String safeName(String value) {
		String name = value == null ? "file" : value.replace('\\', '/');
		name = name.substring(name.lastIndexOf('/') + 1).trim();
		if (name.isBlank() || name.length() > 255 || name.contains("\u0000")) {
			throw new IllegalArgumentException("文件名无效");
		}
		return name;
	}

	private String sha256(byte[] content) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
	}

	private String required(String value, String label) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(label + "不能为空");
		}
		return value.trim();
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private void deleteOrphan(String storageKey, Exception original) {
		if (storageKey == null) {
			return;
		}
		try {
			storage.delete(storageKey);
		} catch (RuntimeException cleanupFailure) {
			original.addSuppressed(cleanupFailure);
		}
	}

	public record FileContent(String filename, String contentType, byte[] content) {
	}

	public record DraftCleanupResult(int scanned, int deleted, int failed) {
	}
}
