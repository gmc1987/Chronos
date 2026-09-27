package com.chronos.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.chronos.commons.model.ResultData;
import com.chronos.Idao.IAccessReviewItemRepository;
import com.chronos.Idao.IAccessReviewRepository;
import com.chronos.Idao.IExternalIdentityRepository;
import com.chronos.Idao.IIdentitySourceRepository;
import com.chronos.Idao.IIdentitySyncConflictRepository;
import com.chronos.Idao.ITemporaryGrantRepository;
import com.chronos.model.pojo.AccessReview;
import com.chronos.model.pojo.AccessReviewItem;
import com.chronos.model.pojo.ExternalIdentity;
import com.chronos.model.pojo.IdentitySource;
import com.chronos.model.pojo.IdentitySyncConflict;
import com.chronos.model.pojo.TemporaryGrant;
import com.chronos.service.IdentityAdminService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class IdentityAdminController {
	private final IdentityAdminService service;
	private final IIdentitySourceRepository sources;
	private final IExternalIdentityRepository externalIdentities;
	private final IIdentitySyncConflictRepository conflicts;
	private final IAccessReviewRepository reviews;
	private final IAccessReviewItemRepository reviewItems;
	private final ITemporaryGrantRepository grants;

	@GetMapping("/admin/iam/identity-sources")
	@PreAuthorize("@iamAuthorization.has(authentication,'iam:identity:source:view')")
	public ResultData<List<IdentitySource>> sources() { return ok(sources.findAll()); }

	@PostMapping("/admin/iam/identity-sources")
	@PreAuthorize("@iamAuthorization.has(authentication,'iam:identity:source:manage')")
	public ResultData<IdentitySource> saveSource(@RequestBody IdentityAdminService.SourceCommand command, Principal actor) {
		return ok(service.saveSource(null, command, actor.getName()));
	}

	@PutMapping("/admin/iam/identity-sources/{id}")
	@PreAuthorize("@iamAuthorization.has(authentication,'iam:identity:source:manage')")
	public ResultData<IdentitySource> updateSource(@PathVariable String id,
			@RequestBody IdentityAdminService.SourceCommand command, Principal actor) {
		return ok(service.saveSource(id, command, actor.getName()));
	}

	@PostMapping("/admin/iam/identity-sources/{id}/test")
	@PreAuthorize("@iamAuthorization.has(authentication,'iam:identity:source:test')")
	public ResultData<IdentitySource> testSource(@PathVariable String id, Principal actor) {
		return ok(service.testSource(id, actor.getName()));
	}

	@GetMapping("/admin/iam/external-identities")
	@PreAuthorize("@iamAuthorization.has(authentication,'iam:identity:external:view')")
	public ResultData<List<ExternalIdentity>> externalIdentities() { return ok(externalIdentities.findAll()); }

	@PostMapping("/admin/iam/external-identities/{sourceId}/bind")
	@PreAuthorize("@iamAuthorization.has(authentication,'iam:identity:external:manage')")
	public ResultData<ExternalIdentity> bind(@PathVariable String sourceId,
			@RequestBody IdentityAdminService.ExternalIdentityCommand command, Principal actor) {
		return ok(service.bindExternal(sourceId, command, actor.getName()));
	}

	@GetMapping("/admin/iam/sync-conflicts")
	@PreAuthorize("@iamAuthorization.has(authentication,'iam:identity:conflict:view')")
	public ResultData<List<IdentitySyncConflict>> conflicts(
			@RequestParam(defaultValue = "OPEN") String status) {
		return ok(conflicts.findByStatusOrderByCreateTimeDesc(status.toUpperCase()));
	}

	@PostMapping("/admin/iam/sync-conflicts/{id}/decide")
	@PreAuthorize("@iamAuthorization.has(authentication,'iam:identity:conflict:decide')")
	public ResultData<IdentitySyncConflict> decideConflict(@PathVariable String id,
			@RequestBody Decision command, Principal actor) {
		return ok(service.decideConflict(id, command.decision(), actor.getName()));
	}

	@GetMapping("/admin/iam/access-reviews")
	@PreAuthorize("@iamAuthorization.has(authentication,'iam:access-review:view')")
	public ResultData<List<AccessReview>> reviews() { return ok(reviews.findAllByOrderByCreateTimeDesc()); }

	@PostMapping("/admin/iam/access-reviews")
	@PreAuthorize("@iamAuthorization.has(authentication,'iam:access-review:manage')")
	public ResultData<AccessReview> createReview(@RequestBody IdentityAdminService.ReviewCommand command, Principal actor) {
		return ok(service.createReview(command, actor.getName()));
	}

	@GetMapping("/admin/iam/access-reviews/{id}/items")
	@PreAuthorize("@iamAuthorization.has(authentication,'iam:access-review:view')")
	public ResultData<List<AccessReviewItem>> reviewItems(@PathVariable String id) {
		return ok(reviewItems.findByReviewIdOrderByCreateTimeAsc(id));
	}

	@PostMapping("/admin/iam/access-review-items/{id}/decide")
	@PreAuthorize("@iamAuthorization.has(authentication,'iam:access-review:manage')")
	public ResultData<AccessReviewItem> decideReviewItem(@PathVariable String id,
			@RequestBody Decision command, Principal actor) {
		return ok(service.decideReviewItem(id, command.decision(), actor.getName()));
	}

	@GetMapping("/admin/iam/temporary-grants")
	@PreAuthorize("@iamAuthorization.has(authentication,'iam:temporary-grant:view')")
	public ResultData<List<TemporaryGrant>> grants() { return ok(grants.findAll()); }

	@PostMapping("/admin/iam/temporary-grants")
	@PreAuthorize("@iamAuthorization.has(authentication,'iam:temporary-grant:manage')")
	public ResultData<TemporaryGrant> requestGrant(@RequestBody IdentityAdminService.GrantCommand command, Principal actor) {
		return ok(service.requestGrant(command, actor.getName()));
	}

	@PostMapping("/admin/iam/temporary-grants/{id}/decide")
	@PreAuthorize("@iamAuthorization.has(authentication,'iam:temporary-grant:manage')")
	public ResultData<TemporaryGrant> decideGrant(@PathVariable String id,
			@RequestBody Approval command, Principal actor) {
		return ok(service.decideGrant(id, command.approve(), actor.getName()));
	}

	private <T> ResultData<T> ok(T value) {
		return ResultData.<T>builder().code("200").msg("ok").data(value).build();
	}

	public record Decision(String decision) {}
	public record Approval(boolean approve) {}
}
