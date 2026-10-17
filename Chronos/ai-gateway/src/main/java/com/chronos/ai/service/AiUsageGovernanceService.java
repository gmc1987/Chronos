package com.chronos.ai.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.ai.dao.AiUsagePolicyRepository;
import com.chronos.ai.dao.AiUsageRecordRepository;
import com.chronos.ai.model.AiModel;
import com.chronos.ai.model.AiUsagePolicy;
import com.chronos.ai.model.AiUsageRecord;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

/** Reserves requests before provider calls and records provider token metadata afterwards. */
@Service
public class AiUsageGovernanceService {
	private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000L);

	private final AiUsagePolicyRepository policies;
	private final AiUsageRecordRepository records;

	public AiUsageGovernanceService(
			AiUsagePolicyRepository policies,
			AiUsageRecordRepository records) {
		this.policies = policies;
		this.records = records;
	}

	@Transactional(noRollbackFor = AiUsageLimitException.class)
	public Reservation begin(AiModel model, String operation, String input) {
		String username = currentUsername();
		int estimatedInput = estimateTokens(input);
		LocalDateTime now = LocalDateTime.now();
		for (AiUsagePolicy policy : policies.findApplicableForUpdate(model.getId(), username)) {
			String rejection = rejectionReason(policy, model, username, estimatedInput, now);
			if (rejection != null) {
				AiUsageRecord rejected = newRecord(model, username, operation, estimatedInput, "REJECTED");
				rejected.setErrorCode(rejectionCode(policy));
				records.saveAndFlush(rejected);
				throw new AiUsageLimitException(rejection);
			}
		}
		AiUsageRecord record = newRecord(model, username, operation, estimatedInput, "STARTED");
		records.saveAndFlush(record);
		return new Reservation(record.getId(), System.nanoTime(), estimatedInput);
	}

	@Transactional
	public void complete(Reservation reservation, AiModel model, ChatResponse response, String output) {
		if (reservation == null) {
			return;
		}
		AiUsageRecord record = records.findById(reservation.recordId()).orElse(null);
		if (record == null) {
			return;
		}
		Usage usage = response == null || response.getMetadata() == null
				? null
				: response.getMetadata().getUsage();
		int inputTokens = usage == null || usage.getPromptTokens() == null
				? reservation.estimatedInputTokens()
				: Math.max(0, usage.getPromptTokens());
		int outputTokens = usage == null || usage.getCompletionTokens() == null
				? estimateTokens(output)
				: Math.max(0, usage.getCompletionTokens());
		record.setInputTokens(inputTokens);
		record.setOutputTokens(outputTokens);
		record.setTotalTokens(inputTokens + outputTokens);
		record.setTokenEstimated(usage == null
				|| usage.getPromptTokens() == null
				|| usage.getCompletionTokens() == null);
		record.setCostAmount(calculateCost(model, inputTokens, outputTokens));
		record.setDurationMs(elapsedMillis(reservation.startedNanos()));
		record.setStatus("SUCCEEDED");
		record.setErrorCode(null);
		records.save(record);
	}

	@Transactional
	public void fail(Reservation reservation, RuntimeException exception) {
		if (reservation == null) {
			return;
		}
		records.findById(reservation.recordId()).ifPresent(record -> {
			record.setStatus("FAILED");
			record.setDurationMs(elapsedMillis(reservation.startedNanos()));
			record.setErrorCode(exception.getClass().getSimpleName());
			records.save(record);
		});
	}

	@Transactional(readOnly = true)
	public List<AiUsagePolicy> policies() {
		return policies.findAllByOrderByScopeTypeAscScopeKeyAsc();
	}

	@Transactional
	public AiUsagePolicy savePolicy(String id, AiUsagePolicy command) {
		String scopeType = normalizeScope(command.getScopeType());
		String scopeKey = normalizeScopeKey(scopeType, command.getScopeKey());
		AiUsagePolicy value = id == null
				? policies.findByScopeTypeAndScopeKey(scopeType, scopeKey).orElseGet(AiUsagePolicy::new)
				: policies.findById(id).orElseThrow(() -> new IllegalArgumentException("AI 用量策略不存在"));
		validatePositive(command.getRequestsPerMinute(), "每分钟请求上限");
		validatePositive(command.getDailyTokenLimit(), "每日 Token 上限");
		if (command.getMonthlyBudget() != null && command.getMonthlyBudget().signum() < 0) {
			throw new IllegalArgumentException("月度预算不能小于 0");
		}
		value.setScopeType(scopeType);
		value.setScopeKey(scopeKey);
		value.setRequestsPerMinute(command.getRequestsPerMinute());
		value.setDailyTokenLimit(command.getDailyTokenLimit());
		value.setMonthlyBudget(command.getMonthlyBudget());
		value.setEnabled(command.getEnabled() == null || command.getEnabled());
		return policies.save(value);
	}

	@Transactional(readOnly = true)
	public Page<AiUsageRecord> records(String modelId, String username, int page, int size) {
		return records.search(
				blankToNull(modelId),
				blankToNull(username),
				PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100)));
	}

	@Transactional(readOnly = true)
	public UsageSummary summary() {
		LocalDateTime today = LocalDate.now().atStartOfDay();
		return new UsageSummary(
				records.countSince(today),
				records.countRejectedSince(today),
				zero(records.sumTokensGlobal(today)),
				zero(records.sumCostGlobal(monthStart())));
	}

	private String rejectionReason(
			AiUsagePolicy policy,
			AiModel model,
			String username,
			int estimatedInput,
			LocalDateTime now) {
		long minuteRequests;
		long dailyTokens;
		BigDecimal monthlyCost;
		if ("MODEL".equals(policy.getScopeType())) {
			minuteRequests = records.countByModelIdAndCreateTimeGreaterThanEqual(model.getId(), now.minusMinutes(1));
			dailyTokens = zero(records.sumTokensByModel(model.getId(), LocalDate.now().atStartOfDay()));
			monthlyCost = zero(records.sumCostByModel(model.getId(), monthStart()));
		} else if ("USER".equals(policy.getScopeType())) {
			minuteRequests = records.countByUsernameAndCreateTimeGreaterThanEqual(username, now.minusMinutes(1));
			dailyTokens = zero(records.sumTokensByUser(username, LocalDate.now().atStartOfDay()));
			monthlyCost = zero(records.sumCostByUser(username, monthStart()));
		} else {
			minuteRequests = records.countSince(now.minusMinutes(1));
			dailyTokens = zero(records.sumTokensGlobal(LocalDate.now().atStartOfDay()));
			monthlyCost = zero(records.sumCostGlobal(monthStart()));
		}
		if (policy.getRequestsPerMinute() != null && minuteRequests >= policy.getRequestsPerMinute()) {
			return label(policy) + "每分钟请求上限已达到，请稍后重试";
		}
		if (policy.getDailyTokenLimit() != null
				&& dailyTokens + estimatedInput > policy.getDailyTokenLimit()) {
			return label(policy) + "每日 Token 预算已用尽";
		}
		BigDecimal inputReservation = calculateCost(model, estimatedInput, 0);
		if (policy.getMonthlyBudget() != null
				&& monthlyCost.add(inputReservation).compareTo(policy.getMonthlyBudget()) > 0) {
			return label(policy) + "月度费用预算已用尽";
		}
		return null;
	}

	private AiUsageRecord newRecord(
			AiModel model,
			String username,
			String operation,
			int estimatedInput,
			String status) {
		AiUsageRecord record = new AiUsageRecord();
		record.setRequestId(UUID.randomUUID().toString());
		record.setModelId(model.getId());
		record.setUsername(username);
		record.setOperation(operation == null || operation.isBlank() ? "CHAT" : operation);
		record.setStatus(status);
		record.setInputTokens(estimatedInput);
		record.setTotalTokens(estimatedInput);
		record.setTokenEstimated(true);
		record.setCostAmount(BigDecimal.ZERO);
		return record;
	}

	private BigDecimal calculateCost(AiModel model, int inputTokens, int outputTokens) {
		BigDecimal inputRate = zero(model.getInputCostPerMillion());
		BigDecimal outputRate = zero(model.getOutputCostPerMillion());
		return inputRate.multiply(BigDecimal.valueOf(inputTokens))
				.add(outputRate.multiply(BigDecimal.valueOf(outputTokens)))
				.divide(ONE_MILLION, 8, RoundingMode.HALF_UP);
	}

	private String currentUsername() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return authentication == null || authentication.getName() == null
				? "SYSTEM"
				: authentication.getName();
	}

	private int estimateTokens(String text) {
		if (text == null || text.isBlank()) {
			return 0;
		}
		return Math.max(1, (int) Math.ceil(text.codePointCount(0, text.length()) / 3.0));
	}

	private long elapsedMillis(long startNanos) {
		return Math.max(0, (System.nanoTime() - startNanos) / 1_000_000L);
	}

	private String label(AiUsagePolicy policy) {
		return switch (policy.getScopeType()) {
			case "MODEL" -> "模型";
			case "USER" -> "当前用户";
			default -> "平台";
		};
	}

	private String rejectionCode(AiUsagePolicy policy) {
		return "LIMIT_" + policy.getScopeType();
	}

	private String normalizeScope(String value) {
		String result = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
		if (!List.of("GLOBAL", "MODEL", "USER").contains(result)) {
			throw new IllegalArgumentException("策略范围只能是 GLOBAL、MODEL 或 USER");
		}
		return result;
	}

	private String normalizeScopeKey(String scopeType, String value) {
		if ("GLOBAL".equals(scopeType)) {
			return "*";
		}
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("模型或用户策略必须填写范围键");
		}
		return value.trim();
	}

	private void validatePositive(Number value, String label) {
		if (value != null && value.longValue() <= 0) {
			throw new IllegalArgumentException(label + "必须大于 0");
		}
	}

	private LocalDateTime monthStart() {
		return LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay();
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private long zero(Long value) {
		return value == null ? 0 : value;
	}

	private BigDecimal zero(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}

	public record Reservation(String recordId, long startedNanos, int estimatedInputTokens) {
	}

	public record UsageSummary(long requestsToday, long rejectedToday, long tokensToday, BigDecimal costThisMonth) {
	}
}
