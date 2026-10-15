package com.chronos.education.scheduling.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.model.SchedulingAiConfirmRequest;
import com.chronos.education.scheduling.model.SchedulingAiReplyRequest;
import com.chronos.education.scheduling.model.SchedulingAiRunRequest;
import com.chronos.education.scheduling.model.SchedulingAiRunView;
import com.chronos.education.scheduling.model.ScheduleCandidateView;
import com.chronos.education.scheduling.model.ScheduleDiffView;
import com.chronos.education.scheduling.model.SchedulingCandidateExplanation;
import com.chronos.education.scheduling.model.SchedulingAiRuleEvidence;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.education.scheduling.service.SchedulingAiRunService;
import com.chronos.education.scheduling.service.SchedulingAiRuleCatalog;
import com.chronos.education.scheduling.service.SchedulingAiRuleEvidenceService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Governed AI scheduling API; it never exposes apply, publish or rollback. */
@RestController
public class SchedulingAiRunController {
	private final SchedulingAiRunService runs;
	private final EducationDataScopeService dataScopes;
	private final SchedulingAiRuleEvidenceService ruleEvidence;

	public SchedulingAiRunController(
			SchedulingAiRunService runs,
			EducationDataScopeService dataScopes,
			SchedulingAiRuleEvidenceService ruleEvidence) {
		this.runs = runs;
		this.dataScopes = dataScopes;
		this.ruleEvidence = ruleEvidence;
	}

	@GetMapping("/admin/education/scheduling/ai/rule-catalog")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:ai:agent:use') && @iamAuthorization.has(authentication,'education:scheduling:manage')")
	public ResultData<?> ruleCatalog(Authentication authentication) {
		requireFullDataAccess(authentication);
		return ok(java.util.Map.of("version", SchedulingAiRuleCatalog.VERSION,
				"definitions", SchedulingAiRuleCatalog.definitions()));
	}

	@PostMapping("/admin/education/scheduling/ai/runs")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:ai:agent:use') && @iamAuthorization.has(authentication,'education:scheduling:manage') && @iamAuthorization.has(authentication,'education:scheduling:ai:use')")
	public ResultData<SchedulingAiRunView> create(
			@RequestBody SchedulingAiRunRequest request,
			Authentication authentication) {
		requireFullDataAccess(authentication);
		return ok(runs.create(request, authentication.getName()));
	}

	@GetMapping("/admin/education/scheduling/ai/runs/{id}")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:ai:agent:use') && @iamAuthorization.has(authentication,'education:scheduling:manage') && @iamAuthorization.any(authentication,'education:scheduling:ai:use','education:scheduling:ai:operations')")
	public ResultData<SchedulingAiRunView> get(
			@PathVariable String id,
			Authentication authentication) {
		requireFullDataAccess(authentication);
		boolean operations = hasOperations(authentication);
		return ok(runs.get(id, authentication.getName(), operations));
	}

	@PostMapping("/admin/education/scheduling/ai/runs/{id}/reply")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:ai:agent:use') && @iamAuthorization.has(authentication,'education:scheduling:manage') && @iamAuthorization.has(authentication,'education:scheduling:ai:use')")
	public ResultData<SchedulingAiRunView> reply(
			@PathVariable String id,
			@RequestBody SchedulingAiReplyRequest request,
			Authentication authentication) {
		requireFullDataAccess(authentication);
		return ok(runs.reply(id, request, authentication.getName()));
	}

	@PostMapping("/admin/education/scheduling/ai/runs/{id}/confirm")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:ai:agent:use') && @iamAuthorization.has(authentication,'education:scheduling:manage') && @iamAuthorization.has(authentication,'education:scheduling:ai:confirm')")
	public ResultData<SchedulingAiRunView> confirm(
			@PathVariable String id,
			@RequestBody SchedulingAiConfirmRequest request,
			Authentication authentication) {
		requireFullDataAccess(authentication);
		return ok(runs.confirm(id, request, authentication.getName()));
	}

	@PostMapping("/admin/education/scheduling/ai/runs/{id}/generate")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:ai:agent:use') && @iamAuthorization.has(authentication,'education:scheduling:manage') && @iamAuthorization.has(authentication,'education:scheduling:ai:use')")
	public ResultData<SchedulingAiRunView> generate(
			@PathVariable String id,
			Authentication authentication) {
		requireFullDataAccess(authentication);
		return ok(runs.generate(id, authentication.getName()));
	}

	@GetMapping("/admin/education/scheduling/ai/runs/{id}/candidates")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:ai:agent:use') && @iamAuthorization.has(authentication,'education:scheduling:manage') && @iamAuthorization.any(authentication,'education:scheduling:ai:use','education:scheduling:ai:operations')")
	public ResultData<List<ScheduleCandidateView>> candidates(
			@PathVariable String id,
			Authentication authentication) {
		requireFullDataAccess(authentication);
		return ok(runs.candidates(id, authentication.getName(), hasOperations(authentication)));
	}

	@PostMapping("/admin/education/scheduling/ai/runs/{id}/compare")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:ai:agent:use') && @iamAuthorization.has(authentication,'education:scheduling:manage') && @iamAuthorization.has(authentication,'education:scheduling:ai:use')")
	public ResultData<List<ScheduleCandidateView>> compare(
			@PathVariable String id, @RequestBody List<String> candidateIds,
			Authentication authentication) {
		requireFullDataAccess(authentication);
		return ok(runs.compare(id, authentication.getName(), candidateIds));
	}

	@GetMapping("/admin/education/scheduling/ai/runs/{id}/candidates/{candidateId}/preview")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:ai:agent:use') && @iamAuthorization.has(authentication,'education:scheduling:manage') && @iamAuthorization.has(authentication,'education:scheduling:ai:use')")
	public ResultData<ScheduleDiffView> preview(
			@PathVariable String id, @PathVariable String candidateId, Authentication authentication) {
		requireFullDataAccess(authentication);
		return ok(runs.preview(id, authentication.getName(), candidateId));
	}

	@GetMapping("/admin/education/scheduling/ai/runs/{id}/candidates/{candidateId}/explanation")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:ai:agent:use') && @iamAuthorization.has(authentication,'education:scheduling:manage') && @iamAuthorization.has(authentication,'education:scheduling:ai:use')")
	public ResultData<SchedulingCandidateExplanation> explain(
			@PathVariable String id, @PathVariable String candidateId, Authentication authentication) {
		requireFullDataAccess(authentication);
		return ok(runs.explain(id, authentication.getName(), candidateId));
	}

	@GetMapping("/admin/education/scheduling/ai/runs/{id}/candidates/{candidateId}/rule-evidence")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:ai:agent:use') && @iamAuthorization.has(authentication,'education:scheduling:manage') && @iamAuthorization.has(authentication,'education:scheduling:ai:use')")
	public ResultData<SchedulingAiRuleEvidence> ruleEvidence(
			@PathVariable String id, @PathVariable String candidateId,
			Authentication authentication) {
		requireFullDataAccess(authentication);
		var run = runs.get(id, authentication.getName(), false);
		var candidate = runs.candidates(id, authentication.getName(), false).stream()
				.filter(item -> candidateId.equals(item.id())).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("候选不属于当前 AI Run"));
		return ok(ruleEvidence.evaluate(run.plan(), candidate));
	}

	@PostMapping("/admin/education/scheduling/ai/runs/{id}/cancel")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:ai:agent:use') && @iamAuthorization.has(authentication,'education:scheduling:manage') && @iamAuthorization.any(authentication,'education:scheduling:ai:use','education:scheduling:ai:operations')")
	public ResultData<SchedulingAiRunView> cancel(
			@PathVariable String id,
			Authentication authentication) {
		requireFullDataAccess(authentication);
		return ok(runs.cancel(id, authentication.getName(), hasOperations(authentication)));
	}

	private boolean hasOperations(Authentication authentication) {
		return authentication.getAuthorities().stream()
				.anyMatch(authority -> {
					String value = authority.getAuthority();
					return "education:scheduling:ai:operations".equals(value)
							|| "education:scheduling:ai:*".equals(value)
							|| "*:*".equals(value)
							|| "ROLE_CODE_SUPER_ADMIN".equals(value);
				});
	}

	private void requireFullDataAccess(Authentication authentication) {
		dataScopes.assertFullAccess(dataScopes.resolve(authentication.getName()));
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder()
				.code("200")
				.msg("success")
				.data(data)
				.build();
	}
}
