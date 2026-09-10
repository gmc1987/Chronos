package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.ScheduleEntryRepository;
import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.education.scheduling.model.ScheduleEntry;
import com.chronos.workflow.WorkflowStartValidator;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 在调课流程创建前校验源课表、目标教室及调整类型，阻断伪造表单的跨校区操作。 */
@Component
public class CourseAdjustmentStartValidator implements WorkflowStartValidator {
	private static final String FLOW_CODE = "EDU_COURSE_ADJUSTMENT_APPROVAL";
	private static final java.util.Set<String> TYPES = java.util.Set.of(
			"MOVE",
			"CANCEL",
			"SUBSTITUTE",
			"MAKEUP");

	private final ScheduleEntryRepository entries;
	private final EducationDataScopeService dataScopes;

	public CourseAdjustmentStartValidator(
			ScheduleEntryRepository entries,
			EducationDataScopeService dataScopes) {
		this.entries = entries;
		this.dataScopes = dataScopes;
	}

	@Override
	public boolean supports(String flowCode) {
		return FLOW_CODE.equals(flowCode);
	}

	@Override
	public void validate(String actor, Map<String, Object> formData) {
		String entryId = required(formData, "scheduleEntryId", "原课表项不能为空");
		String type = required(formData, "adjustmentType", "调整类型不能为空").toUpperCase();
		if (!TYPES.contains(type)) {
			throw new IllegalArgumentException("不支持的调整类型：" + type);
		}

		EducationDataScope scope = dataScopes.resolve(actor);
		dataScopes.assertScheduleEntryAccess(scope, entryId);
		ScheduleEntry source = entries.findById(entryId)
				.orElseThrow(() -> new IllegalArgumentException("原课表项不存在"));

		if ("MOVE".equals(type) || "MAKEUP".equals(type)) {
			String targetClassroomId = text(formData.get("targetClassroomId"));
			dataScopes.assertClassroomAccess(
					scope,
					targetClassroomId == null ? source.getClassroomId() : targetClassroomId);
		}
		if ("SUBSTITUTE".equals(type)) {
			required(formData, "substituteTeacherId", "代课教师不能为空");
		}
	}

	private String required(Map<String, Object> formData, String key, String message) {
		String value = text(formData.get(key));
		if (value == null) {
			throw new IllegalArgumentException(message);
		}
		return value;
	}

	private String text(Object value) {
		if (value == null || String.valueOf(value).isBlank()) {
			return null;
		}
		return String.valueOf(value).trim();
	}
}
