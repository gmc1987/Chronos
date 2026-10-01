package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.workflow.WorkflowStartValidator;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 教室申请发起前的权限和资源快照校验。 */
@Component
public class ClassroomReservationStartValidator implements WorkflowStartValidator {
	public static final String FLOW_CODE = "EDU_CLASSROOM_RESERVATION_APPROVAL";

	private final EducationDataScopeService dataScopes;
	private final ClassroomReservationConflictService conflicts;

	public ClassroomReservationStartValidator(
			EducationDataScopeService dataScopes,
			ClassroomReservationConflictService conflicts) {
		this.dataScopes = dataScopes;
		this.conflicts = conflicts;
	}

	@Override
	public boolean supports(String flowCode) {
		return FLOW_CODE.equals(flowCode);
	}

	@Override
	public void validate(String actor, Map<String, Object> formData) {
		String semesterCode = required(formData, "semesterCode", "学期不能为空");
		String classroomId = required(formData, "classroomId", "教室不能为空");
		LocalDate usageDate = date(formData, "usageDate", "使用日期格式应为 yyyy-MM-dd");
		int startPeriod = positiveInteger(formData, "startPeriod", "开始节次必须是正整数");
		int durationPeriods = positiveInteger(formData, "durationPeriods", "持续节数必须是正整数");
		int attendeeCount = positiveInteger(formData, "attendeeCount", "使用人数必须是正整数");
		required(formData, "purpose", "用途不能为空");

		EducationDataScope scope = dataScopes.resolve(actor);
		dataScopes.assertClassroomAccess(scope, classroomId);
		conflicts.assertAvailable(
				semesterCode,
				classroomId,
				usageDate,
				startPeriod,
				durationPeriods,
				attendeeCount,
				null);
	}

	private LocalDate date(Map<String, Object> formData, String key, String message) {
		try {
			return LocalDate.parse(required(formData, key, message));
		} catch (RuntimeException exception) {
			throw new IllegalArgumentException(message);
		}
	}

	private int positiveInteger(Map<String, Object> formData, String key, String message) {
		try {
			int value = Integer.parseInt(required(formData, key, message));
			if (value > 0) {
				return value;
			}
		} catch (NumberFormatException ignored) {
			// 统一由下方业务异常返回稳定的中文提示。
		}
		throw new IllegalArgumentException(message);
	}

	private String required(Map<String, Object> formData, String key, String message) {
		Object value = formData.get(key);
		if (value == null || String.valueOf(value).isBlank()) {
			throw new IllegalArgumentException(message);
		}
		return String.valueOf(value).trim();
	}
}
