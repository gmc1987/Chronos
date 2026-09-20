package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.EducationUserBindingRepository;
import com.chronos.education.scheduling.model.Preparation;
import com.chronos.education.scheduling.model.ResearchActivity;
import com.chronos.education.scheduling.model.ResearchResult;
import com.chronos.workflow.WorkflowNotificationService;
import java.util.Collection;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** 备课与教研消息统一写入平台 Outbox，业务事务失败时不会留下幽灵通知。 */
@Service
public class TeachingCollaborationNotificationService {
	private final EducationUserBindingRepository bindings;
	private final WorkflowNotificationService notifications;

	public TeachingCollaborationNotificationService(
			EducationUserBindingRepository bindings,
			WorkflowNotificationService notifications) {
		this.bindings = bindings;
		this.notifications = notifications;
	}

	public void preparationInvited(Preparation preparation, String teacherId) {
		notifyTeacher(
				teacherId,
				"EDUCATION_PREPARATION_INVITED",
				preparation.getId(),
				"集体备课邀请：" + preparation.getTitle(),
				"请进入教学中心查看议程并确认是否参加。",
				"INVITED:" + teacherId);
	}

	public void researchInvited(ResearchActivity activity, String teacherId) {
		notifyTeacher(
				teacherId,
				"EDUCATION_RESEARCH_INVITED",
				activity.getId(),
				"教研活动邀请：" + activity.getTitle(),
				String.valueOf(activity.getActivityTime()) + " " + Objects.toString(activity.getLocation(), ""),
				"INVITED:" + teacherId);
	}

	public void researchCancelled(ResearchActivity activity, Collection<String> teacherIds) {
		for (String teacherId : teacherIds) {
			notifyTeacher(
					teacherId,
					"EDUCATION_RESEARCH_CANCELLED",
					activity.getId(),
					"教研活动已取消：" + activity.getTitle(),
					Objects.toString(activity.getCancelReason(), ""),
					"CANCELLED:" + teacherId);
		}
	}

	public void researchResultReviewed(
			ResearchResult result,
			String activityTitle,
			Collection<String> teacherIds,
			boolean published,
			String comment) {
		String decision = published ? "审核通过" : "审核退回";
		for (String teacherId : teacherIds) {
			notifyTeacher(
					teacherId,
					published
							? "EDUCATION_RESEARCH_RESULT_PUBLISHED"
							: "EDUCATION_RESEARCH_RESULT_REJECTED",
					result.getId(),
					"教研成果" + decision + "：" + result.getTitle(),
					activityTitle + (comment == null || comment.isBlank() ? "" : "；审核意见：" + comment),
					decision + ":" + teacherId + ":" + result.getRowVersion());
		}
	}

	private void notifyTeacher(
			String teacherId,
			String eventType,
			String aggregateId,
			String title,
			String content,
			String occurrence) {
		bindings.findByProfileTypeAndProfileId("TEACHER", teacherId)
				.filter(value -> "ACTIVE".equals(value.getStatus()))
				.ifPresent(value -> notifications.enqueueUserEvent(
						eventType,
						aggregateId,
						value.getUsername(),
						title,
						content,
						occurrence));
	}
}
