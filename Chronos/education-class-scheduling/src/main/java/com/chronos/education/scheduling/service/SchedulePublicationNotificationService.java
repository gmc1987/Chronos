package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.EducationUserBindingRepository;
import com.chronos.education.scheduling.dao.TeachingClassMemberRepository;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.EducationUserBinding;
import com.chronos.education.scheduling.model.ScheduleEntry;
import com.chronos.education.scheduling.model.SchedulePlanVersion;
import com.chronos.education.scheduling.model.TeachingClassMember;
import com.chronos.workflow.WorkflowNotificationService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/** 将课表发布和回滚结果可靠通知到受影响教师、学生的门户账号。 */
@Service
public class SchedulePublicationNotificationService {
	private final CourseOfferingRepository offerings;
	private final TeachingClassMemberRepository members;
	private final EducationUserBindingRepository bindings;
	private final WorkflowNotificationService notifications;

	public SchedulePublicationNotificationService(
			CourseOfferingRepository offerings,
			TeachingClassMemberRepository members,
			EducationUserBindingRepository bindings,
			WorkflowNotificationService notifications) {
		this.offerings = offerings;
		this.members = members;
		this.bindings = bindings;
		this.notifications = notifications;
	}

	public void enqueue(
			SchedulePlanVersion version,
			List<ScheduleEntry> snapshot,
			boolean rollback) {
		Set<String> offeringIds = snapshot.stream()
				.map(ScheduleEntry::getOfferingId)
				.filter(this::hasText)
				.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
		if (offeringIds.isEmpty()) {
			return;
		}

		List<CourseOffering> affectedOfferings = offerings.findAllById(offeringIds);
		Set<String> teacherIds = affectedOfferings.stream()
				.map(CourseOffering::getTeacherId)
				.filter(this::hasText)
				.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
		Set<String> studentIds = members.findByOfferingIdInAndEnrollmentStatus(
				List.copyOf(offeringIds),
				"ENROLLED").stream()
				.map(TeachingClassMember::getStudentId)
				.filter(this::hasText)
				.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

		Set<String> recipients = new LinkedHashSet<>();
		recipients.addAll(usernames("TEACHER", teacherIds));
		recipients.addAll(usernames("STUDENT", studentIds));
		String action = rollback ? "回滚并重新发布" : "发布";
		String eventType = rollback ? "EDUCATION_SCHEDULE_ROLLBACK" : "EDUCATION_SCHEDULE_PUBLISHED";
		String title = "课表已" + action;
		String content = version.getSemesterCode()
				+ " 学期课表已"
				+ action
				+ "，当前版本 V"
				+ version.getVersionNo()
				+ "，请及时查看个人课表。";
		for (String recipient : recipients) {
			notifications.enqueueUserEvent(
					eventType,
					version.getId(),
					recipient,
					title,
					content,
					"V" + version.getVersionNo());
		}
	}

	private Set<String> usernames(String profileType, Set<String> profileIds) {
		if (profileIds.isEmpty()) {
			return Set.of();
		}
		return bindings.findByProfileTypeAndProfileIdInAndStatus(
				profileType,
				List.copyOf(profileIds),
				"ACTIVE").stream()
				.map(EducationUserBinding::getUsername)
				.filter(this::hasText)
				.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
