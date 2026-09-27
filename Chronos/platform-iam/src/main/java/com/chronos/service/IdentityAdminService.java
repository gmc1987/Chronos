package com.chronos.service;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
	private final IAuditLogService audit;
	private final ObjectMapper objectMapper;

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
		String status = blank(command.status()) ? "DRAFT" : command.status().trim().toUpperCase(Locale.ROOT);
		if ("OIDC".equals(type)) {
			validateOidcConfiguration(command, status);
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
		source.setStatus(status);
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
	public AccessReview submitReview(String id, String actor) {
		AccessReview review = reviews.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("授权复核不存在"));
		if (!"OPEN".equals(review.getStatus())) {
			throw new IllegalStateException("当前复核批次不能提交");
		}
		review.setStatus("SUBMITTED");
		review.setSubmittedAt(LocalDateTime.now());
		AccessReview saved = reviews.save(review);
		audit.log(actor, "IAM_ACCESS_REVIEW_SUBMIT", id);
		return saved;
	}

	@Transactional
	public AccessReview completeReview(String id, String actor) {
		AccessReview review = reviews.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("授权复核不存在"));
		if (!"SUBMITTED".equals(review.getStatus())) {
			throw new IllegalStateException("复核批次必须先提交");
		}
		if (reviewItems.countByReviewIdAndDecisionIsNull(id) > 0) {
			throw new IllegalStateException("仍有未决复核项");
		}
		review.setStatus("COMPLETED");
		review.setCompletedAt(LocalDateTime.now());
		AccessReview saved = reviews.save(review);
		audit.log(actor, "IAM_ACCESS_REVIEW_COMPLETE", id);
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
		grant.setRequestedBy(actor);
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
		if (actor.equals(grant.getRequestedBy()) || actor.equals(grant.getApprovedBy())) {
			throw new IllegalArgumentException("申请人和第一审批人不能重复");
		}
		if (!approve) {
			grant.setApprovedBy(actor);
			grant.setStatus("REVOKED");
		} else if (grant.getApprovedBy() == null) {
			grant.setApprovedBy(actor);
		} else {
			grant.setSecondApprovedBy(actor);
			grant.setStatus(LocalDateTime.now().isBefore(grant.getValidFrom()) ? "APPROVED" : "ACTIVE");
		}
		TemporaryGrant saved = grants.save(grant);
		audit.log(actor, "IAM_TEMPORARY_GRANT_DECIDE", id + ":" + approve);
		return saved;
	}

	@Transactional
	public TemporaryGrant revokeGrant(String id, String actor) {
		TemporaryGrant grant = grants.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("临时授权不存在"));
		if ("EXPIRED".equals(grant.getStatus()) || "REVOKED".equals(grant.getStatus())) {
			return grant;
		}
		grant.setStatus("REVOKED");
		grant.setRevokedAt(LocalDateTime.now());
		TemporaryGrant saved = grants.save(grant);
		users.findById(grant.getUserId()).ifPresent(user -> {
			user.setTokenVersion((user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1);
			users.save(user);
		});
		audit.log(actor, "IAM_TEMPORARY_GRANT_REVOKE", id);
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

	private void validateOidcConfiguration(SourceCommand command, String status) {
		final JsonNode config;
		try {
			config = objectMapper.readTree(blank(command.configJson()) ? "{}" : command.configJson());
		} catch (Exception exception) {
			throw new IllegalArgumentException("OIDC source configuration is invalid", exception);
		}
		if (config == null || !config.isObject()) {
			throw new IllegalArgumentException("OIDC source configuration must be a JSON object");
		}
		if (config.has("clientSecret") || config.has("client_secret")) {
			throw new IllegalArgumentException("OIDC client secret must use secretRef");
		}
		if (!"ACTIVE".equals(status)) {
			return;
		}
		JsonNode redirectUris = config.get("redirectUris");
		if (redirectUris == null || !redirectUris.isArray() || redirectUris.isEmpty()) {
			throw new IllegalArgumentException("ACTIVE OIDC source requires redirectUris");
		}
		for (JsonNode redirectUri : redirectUris) {
			validateRedirectUri(redirectUri.asText());
		}
		String endpoint = config.path("tokenEndpoint").asText(null);
		validateTokenEndpoint(command.issuerUrl(), endpoint);
		boolean publicClient = config.path("publicClient").asBoolean(false);
		if (!publicClient && blank(command.secretRef())) {
			throw new IllegalArgumentException("confidential OIDC source requires secretRef");
		}
	}

	private static void validateRedirectUri(String value) {
		try {
			URI uri = URI.create(value == null ? "" : value);
			if ((!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme()))
					|| uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
				throw new IllegalArgumentException();
			}
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("OIDC redirect URI is invalid", exception);
		}
	}

	private static void validateTokenEndpoint(String issuerUrl, String endpoint) {
		try {
			URI issuer = URI.create(issuerUrl);
			URI token = URI.create(endpoint == null ? "" : endpoint);
			if (!"https".equalsIgnoreCase(token.getScheme()) || token.getHost() == null
					|| !token.getHost().equalsIgnoreCase(issuer.getHost())
					|| token.getUserInfo() != null || token.getFragment() != null) {
				throw new IllegalArgumentException();
			}
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("OIDC token endpoint must be HTTPS on the issuer host", exception);
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
