package com.chronos.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.chronos.ai.dao.AiUsagePolicyRepository;
import com.chronos.ai.dao.AiUsageRecordRepository;
import com.chronos.ai.model.AiModel;
import com.chronos.ai.model.AiUsagePolicy;
import com.chronos.ai.model.AiUsageRecord;

class AiUsageGovernanceServiceTest {
	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void providerUsageProducesAuditableTokensAndCost() {
		AiUsagePolicyRepository policies = mock(AiUsagePolicyRepository.class);
		AiUsageRecordRepository records = mock(AiUsageRecordRepository.class);
		AtomicReference<AiUsageRecord> persisted = new AtomicReference<>();
		when(policies.findApplicableForUpdate("model-1", "admin")).thenReturn(List.of());
		when(records.saveAndFlush(any())).thenAnswer(invocation -> {
			AiUsageRecord record = invocation.getArgument(0);
			record.setId("usage-1");
			persisted.set(record);
			return record;
		});
		when(records.findById("usage-1")).thenAnswer(invocation -> Optional.ofNullable(persisted.get()));
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken("admin", "n/a"));

		AiModel model = model();
		AiUsageGovernanceService service = new AiUsageGovernanceService(policies, records);
		AiUsageGovernanceService.Reservation reservation = service.begin(model, "CHAT", "输入内容");
		ChatResponse response = mock(ChatResponse.class);
		ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
		Usage usage = mock(Usage.class);
		when(response.getMetadata()).thenReturn(metadata);
		when(metadata.getUsage()).thenReturn(usage);
		when(usage.getPromptTokens()).thenReturn(100);
		when(usage.getCompletionTokens()).thenReturn(50);

		service.complete(reservation, model, response, "输出内容");

		ArgumentCaptor<AiUsageRecord> saved = ArgumentCaptor.forClass(AiUsageRecord.class);
		verify(records).save(saved.capture());
		assertThat(saved.getValue().getStatus()).isEqualTo("SUCCEEDED");
		assertThat(saved.getValue().getTotalTokens()).isEqualTo(150);
		assertThat(saved.getValue().getTokenEstimated()).isFalse();
		assertThat(saved.getValue().getCostAmount()).isEqualByComparingTo("0.00060000");
	}

	@Test
	void userRateLimitRejectsBeforeProviderAndStillWritesAuditRecord() {
		AiUsagePolicyRepository policies = mock(AiUsagePolicyRepository.class);
		AiUsageRecordRepository records = mock(AiUsageRecordRepository.class);
		AiUsagePolicy policy = new AiUsagePolicy();
		policy.setScopeType("USER");
		policy.setScopeKey("admin");
		policy.setRequestsPerMinute(1);
		policy.setEnabled(true);
		when(policies.findApplicableForUpdate("model-1", "admin")).thenReturn(List.of(policy));
		when(records.countByUsernameAndCreateTimeGreaterThanEqual(eq("admin"), any())).thenReturn(1L);
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken("admin", "n/a"));
		AiUsageGovernanceService service = new AiUsageGovernanceService(policies, records);

		assertThatThrownBy(() -> service.begin(model(), "CHAT", "输入内容"))
				.isInstanceOf(AiUsageLimitException.class)
				.hasMessageContaining("每分钟请求上限");

		ArgumentCaptor<AiUsageRecord> rejected = ArgumentCaptor.forClass(AiUsageRecord.class);
		verify(records).saveAndFlush(rejected.capture());
		assertThat(rejected.getValue().getStatus()).isEqualTo("REJECTED");
		assertThat(rejected.getValue().getErrorCode()).isEqualTo("LIMIT_USER");
	}

	private AiModel model() {
		AiModel model = new AiModel();
		model.setId("model-1");
		model.setInputCostPerMillion(new BigDecimal("2"));
		model.setOutputCostPerMillion(new BigDecimal("8"));
		return model;
	}
}
