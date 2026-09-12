package com.chronos.education.scheduling.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.dao.KnowledgePointRepository;
import com.chronos.education.scheduling.dao.QuestionBankRepository;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.education.scheduling.model.KnowledgePoint;
import com.chronos.education.scheduling.model.QuestionBank;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

/** 题库/知识点的稳定导出端点；导入在服务校验后再落库，避免绕过数据范围。 */
@RestController
@RequestMapping("/education/teaching-center")
public class TeachingCenterDomainController {
	private final QuestionBankRepository banks;
	private final KnowledgePointRepository points;
	private final EducationDataScopeService scopes;

	public TeachingCenterDomainController(QuestionBankRepository banks, KnowledgePointRepository points,
			EducationDataScopeService scopes) {
		this.banks = banks;
		this.points = points;
		this.scopes = scopes;
	}

	@GetMapping("/question-banks/export")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:question-bank:view','education:teaching:manage')")
	public ResultData<List<QuestionBank>> exportQuestionBanks(@RequestParam(required = false) String offeringId,
			Authentication authentication) {
		EducationDataScope scope = scopes.resolve(authentication.getName());
		if (offeringId != null && !offeringId.isBlank()) scopes.assertOfferingAccess(scope, offeringId);
		else scopes.assertFullAccess(scope);
		return ok(offeringId == null ? banks.findAll() : banks.findByOfferingIdAndArchivedFalse(offeringId));
	}

	@GetMapping("/knowledge-points/export")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:knowledge-point:view','education:teaching:manage')")
	public ResultData<List<KnowledgePoint>> exportKnowledgePoints(
			@RequestParam(required = false) String subjectId, Authentication authentication) {
		scopes.assertFullAccess(scopes.resolve(authentication.getName()));
		return ok(subjectId == null ? points.findAll() : points.findBySubjectIdAndEnabledTrueOrderBySortOrderAsc(subjectId));
	}

	private <T> ResultData<T> ok(T value) {
		return ResultData.<T>builder().code("200").msg("success").data(value).build();
	}
}
