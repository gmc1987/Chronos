package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.chronos.education.scheduling.dao.*;
import com.chronos.file.service.ManagedFileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QuestionKnowledgeServiceTest {
	private QuestionKnowledgeService service;

	@BeforeEach
	void setUp() {
		service = new QuestionKnowledgeService(mock(QuestionBankRepository.class), mock(QuestionRepository.class),
				mock(QuestionOptionRepository.class), mock(QuestionKnowledgePointRepository.class),
				mock(KnowledgePointRepository.class), mock(QuestionVersionRepository.class),
				mock(QuestionReferenceRepository.class), mock(QuestionFileRepository.class),
				mock(ManagedFileService.class), mock(CourseOfferingRepository.class),
				mock(EducationDataScopeService.class), new ObjectMapper());
	}

	@Test
	void precheckReturnsStableHashAndCountsRows() {
		var preview = service.precheckCsv("id,questionType,difficulty,score,stem,answer,bankId,analysis,knowledgePointIds\n"
				+ "1,SINGLE_CHOICE,EASY,1,题干,A,bank,,kp\n");
		assertThat(preview.acceptedRows()).isOne();
		assertThat(preview.totalRows()).isOne();
		assertThat(preview.precheckHash()).hasSize(64);
		assertThat(preview.errors()).isEmpty();
	}

	@Test
	void precheckRejectsFormulaInjection() {
		var preview = service.precheckCsv(
				"id,questionType,difficulty,score,stem,answer,bankId,analysis,knowledgePointIds\n"
						+ "1,SINGLE_CHOICE,EASY,1,=cmd,A,bank,,kp\n");
		assertThat(preview.acceptedRows()).isZero();
		assertThat(preview.errors()).hasSize(1);
	}
}
