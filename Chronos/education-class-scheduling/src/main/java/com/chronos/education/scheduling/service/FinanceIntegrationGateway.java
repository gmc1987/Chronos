package com.chronos.education.scheduling.service;

import java.math.BigDecimal;

/**
 * 财务模块防腐层。未来财务模块或外部财务适配器只需实现该接口，
 * 出差业务不得直接依赖财务数据表。
 */
public interface FinanceIntegrationGateway {
	String providerCode();
	boolean available();
	BudgetAvailability queryAvailableBudget(BudgetRequest request);
	FinanceOperationResult precheck(BudgetRequest request);
	FinanceOperationResult reserve(BudgetRequest request);
	FinanceOperationResult release(String businessKey, String reservationReference, String reason);
	FinanceOperationResult writeOff(
			String businessKey, String reservationReference, BigDecimal actualAmount, String currency);
	FinanceOperationResult linkReimbursement(
			String businessKey, String reimbursementReference, BigDecimal amount, String currency);
	FinanceSettlementStatus settlementStatus(String businessKey);

	record BudgetRequest(
			String businessKey,
			String employeeId,
			String budgetProjectCode,
			String costCenterCode,
			BigDecimal amount,
			String currency) {}
	record BudgetAvailability(boolean available, BigDecimal remainingAmount, String currency, String message) {}
	record FinanceOperationResult(boolean success, String reference, String message) {}
	record FinanceSettlementStatus(String status, String reference, String message) {}
}
