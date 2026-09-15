package com.chronos.education.scheduling.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.model.ExamItemScore;
import com.chronos.education.scheduling.model.ExamPaperItem;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.education.scheduling.service.ExamPaperAnalysisService;
import com.chronos.education.scheduling.service.ExamPaperAnalysisService.ItemAnalysis;
import com.chronos.education.scheduling.service.ExamPaperAnalysisService.ItemCommand;
import com.chronos.education.scheduling.service.ExamPaperAnalysisService.ScoreCommand;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ExamPaperAnalysisController {
	private final ExamPaperAnalysisService service;
	private final EducationDataScopeService scopes;

	@GetMapping("/admin/education/exam/sessions/{sessionId}/paper-items")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:exam:paper-analysis:view','education:exam:paper-analysis:manage')")
	public ResultData<List<ExamPaperItem>> items(
			@PathVariable String sessionId,
			Authentication authentication) {
		requireFullAccess(authentication);
		return ok(service.items(sessionId));
	}

	@PostMapping("/admin/education/exam/sessions/{sessionId}/paper-items")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:exam:paper-analysis:manage')")
	public ResultData<ExamPaperItem> addItem(
			@PathVariable String sessionId,
			@RequestBody ItemCommand command,
			Authentication authentication) {
		requireFullAccess(authentication);
		return ok(service.addItem(sessionId, command));
	}

	@DeleteMapping("/admin/education/exam/sessions/{sessionId}/paper-items/{itemId}")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:exam:paper-analysis:manage')")
	public ResultData<Void> deleteItem(
			@PathVariable String sessionId,
			@PathVariable String itemId,
			Authentication authentication) {
		requireFullAccess(authentication);
		service.deleteItem(sessionId, itemId);
		return ok(null);
	}

	@GetMapping("/admin/education/exam/sessions/{sessionId}/paper-items/{itemId}/scores")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:exam:paper-analysis:view','education:exam:paper-analysis:manage')")
	public ResultData<List<ExamItemScore>> scores(
			@PathVariable String sessionId,
			@PathVariable String itemId,
			Authentication authentication) {
		requireFullAccess(authentication);
		return ok(service.itemScores(sessionId, itemId));
	}

	@PutMapping("/admin/education/exam/sessions/{sessionId}/paper-items/{itemId}/scores")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:exam:paper-analysis:manage')")
	public ResultData<ExamItemScore> saveScore(
			@PathVariable String sessionId,
			@PathVariable String itemId,
			@RequestBody ScoreCommand command,
			Authentication authentication) {
		requireFullAccess(authentication);
		return ok(service.saveScore(sessionId, itemId, command));
	}

	@GetMapping("/admin/education/exam/sessions/{sessionId}/paper-analysis")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:exam:paper-analysis:view','education:exam:paper-analysis:manage')")
	public ResultData<List<ItemAnalysis>> analysis(
			@PathVariable String sessionId,
			Authentication authentication) {
		requireFullAccess(authentication);
		return ok(service.analysis(sessionId));
	}

	private void requireFullAccess(Authentication authentication) {
		scopes.assertFullAccess(scopes.resolve(authentication.getName()));
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("ok").data(data).build();
	}
}
