package com.chronos.service.impl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.Idao.IEmployeeAssignmentRepository;
import com.chronos.Idao.IEmployeeRepository;
import com.chronos.Idao.IJobLevelRepository;
import com.chronos.Idao.IOrganizationRepository;
import com.chronos.Idao.IOrganizationUnitRepository;
import com.chronos.Idao.IPersonExternalIdentityRepository;
import com.chronos.Idao.IPositionRepository;
import com.chronos.model.pojo.Employee;
import com.chronos.model.pojo.EmployeeAssignment;
import com.chronos.model.pojo.PersonExternalIdentity;

@Service
public class EmployeeImportService {
	private final IOrganizationRepository organizations;
	private final IOrganizationUnitRepository units;
	private final IPositionRepository positions;
	private final IJobLevelRepository levels;
	private final IEmployeeRepository employees;
	private final IEmployeeAssignmentRepository assignments;
	private final IPersonExternalIdentityRepository identities;
	private final BusinessNumberService businessNumbers;
	private final StaffAccountProvisioningService accountProvisioning;

	public EmployeeImportService(IOrganizationRepository organizations,
			IOrganizationUnitRepository units, IPositionRepository positions,
			IJobLevelRepository levels, IEmployeeRepository employees,
			IEmployeeAssignmentRepository assignments,
			IPersonExternalIdentityRepository identities,
			BusinessNumberService businessNumbers,
			StaffAccountProvisioningService accountProvisioning) {
		this.organizations = organizations;
		this.units = units;
		this.positions = positions;
		this.levels = levels;
		this.employees = employees;
		this.assignments = assignments;
		this.identities = identities;
		this.businessNumbers = businessNumbers;
		this.accountProvisioning = accountProvisioning;
	}

	@Transactional
	public Map<String, Object> importRows(List<Map<String, String>> rows, ImportOptions options) {
		List<String> errors = new ArrayList<>();
		Map<String, String> fileKeySignatures = new LinkedHashMap<>();
		List<ImportRow> values = new ArrayList<>();
		int line = 1;
		for (Map<String, String> row : rows) {
			line++;
			String externalKey = value(row, "导入标识*");
			String employeeCode = first(row, "工号", "工号*");
			String employeeName = first(row, "姓名*", "姓名");
			String organizationCode = value(row, "机构编码");
			LocalDate hireDate = date(row, "入职日期", line, errors);
			LocalDate leaveDate = date(row, "离职日期", line, errors);
			LocalDate effectiveFrom = date(row, "任职生效日期", line, errors);
			LocalDate effectiveTo = date(row, "任职失效日期", line, errors);
			if (employeeName.isBlank()) errors.add(error(line, "姓名不能为空"));
			if (externalKey.isBlank() && employeeCode.isBlank()) {
				errors.add(error(line, "导入标识和工号至少填写一个"));
			}
			if (!externalKey.isBlank()) {
				String scopedKey = organizationCode + "|" + externalKey;
				String signature = employeeCode + "|" + employeeName;
				String previous = fileKeySignatures.putIfAbsent(scopedKey, signature);
				if (previous != null && !previous.equals(signature)) {
					errors.add(error(line, "相同导入标识的工号或姓名不一致：" + externalKey));
				}
			}
			if (organizationCode.isBlank()) errors.add(error(line, "机构编码不能为空"));
			if (employeeCode.isBlank() && hireDate == null) {
				errors.add(error(line, "系统生成工号时必须填写入职日期"));
			}
			if (effectiveFrom != null && effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
				errors.add(error(line, "任职失效日期不能早于生效日期"));
			}
			values.add(new ImportRow(line, row, externalKey, employeeCode, employeeName,
					organizationCode, hireDate, leaveDate, effectiveFrom, effectiveTo));
		}
		fail(errors);

		Set<String> createdEmployeeIds = new LinkedHashSet<>();
		Set<String> updatedEmployeeIds = new LinkedHashSet<>();
		int assignmentCreated = 0;
		int assignmentUpdated = 0;
		Map<String, Employee> affectedEmployees = new LinkedHashMap<>();
		for (ImportRow row : values) {
			var organization = organizations.findByOrgCode(row.organizationCode());
			if (organization == null) throw new IllegalArgumentException(error(row.line(),
					"机构编码不存在：" + row.organizationCode()));
			PersonExternalIdentity identity = row.externalKey().isBlank() ? null
					: identities.findByOrganizationIdAndPersonTypeAndSourceSystemAndExternalPersonKey(
							organization.getId(), "EMPLOYEE", options.sourceSystem(), row.externalKey()).orElse(null);
			Employee employee = identity == null ? null : employees.findById(identity.getPersonId())
					.orElseThrow(() -> new IllegalArgumentException(error(row.line(), "导入标识绑定的员工不存在")));
			if (employee == null && !row.employeeCode().isBlank()) {
				employee = employees.findByEmployeeCode(row.employeeCode()).orElse(null);
			}
			boolean creating = employee == null;
			if (creating) employee = new Employee();
			if (!creating && !row.employeeCode().isBlank()
					&& !row.employeeCode().equals(employee.getEmployeeCode())) {
				throw new IllegalArgumentException(error(row.line(), "导入标识与工号指向不同员工"));
			}
			if (creating) {
				String code = row.employeeCode().isBlank()
						? businessNumbers.nextEmployeeNumber(organization.getId(), row.hireDate())
						: row.employeeCode();
				employees.findByEmployeeCode(code).ifPresent(existing -> {
					throw new IllegalArgumentException(error(row.line(), "工号已存在：" + code));
				});
				employee.setEmployeeCode(code);
			}
			applyEmployee(employee, row);
			employee = employees.save(employee);
			if (creating) createdEmployeeIds.add(employee.getId());
			else if (!createdEmployeeIds.contains(employee.getId())) updatedEmployeeIds.add(employee.getId());
			if (!row.externalKey().isBlank() && identity == null) {
				identity = new PersonExternalIdentity();
				identity.setOrganizationId(organization.getId());
				identity.setPersonType("EMPLOYEE");
				identity.setPersonId(employee.getId());
				identity.setSourceSystem(options.sourceSystem());
				identity.setExternalPersonKey(row.externalKey());
				identities.save(identity);
			}

			String departmentCode = value(row.data(), "部门编码");
			String positionCode = value(row.data(), "岗位编码");
			if (!departmentCode.isBlank() || !positionCode.isBlank()) {
				if (departmentCode.isBlank() || positionCode.isBlank()) {
					throw new IllegalArgumentException(error(row.line(), "部门编码和岗位编码必须同时填写"));
				}
				var unit = units.findByOrgIdAndOrganizationUnitCode(organization.getId(), departmentCode)
						.orElseThrow(() -> new IllegalArgumentException(error(row.line(),
								"部门编码不存在：" + departmentCode)));
				var position = positions.findByPositionCode(positionCode);
				if (position == null) throw new IllegalArgumentException(error(row.line(),
						"岗位编码不存在：" + positionCode));
				EmployeeAssignment assignment = assignments
						.findFirstByEmployeeIdAndOrganizationIdAndOrganizationUnitIdAndPositionId(
								employee.getId(), organization.getId(), unit.getId(), position.getId())
						.orElse(null);
				boolean newAssignment = assignment == null;
				if (newAssignment) assignment = new EmployeeAssignment();
				assignment.setEmployeeId(employee.getId());
				assignment.setOrganizationId(organization.getId());
				assignment.setOrganizationUnitId(unit.getId());
				assignment.setPositionId(position.getId());
				String levelCode = value(row.data(), "职级编码");
				assignment.setJobLevelId(levelCode.isBlank() ? null : levels.findByLevelCode(levelCode)
						.orElseThrow(() -> new IllegalArgumentException(error(row.line(),
								"职级编码不存在：" + levelCode))).getId());
				assignment.setPrimaryAssignment(bool(value(row.data(), "主任职")));
				assignment.setDepartmentLeader(bool(value(row.data(), "部门负责人")));
				assignment.setEffectiveFrom(row.effectiveFrom());
				assignment.setEffectiveTo(row.effectiveTo());
				assignment.setStatus(integer(row.data(), "任职状态", 1, row.line()));
				if (Boolean.TRUE.equals(assignment.getPrimaryAssignment())) {
					String assignmentId = assignment.getId();
					assignments.findFirstByEmployeeIdAndPrimaryAssignmentTrueAndStatus(employee.getId(), 1)
							.filter(old -> !old.getId().equals(assignmentId)).ifPresent(old -> {
								old.setPrimaryAssignment(false);
								assignments.save(old);
							});
				}
				assignments.save(assignment);
				if (newAssignment) assignmentCreated++; else assignmentUpdated++;
			}
			affectedEmployees.put(employee.getId(), employee);
		}

		List<StaffAccountProvisioningService.AccountSyncResult> accountResults = new ArrayList<>();
		List<String> warnings = new ArrayList<>();
		if (options.syncAccounts()) {
			for (Employee employee : affectedEmployees.values()) {
				var result = accountProvisioning.sync(employee, options.syncRoles(),
						options.disableInactiveAccounts(), options.dryRun());
				accountResults.add(result);
				for (String warning : result.warnings()) {
					warnings.add(employee.getEmployeeCode() + "：" + warning);
				}
			}
		}
		long accountsCreated = accountResults.stream().filter(
				StaffAccountProvisioningService.AccountSyncResult::created).count();
		return Map.of(
				"successCount", values.size(),
				"employees", Map.of("created", createdEmployeeIds.size(), "updated", updatedEmployeeIds.size()),
				"assignments", Map.of("created", assignmentCreated, "updated", assignmentUpdated),
				"accounts", Map.of("created", accountsCreated,
						"updated", accountResults.size() - accountsCreated),
				"accountResults", accountResults,
				"warnings", warnings);
	}

	private void applyEmployee(Employee employee, ImportRow row) {
		Map<String, String> data = row.data();
		employee.setEmployeeName(row.employeeName());
		employee.setGender(defaultValue(value(data, "性别"), "UNSPECIFIED"));
		employee.setPhone(value(data, "手机号"));
		employee.setEmail(value(data, "邮箱"));
		employee.setEmployeeType(defaultValue(value(data, "员工类型"), "STAFF"));
		employee.setEmploymentStatus(defaultValue(value(data, "在职状态"), "ACTIVE"));
		employee.setHireDate(row.hireDate());
		employee.setLeaveDate(row.leaveDate());
	}

	private LocalDate date(Map<String, String> row, String key, int line, List<String> errors) {
		String value = value(row, key);
		if (value.isBlank()) return null;
		try {
			return LocalDate.parse(value);
		} catch (Exception exception) {
			errors.add(error(line, key + "格式必须为yyyy-MM-dd"));
			return null;
		}
	}

	private int integer(Map<String, String> row, String key, int fallback, int line) {
		String value = value(row, key);
		if (value.isBlank()) return fallback;
		try {
			return Integer.parseInt(value.replace(".0", ""));
		} catch (Exception exception) {
			throw new IllegalArgumentException(error(line, key + "必须是整数"));
		}
	}

	private boolean bool(String value) {
		return "是".equals(value) || "Y".equalsIgnoreCase(value)
				|| "TRUE".equalsIgnoreCase(value) || "1".equals(value);
	}

	private String first(Map<String, String> row, String... keys) {
		for (String key : keys) {
			String value = value(row, key);
			if (!value.isBlank()) return value;
		}
		return "";
	}

	private String value(Map<String, String> row, String key) {
		return row.getOrDefault(key, "").trim();
	}

	private String defaultValue(String value, String fallback) {
		return value.isBlank() ? fallback : value;
	}

	private String error(int line, String message) {
		return "第" + line + "行：" + message;
	}

	private void fail(List<String> errors) {
		if (!errors.isEmpty()) throw new IllegalArgumentException("导入校验失败：" + String.join("；", errors));
	}

	public record ImportOptions(boolean syncAccounts, boolean syncRoles,
			boolean disableInactiveAccounts, String sourceSystem, boolean dryRun) {
		public ImportOptions {
			sourceSystem = sourceSystem == null || sourceSystem.isBlank()
					? "EXCEL" : sourceSystem.trim().toUpperCase(java.util.Locale.ROOT);
		}
	}

	private record ImportRow(int line, Map<String, String> data, String externalKey,
			String employeeCode, String employeeName, String organizationCode,
			LocalDate hireDate, LocalDate leaveDate, LocalDate effectiveFrom,
			LocalDate effectiveTo) {}
}
