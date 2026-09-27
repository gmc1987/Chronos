package com.chronos.service;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.Idao.IAccessReviewItemRepository;
import com.chronos.Idao.IAccessReviewRepository;
import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.IExternalIdentityRepository;
import com.chronos.Idao.IIdentitySourceRepository;
import com.chronos.Idao.IIdentitySyncConflictRepository;
import com.chronos.Idao.IMfaFactorRepository;
import com.chronos.Idao.ITemporaryGrantRepository;
import com.chronos.model.pojo.AccessReview;
import com.chronos.model.pojo.AccessReviewItem;
import com.chronos.model.pojo.AdminUser;
import com.chronos.model.pojo.ExternalIdentity;
import com.chronos.model.pojo.IdentitySource;
import com.chronos.model.pojo.IdentitySyncConflict;
import com.chronos.model.pojo.MfaFactor;
import com.chronos.model.pojo.TemporaryGrant;
import com.chronos.service.iService.IAuditLogService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class IdentityAdminService {
	private final IIdentitySourceRepository sources;
	private final IExternalIdentityRepository externalIdentities;
	private final IIdentitySyncConflictRepository conflicts;
	private final IMfaFactorRepository factors;
	private final IAccessReviewRepository reviews;
	private final IAccessReviewItemRepository reviewItems;
	private final ITemporaryGrantRepository grants;
	private final IAdminUserRepository users;
	private final PasswordEncoder passwordEncoder;
	private final IAuditLogService audit;

	@Transactional
	public IdentitySource saveSource(String id, SourceCommand command, String actor) {
		if (command == null || blank(command.sourceCode()) || blank(command.name())
				|| blank(command.sourceType())) {
			throw new IllegalArgumentException("身份源编码、名称和类型不能为空");
		}
		String type = command.sourceType().trim().toUpperCase(Locale.ROOT);
		if (!List.of("OIDC", "LDAP", "AD").contains(type)) {
			throw new IllegalArgumentException("身份源类型只允许 OIDC、LDAP 或 AD");
		}
		if ("OIDC".equals(type)) {
			validateIssuer(command.issuerUrl());
			if (blank(command.clientId())) {
				throw new IllegalArgumentException("OIDC clientId 不能为空");
			}
		}
		IdentitySource source = id == null
				? new IdentitySource()
				: sources.findById(id).orElseThrow(() -> new IllegalArgumentException("身份源不存在"));
		sources.findBySourceCode(command.sourceCode().trim()).ifPresent(existing -> {
			if (!existing.getId().equals(source.getId())) {
				throw new IllegalArgumentException("身份源编码已存在");
			}
		});
		source.setSourceCode(command.sourceCode().trim());
		source.setName(command.name().trim());
		source.setSourceType(type);
		source.setIssuerUrl(trim(command.issuerUrl()));
		source.setClientId(trim(command.clientId()));
		source.setSecretRef(trim(command.secretRef()));
		source.setConfigJson(blank(command.configJson()) ? "{}" : command.configJson().trim());
		source.setStatus(blank(command.status()) ? "DRAFT" : command.status().trim().toUpperCase(Locale.ROOT));
		IdentitySource saved = sources.save(source);
		audit.log(actor, "IAM_IDENTITY_SOURCE_SAVE", saved.getId());
		return saved;
	}

	@Transactional
	public IdentitySource testSource(String id, String actor) {
		IdentitySource source = requireSource(id);
		if ("OIDC".equalsIgnoreCase(source.getSourceType())) {
			validateIssuer(source.getIssuerUrl());
		}
		source.setLastTestAt(LocalDateTime.now());
		IdentitySource saved = sources.save(source);
		audit.log(actor, "IAM_IDENTITY_SOURCE_TEST", id);
		return saved;
	}

	@Transactional
	public ExternalIdentity bindExternal(String sourceId, ExternalIdentityCommand command, String actor) {
		IdentitySource source = requireSource(sourceId);
		if (!"ACTIVE".equals(source.getStatus())) {
			throw new IllegalStateException("身份源未启用");
		}
		if (command == null || blank(command.externalSubject()) || blank(command.username())) {
			throw new IllegalArgumentException("外部主体和本地账号不能为空");
		}
		AdminUser user = users.findByUsername(command.username().trim());
		if (user == null) {
			throw new IllegalArgumentException("本地账号不存在，禁止自动合并");
		}
		externalIdentities.findBySourceIdAndExternalSubject(sourceId, command.externalSubject().trim())
				.ifPresent(existing -> {
					if (!existing.getUserId().equals(user.getId())) {
						throw new IllegalStateException("外部主体已绑定其他账号，必须人工处理");
					}
				});
		ExternalIdentity identity = externalIdentities
				.findBySourceIdAndExternalSubject(sourceId, command.externalSubject().trim())
				.orElseGet(ExternalIdentity::new);
		identity.setSourceId(sourceId);
		identity.setExternalSubject(command.externalSubject().trim());
		identity.setUserId(user.getId());
		identity.setEmployeeId(user.getEmployeeId());
		identity.setStatus("ACTIVE");
		if (identity.getLinkedAt() == null) {
			identity.setLinkedAt(LocalDateTime.now());
		}
		ExternalIdentity saved = externalIdentities.save(identity);
		audit.log(actor, "IAM_EXTERNAL_IDENTITY_BIND", saved.getId());
		return saved;
	}

	@Transactional
	public IdentitySyncConflict decideConflict(String id, String decision, String actor) {
		IdentitySyncConflict conflict = conflicts.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("同步冲突不存在"));
		String normalized = decision == null ? "" : decision.trim().toUpperCase(Locale.ROOT);
		if (!List.of("RESOLVE", "IGNORE", "UPSTREAM", "LOCAL").contains(normalized)) {
			throw new IllegalArgumentException("冲突决策无效");
		}
		conflict.setStatus("IGNORE".equals(normalized) || "LOCAL".equals(normalized) ? "IGNORED" : "RESOLVED");
		conflict.setDecisionBy(actor);
		conflict.setDecisionAt(LocalDateTime.now());
		IdentitySyncConflict saved = conflicts.save(conflict);
		audit.log(actor, "IAM_IDENTITY_CONFLICT_DECIDE", id + ":" + normalized);
		return saved;
	}

	@Transactional
	public AccessReview createReview(ReviewCommand command, String actor) {
		if (command == null || blank(command.scope()) || command.dueAt() == null) {
			throw new IllegalArgumentException("复核范围和截止时间不能为空");
		}
		if (command.dueAt().isBefore(LocalDateTime.now())) {
			throw new IllegalArgumentException("复核截止时间必须在未来");
		}
		AccessReview review = new AccessReview();
		review.setScope(command.scope().trim());
		review.setDueAt(command.dueAt());
		review.setOwner(blank(command.owner()) ? actor : command.owner().trim());
		review.setStatus("OPEN");
		AccessReview saved = reviews.save(review);
		audit.log(actor, "IAM_ACCESS_REVIEW_CREATE", saved.getId());
		return saved;
	}

	@Transactional
	public AccessReviewItem decideReviewItem(String id, String decision, String actor) {
		AccessReviewItem item = reviewItems.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("授权复核项不存在"));
		String normalized = decision == null ? "" : decision.trim().toUpperCase(Locale.ROOT);
		if (!List.of("CONFIRM", "REVOKE", "REJECT").contains(normalized)) {
			throw new IllegalArgumentException("授权复核决策无效");
		}
		item.setDecision(normalized);
		item.setDecidedBy(actor);
		item.setDecidedAt(LocalDateTime.now());
		AccessReviewItem saved = reviewItems.save(item);
		audit.log(actor, "IAM_ACCESS_REVIEW_ITEM_DECIDE", id + ":" + normalized);
		return saved;
	}

	@Transactional
	public TemporaryGrant requestGrant(GrantCommand command, String actor) {
		if (command == null || blank(command.username()) || blank(command.permissionCode())
				|| blank(command.reason()) || command.validFrom() == null || command.validUntil() == null) {
			throw new IllegalArgumentException("临时授权参数不完整");
		}
		AdminUser user = users.findByUsername(command.username().trim());
		if (user == null) throw new IllegalArgumentException("账号不存在");
		if (!command.validUntil().isAfter(command.validFrom())) {
			throw new IllegalArgumentException("授权结束时间必须晚于开始时间");
		}
		TemporaryGrant grant = new TemporaryGrant();
		grant.setUserId(user.getId());
		grant.setPermissionCode(command.permissionCode().trim());
		grant.setReason(command.reason().trim());
		grant.setValidFrom(command.validFrom());
		grant.setValidUntil(command.validUntil());
		grant.setStatus("REQUESTED");
		TemporaryGrant saved = grants.save(grant);
		audit.log(actor, "IAM_TEMPORARY_GRANT_REQUEST", saved.getId());
		return saved;
	}

	@Transactional
	public TemporaryGrant decideGrant(String id, boolean approve, String actor) {
		TemporaryGrant grant = grants.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("临时授权不存在"));
		if (!"REQUESTED".equals(grant.getStatus())) {
			throw new IllegalStateException("当前临时授权不能审批");
		}
		if (grant.getUserId().equals(users.findByUsername(actor) == null ? null : users.findByUsername(actor).getId())) {
			throw new IllegalArgumentException("申请人不能审批自己的临时授权");
		}
		grant.setApprovedBy(actor);
		grant.setStatus(approve ? "APPROVED" : "REVOKED");
		TemporaryGrant saved = grants.save(grant);
		audit.log(actor, "IAM_TEMPORARY_GRANT_DECIDE", id + ":" + approve);
		return saved;
	}

	@Transactional
	public MfaFactor enrollMfa(String username, String secret, String actor) {
		AdminUser user = users.findByUsername(username);
		if (user == null || blank(secret)) throw new IllegalArgumentException("账号或密钥无效");
		MfaFactor factor = factors.findByUserIdAndFactorType(user.getId(), "TOTP").orElseGet(MfaFactor::new);
		factor.setUserId(user.getId());
		factor.setFactorType("TOTP");
		factor.setSecretCiphertext(passwordEncoder.encode(secret));
		factor.setStatus("PENDING");
		factor.setEnrolledAt(LocalDateTime.now());
		MfaFactor saved = factors.save(factor);
		audit.log(actor, "IAM_MFA_ENROLL", user.getId());
		return saved;
	}

	public IdentitySource requireSource(String id) {
		return sources.findById(id).orElseThrow(() -> new IllegalArgumentException("身份源不存在"));
	}

	private static void validateIssuer(String value) {
		try {
			URI uri = URI.create(value == null ? "" : value.trim());
			if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
					|| uri.getUserInfo() != null || uri.getFragment() != null) {
				throw new IllegalArgumentException();
			}
		} catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("OIDC issuer 必须是合法 HTTPS 地址");
		}
	}

	private static String trim(String value) {
		return blank(value) ? null : value.trim();
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}

	public record SourceCommand(String sourceCode, String name, String sourceType, String issuerUrl,
			String clientId, String secretRef, String status, String configJson) {}
	public record ExternalIdentityCommand(String externalSubject, String username) {}
	public record ReviewCommand(String scope, LocalDateTime dueAt, String owner) {}
	public record GrantCommand(String username, String permissionCode, String reason,
			LocalDateTime validFrom, LocalDateTime validUntil) {}
}
