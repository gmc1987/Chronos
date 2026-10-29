package com.chronos.education.operationagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OperationAgentToolCatalogTest {
	private final OperationAgentToolCatalog catalog = new OperationAgentToolCatalog();

	@Test
	void exposesTheCompleteOpeningAndClosingLifecycleWithGovernanceMetadata() {
		assertThat(catalog.all()).hasSize(28);
		assertThat(catalog.require("scheduling.review.submitted").approvalActorExecutes()).isTrue();
		assertThat(catalog.require("admission.enroll.batch").writeTool()).isTrue();
		assertThat(catalog.require("semester.context.check").maxAttempts()).isEqualTo(2);
		assertThat(catalog.require("teaching.assignment.generate").businessStageOrder()).isEqualTo(5);
		assertThat(catalog.require("teaching.offering.generate").businessStageOrder()).isEqualTo(6);
		assertThat(catalog.require("scheduling.publish").businessStageOrder()).isEqualTo(7);
		assertThat(catalog.require("term.end.exam.check").businessStageOrder()).isEqualTo(8);
		assertThat(catalog.require("term.graduation.prepare").businessStageOrder()).isEqualTo(9);
		assertThat(catalog.require("term.end.archive.check").businessStageOrder()).isEqualTo(10);
		assertThat(catalog.require("admission.accommodation.check").businessStageOrder()).isEqualTo(4);
		assertThat(catalog.require("admission.accommodation.assign.batch").approvalRequired()).isTrue();
		assertThat(catalog.executionOrder("admission.import.managed-file"))
				.isLessThan(catalog.executionOrder("admission.data.check"));
		assertThat(catalog.executionOrder("admission.data.check"))
				.isLessThan(catalog.executionOrder("admission.class.assign.batch"));
		assertThatThrownBy(() -> catalog.require("database.raw.sql"))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
