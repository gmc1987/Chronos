package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.dao.SchedulePolicyRepository;
import com.chronos.education.scheduling.model.SchedulePolicy;
import com.chronos.service.iService.IAuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 提供排课策略的安全默认值、参数校验和审计保存。 */
@Service
public class SchedulePolicyService {
	private final SchedulePolicyRepository policies;
	private final AcademicTermRepository terms;
	private final IAuditLogService audit;

	public SchedulePolicyService(
			SchedulePolicyRepository policies,
			AcademicTermRepository terms,
			IAuditLogService audit) {
		this.policies = policies;
		this.terms = terms;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public SchedulePolicy resolve(String semesterCode) {
		return policies.findBySemesterCode(required(semesterCode))
				.orElseGet(() -> defaults(semesterCode.trim()));
	}

	@Transactional
	public SchedulePolicy save(SchedulePolicy input, String actor) {
		String semester = required(input.getSemesterCode());
		terms.findByTermCode(semester)
				.orElseThrow(() -> new IllegalArgumentException("学期不存在"));
		validate(input);
		SchedulePolicy target = policies.findBySemesterCode(semester)
				.orElseGet(SchedulePolicy::new);
		copy(input, target);
		target.setSemesterCode(semester);
		SchedulePolicy saved = policies.save(target);
		audit.log(actor, "EDUCATION_SCHEDULE_POLICY_SAVE", "semester=" + semester);
		return saved;
	}

	private SchedulePolicy defaults(String semesterCode) {
		SchedulePolicy policy = new SchedulePolicy();
		policy.setSemesterCode(semesterCode);
		return policy;
	}

	private void copy(SchedulePolicy source, SchedulePolicy target) {
		target.setDefaultMaxWeeklyLessons(source.getDefaultMaxWeeklyLessons());
		target.setDefaultMaxDailyLessons(source.getDefaultMaxDailyLessons());
		target.setDefaultMaxConsecutiveLessons(source.getDefaultMaxConsecutiveLessons());
		target.setScheduledLessonReward(source.getScheduledLessonReward());
		target.setPreferredSlotReward(source.getPreferredSlotReward());
		target.setSameCourseDayPenalty(source.getSameCourseDayPenalty());
		target.setTeacherLoadPenalty(source.getTeacherLoadPenalty());
		target.setConsecutivePenalty(source.getConsecutivePenalty());
		target.setCampusSwitchPenalty(source.getCampusSwitchPenalty());
		target.setTeacherGapPenalty(source.getTeacherGapPenalty());
		target.setMinimumCampusTravelPeriods(source.getMinimumCampusTravelPeriods());
		target.setUnscheduledLessonPenalty(source.getUnscheduledLessonPenalty());
		target.setCourseConcentrationThreshold(source.getCourseConcentrationThreshold());
		target.setBlockTeacherOverload(source.getBlockTeacherOverload());
		target.setBlockHardConflicts(source.getBlockHardConflicts());
		target.setBlockIncompleteOfferings(source.getBlockIncompleteOfferings());
	}

	private void validate(SchedulePolicy policy) {
		requireRange(policy.getDefaultMaxWeeklyLessons(), 1, 100, "默认教师周课时上限");
		requireRange(policy.getDefaultMaxDailyLessons(), 1, 20, "默认教师日课时上限");
		requireRange(policy.getDefaultMaxConsecutiveLessons(), 1, 10, "默认教师连续授课上限");
		requireRange(policy.getCourseConcentrationThreshold(), 1, 20, "课程集中阈值");
		requireNonNegative(policy.getScheduledLessonReward(), "已排课时奖励");
		requireNonNegative(policy.getPreferredSlotReward(), "偏好时段奖励");
		requireNonNegative(policy.getSameCourseDayPenalty(), "同课程同日惩罚");
		requireNonNegative(policy.getTeacherLoadPenalty(), "教师负载惩罚");
		requireNonNegative(policy.getConsecutivePenalty(), "连续授课惩罚");
		requireNonNegative(policy.getCampusSwitchPenalty(), "跨校区惩罚");
		requireNonNegative(policy.getTeacherGapPenalty(), "教师空档惩罚");
		requireRange(policy.getMinimumCampusTravelPeriods(), 0, 10, "跨校区通勤间隔");
		requireNonNegative(policy.getUnscheduledLessonPenalty(), "未排课时惩罚");
		if (policy.getBlockTeacherOverload() == null) {
			throw new IllegalArgumentException("发布门禁开关不能为空");
		}
		if (policy.getBlockHardConflicts() == null
				|| policy.getBlockIncompleteOfferings() == null) {
			throw new IllegalArgumentException("发布门禁开关不能为空");
		}
	}

	private void requireRange(Integer value, int minimum, int maximum, String label) {
		if (value == null || value < minimum || value > maximum) {
			throw new IllegalArgumentException(label + "必须在 " + minimum + " 到 " + maximum + " 之间");
		}
	}

	private void requireNonNegative(Integer value, String label) {
		if (value == null || value < 0 || value > 100000) {
			throw new IllegalArgumentException(label + "必须在 0 到 100000 之间");
		}
	}

	private String required(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("学期编码不能为空");
		}
		return value.trim();
	}
}
