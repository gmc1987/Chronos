package com.chronos.education.scheduling.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.model.*;
import com.chronos.education.scheduling.model.dto.QuestionDtos.*;
import com.chronos.education.scheduling.service.QuestionKnowledgeService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/education/teaching-center")
public class QuestionKnowledgeController {
	private final QuestionKnowledgeService service;
	public QuestionKnowledgeController(QuestionKnowledgeService service) { this.service = service; }

	@PostMapping("/question-banks")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:question-bank:manage','education:teaching:manage')")
	public ResultData<QuestionBank> createBank(@RequestBody BankRequest request, Authentication user) {
		return ok(service.createBank(request, user));
	}
	@PutMapping("/question-banks/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:question-bank:manage','education:teaching:manage')")
	public ResultData<QuestionBank> updateBank(@PathVariable String id, @RequestBody BankRequest request,
			Authentication user) {
		QuestionBank bank = service.updateBank(id, request, user);
		return ok(bank);
	}
	@PostMapping("/questions")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:question-bank:manage','education:teaching:manage')")
	public ResultData<Question> createQuestion(@RequestBody QuestionRequest request, Authentication user) {
		return ok(service.createQuestion(request, user));
	}
	@PutMapping("/questions/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:question-bank:manage','education:teaching:manage')")
	public ResultData<Question> updateQuestion(@PathVariable String id, @RequestBody QuestionRequest request, Authentication user) {
		return ok(service.updateQuestion(id, request, user));
	}
	@GetMapping("/question-banks/{id}/questions")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:question-bank:view','education:teaching:view')")
	public ResultData<List<Question>> questions(@PathVariable String id, Authentication user) {
		return ok(service.questions(id, user));
	}
	@GetMapping("/questions/{id}/versions")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:question-bank:view','education:teaching:view')")
	public ResultData<List<QuestionVersion>> versions(@PathVariable String id, Authentication user) {
		return ok(service.versions(id, user));
	}
	@PostMapping("/questions/{id}/submit")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:question-bank:manage','education:teaching:manage')")
	public ResultData<Question> submit(@PathVariable String id, Authentication user) {
		return ok(service.submitQuestion(id, user));
	}
	@PostMapping("/questions/{id}/approve")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:question-bank:review','education:question-bank:manage','education:teaching:manage')")
	public ResultData<Question> approve(@PathVariable String id, Authentication user) { return ok(service.approveQuestion(id, user)); }
	@PostMapping("/questions/{id}/publish")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:question-bank:publish','education:question-bank:manage','education:teaching:manage')")
	public ResultData<Question> publish(@PathVariable String id, Authentication user) { return ok(service.publishQuestion(id, user)); }
	@PostMapping("/questions/{id}/withdraw")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:question-bank:publish','education:question-bank:manage','education:teaching:manage')")
	public ResultData<Question> withdraw(@PathVariable String id, Authentication user) { return ok(service.withdrawQuestion(id, user)); }
	@PostMapping("/questions/{id}/revise")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:question-bank:manage','education:teaching:manage')")
	public ResultData<Question> revise(@PathVariable String id, Authentication user) { return ok(service.reviseQuestion(id, user)); }
	@PostMapping("/questions/{id}/archive")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:question-bank:manage','education:teaching:manage')")
	public ResultData<Question> archive(@PathVariable String id, Authentication user) { return ok(service.archiveQuestion(id, user)); }
	@PostMapping("/questions/{id}/rollback/{versionNo}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:question-bank:manage','education:teaching:manage')")
	public ResultData<Question> rollback(@PathVariable String id, @PathVariable int versionNo, Authentication user) {
		return ok(service.rollbackQuestion(id, versionNo, user));
	}
	@PostMapping("/knowledge-points")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:knowledge-point:manage','education:teaching:manage')")
	public ResultData<KnowledgePoint> createPoint(@RequestBody KnowledgePointRequest request, Authentication user) {
		return ok(service.createKnowledgePoint(request, user));
	}
	@PutMapping("/knowledge-points/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:knowledge-point:manage','education:teaching:manage')")
	public ResultData<KnowledgePoint> updatePoint(@PathVariable String id, @RequestBody KnowledgePointRequest request, Authentication user) {
		return ok(service.updateKnowledgePoint(id, request, user));
	}
	@PostMapping("/knowledge-points/{id}/disable")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:knowledge-point:manage','education:teaching:manage')")
	public ResultData<Void> disablePoint(@PathVariable String id, Authentication user) {
		service.disableKnowledgePoint(id, user); return ok(null);
	}
	@PostMapping("/knowledge-points/{id}/enable")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:knowledge-point:manage','education:teaching:manage')")
	public ResultData<KnowledgePoint> enablePoint(@PathVariable String id, Authentication user) {
		return ok(service.enableKnowledgePoint(id, user));
	}
	@PostMapping("/knowledge-points/{id}/move")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:knowledge-point:manage','education:teaching:manage')")
	public ResultData<KnowledgePoint> movePoint(@PathVariable String id,
			@RequestBody KnowledgePointMoveRequest request, Authentication user) {
		return ok(service.moveKnowledgePoint(id, request, user));
	}
	@GetMapping("/knowledge-points/tree")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:knowledge-point:view','education:teaching:view')")
	public ResultData<List<KnowledgePoint>> tree(@RequestParam String courseId, Authentication user) {
		return ok(service.knowledgeTree(courseId, user));
	}
	@GetMapping("/questions/import/template")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:question-bank:view','education:teaching:view')")
	public String template() { return "id,questionType,difficulty,score,stem,answer,bankId,analysis,knowledgePointIds\n"; }
	@PostMapping("/questions/import/precheck")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:question-bank:manage','education:teaching:manage')")
	public ResultData<ImportPreview> precheck(@RequestBody String csv) { return ok(service.precheckCsv(csv)); }
	@PostMapping("/questions/import/confirm")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:question-bank:manage','education:teaching:manage')")
	public ResultData<List<Question>> confirm(@RequestBody ImportConfirm request, Authentication user) {
		return ok(service.importCsv(request.csv(), request.precheckHash(), user));
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("success").data(data).build();
	}
}
