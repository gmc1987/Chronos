package com.chronos.service.impl;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.chronos.Idao.IBusinessNumberRuleRepository;
import com.chronos.Idao.IOrganizationRepository;
import com.chronos.model.pojo.BusinessNumberRule;
import jakarta.persistence.EntityManager;

/** 统一生成校内工号和学号；数据库原子分配流水，多个应用实例并发时也不会重号。 */
@Service
public class BusinessNumberService {
	public static final String EMPLOYEE = "EMPLOYEE";
	public static final String STUDENT = "STUDENT";
	private static final Set<String> BUSINESS_TYPES = Set.of(EMPLOYEE, STUDENT);
	private static final Set<String> RESET_PERIODS = Set.of("NEVER", "YEAR");
	private final IBusinessNumberRuleRepository rules;
	private final IOrganizationRepository organizations;
	private final EntityManager entityManager;

	public BusinessNumberService(IBusinessNumberRuleRepository rules,
			IOrganizationRepository organizations, EntityManager entityManager) {
		this.rules = rules;
		this.organizations = organizations;
		this.entityManager = entityManager;
	}

	public List<BusinessNumberRule> rules() {
		return rules.findAllByOrderByBusinessTypeAscOrganizationIdAsc();
	}

	@Transactional
	public BusinessNumberRule saveRule(BusinessNumberRule command) {
		String businessType = normalize(command.getBusinessType());
		String organizationId = normalizeScope(command.getOrganizationId());
		if (!BUSINESS_TYPES.contains(businessType)) throw new IllegalArgumentException("编号业务类型只支持 EMPLOYEE 或 STUDENT");
		if (!"GLOBAL".equals(organizationId)) organizations.findById(organizationId)
				.orElseThrow(() -> new IllegalArgumentException("编号规则所属学校不存在"));
		String resetPeriod = normalize(command.getResetPeriod());
		if (!RESET_PERIODS.contains(resetPeriod)) throw new IllegalArgumentException("流水重置周期只支持 YEAR 或 NEVER");
		int sequenceLength = command.getSequenceLength() == null ? 6 : command.getSequenceLength();
		if (sequenceLength < 3 || sequenceLength > 12) throw new IllegalArgumentException("流水号长度必须在3到12位之间");
		String pattern = command.getPattern() == null ? "" : command.getPattern().trim().toUpperCase(Locale.ROOT);
		if (!pattern.contains("{SEQ}")) throw new IllegalArgumentException("编号规则必须包含 {SEQ}");
		if (STUDENT.equals(businessType) && (!pattern.contains("{YEAR}") || !pattern.contains("{LEVEL}")))
			throw new IllegalArgumentException("学号规则必须包含 {YEAR}、{LEVEL} 和 {SEQ}");
		if (EMPLOYEE.equals(businessType) && !pattern.contains("{YEAR}"))
			throw new IllegalArgumentException("工号规则必须包含 {YEAR} 和 {SEQ}");
		if (!pattern.replace("{YEAR}", "").replace("{LEVEL}", "").replace("{SEQ}", "").matches("[0-9A-Z_-]*"))
			throw new IllegalArgumentException("编号规则包含不支持的字符或变量");
		BusinessNumberRule value = rules.findByBusinessTypeAndOrganizationId(businessType, organizationId)
				.orElseGet(BusinessNumberRule::new);
		value.setBusinessType(businessType); value.setOrganizationId(organizationId); value.setPattern(pattern);
		value.setSequenceLength(sequenceLength); value.setResetPeriod(resetPeriod);
		value.setEnabled(command.getEnabled() == null || command.getEnabled()); value.setDescription(command.getDescription());
		return rules.save(value);
	}

	@Transactional
	public String nextEmployeeNumber(String organizationId, LocalDate hireDate) {
		LocalDate date = hireDate == null ? LocalDate.now() : hireDate;
		return next(EMPLOYEE, organizationId, date.getYear(), "");
	}

	@Transactional
	public String nextStudentNumber(String organizationId, int admissionYear, String educationLevel) {
		if (admissionYear < 2000 || admissionYear > LocalDate.now().getYear() + 2) throw new IllegalArgumentException("入学年份不正确");
		return next(STUDENT, organizationId, admissionYear, normalizeEducationLevel(educationLevel));
	}

	private String next(String businessType, String organizationId, int year, String educationLevel) {
		BusinessNumberRule rule = resolveRule(businessType, organizationId);
		if (!Boolean.TRUE.equals(rule.getEnabled())) throw new IllegalStateException("当前学校的编号规则已停用");
		String scopeKey = "YEAR".equals(rule.getResetPeriod()) ? Integer.toString(year) : "ALL";
		Number allocated = (Number) entityManager.createNativeQuery("""
			INSERT INTO t_business_number_sequence (rule_id, scope_key, current_value, last_update_time)
			VALUES (:ruleId, :scopeKey, 1, CURRENT_TIMESTAMP)
			ON CONFLICT (rule_id, scope_key)
			DO UPDATE SET current_value = t_business_number_sequence.current_value + 1,
			              last_update_time = CURRENT_TIMESTAMP
			RETURNING current_value
			""").setParameter("ruleId", rule.getId()).setParameter("scopeKey", scopeKey).getSingleResult();
		long sequence = allocated.longValue();
		long maximum = powerOfTen(rule.getSequenceLength()) - 1;
		if (sequence > maximum) throw new IllegalStateException("编号流水已超过规则允许的最大值，请扩展流水号长度");
		String padded = String.format(Locale.ROOT, "%0" + rule.getSequenceLength() + "d", sequence);
		return rule.getPattern().replace("{YEAR}", Integer.toString(year)).replace("{LEVEL}", educationLevel).replace("{SEQ}", padded);
	}

	private BusinessNumberRule resolveRule(String businessType, String organizationId) {
		return rules.findByBusinessTypeAndOrganizationId(businessType, normalizeScope(organizationId))
				.or(() -> rules.findByBusinessTypeAndOrganizationId(businessType, "GLOBAL"))
				.orElseThrow(() -> new IllegalStateException("系统尚未配置" + businessType + "编号规则"));
	}

	private long powerOfTen(int exponent) { long value = 1; for (int i = 0; i < exponent; i++) value *= 10; return value; }
	private String normalizeEducationLevel(String value) {
		String normalized = normalize(value);
		return Map.ofEntries(Map.entry("UNDERGRADUATE", "01"), Map.entry("VOCATIONAL_UNDERGRADUATE", "02"),
				Map.entry("HIGHER_EDUCATION", "03"), Map.entry("JUNIOR_COLLEGE", "03"), Map.entry("HIGHER_VOCATIONAL", "03"),
				Map.entry("VOCATIONAL", "04"), Map.entry("SENIOR_HIGH", "05"), Map.entry("JUNIOR_HIGH", "06"), Map.entry("PRIMARY", "07"))
				.getOrDefault(normalized, normalized.matches("\\d{2}") ? normalized : "99");
	}
	private String normalizeScope(String value) { return value == null || value.isBlank() ? "GLOBAL" : value.trim(); }
	private String normalize(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT); }
}
