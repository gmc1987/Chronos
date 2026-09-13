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
	@GetMapping("/knowledge-points/tree")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:knowledge-point:view','education:teaching:view')")
	public ResultData<List<KnowledgePoint>> tree(@RequestParam String courseId, Authentication user) {
		return ok(service.knowledgeTree(courseId, user));
	}
	@GetMapping("/questions/import/template")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:question-bank:view','education:teaching:view')")
	public String template() { return "id,questionType,difficulty,score,stem,answer,bankId,analysis\n"; }
	@PostMapping("/questions/import/precheck")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:question-bank:manage','education:teaching:manage')")
	public ResultData<ImportPreview> precheck(@RequestBody String csv) { return ok(service.precheckCsv(csv)); }
	@PostMapping("/questions/import/confirm")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:question-bank:manage','education:teaching:manage')")
	public ResultData<List<Question>> confirm(@RequestBody String csv, Authentication user) { return ok(service.importCsv(csv, user)); }

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("success").data(data).build();
	}
}
