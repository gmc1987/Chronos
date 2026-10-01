package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.chronos.education.scheduling.dao.*;
import com.chronos.education.scheduling.model.*;
import com.chronos.education.scheduling.model.dto.ResearchErrorDtos.*;
import com.chronos.file.dao.ManagedFileRepository;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

class ResearchErrorServiceFlowTest {
	private ResearchGroupRepository groups;
	private ResearchGroupMemberRepository groupMembers;
	private ResearchActivityRepository activities;
	private ResearchActivityMemberRepository activityMembers;
	private ResearchMaterialRepository materials;
	private ResearchResultRepository results;
	private TeachingReviewService reviews;
	private EducationDataScopeService scopes;
	private Authentication auth;
	private ResearchErrorService service;

	@BeforeEach
	void setUp() {
		groups = mock(ResearchGroupRepository.class);
		groupMembers = mock(ResearchGroupMemberRepository.class);
		activities = mock(ResearchActivityRepository.class);
		activityMembers = mock(ResearchActivityMemberRepository.class);
		materials = mock(ResearchMaterialRepository.class);
		results = mock(ResearchResultRepository.class);
		reviews = mock(TeachingReviewService.class);
		scopes = mock(EducationDataScopeService.class);
		auth = mock(Authentication.class);
		when(auth.getName()).thenReturn("teacher-1");
		when(scopes.resolve("teacher-1")).thenReturn(new EducationDataScope(
				true, Set.of(), Set.of(), Set.of(), Set.of(), Set.of()));
		service = new ResearchErrorService(groups, groupMembers, activities, activityMembers,
				materials, results, mock(ErrorBookRepository.class), mock(ErrorItemRepository.class),
				scopes, mock(ManagedFileRepository.class), reviews,
				mock(QuestionRepository.class), mock(KnowledgePointRepository.class),
				mock(QuestionKnowledgePointRepository.class), mock(QuestionBankRepository.class));
	}

	@Test
	void maintainsGroupMembersAndRejectsDuplicate() {
		ResearchGroup group = new ResearchGroup();
		group.setId("group-1");
		when(groups.findById("group-1")).thenReturn(Optional.of(group));
		when(groupMembers.existsByGroupIdAndTeacherId("group-1", "teacher-2")).thenReturn(false, true);
		when(groupMembers.save(any(ResearchGroupMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

		ResearchGroupMember created = service.addMember("group-1",
				new MemberRequest("teacher-2", "MEMBER"), auth);

		assertThat(created.getTeacherId()).isEqualTo("teacher-2");
		verify(groupMembers).save(created);
		assertThatThrownBy(() -> service.addMember("group-1",
				new MemberRequest("teacher-2", "MEMBER"), auth))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void requiresLeaveReasonAndAllowsOnlyVisibleMemberAttendance() {
		ResearchActivity activity = new ResearchActivity();
		activity.setId("activity-1");
		activity.setGroupId("group-1");
		when(activities.findById("activity-1")).thenReturn(Optional.of(activity));
		when(groups.findById("group-1")).thenReturn(Optional.of(new ResearchGroup()));
		ResearchActivityMember member = new ResearchActivityMember();
		member.setActivityId("activity-1");
		member.setTeacherId("teacher-1");
		when(activityMembers.findByActivityIdAndTeacherId("activity-1", "teacher-1"))
				.thenReturn(Optional.of(member));

		assertThatThrownBy(() -> service.attendance("activity-1",
				new AttendanceRequest("teacher-1", "LEAVE", null), auth))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("请假必须填写原因");
		service.attendance("activity-1",
				new AttendanceRequest("teacher-1", "LEAVE", "公出"), auth);
		assertThat(member.getAttendanceStatus()).isEqualTo("LEAVE");
	}

	@Test
	void preventsArchivingUnpublishedResult() {
		ResearchResult result = new ResearchResult();
		result.setId("result-1");
		result.setActivityId("activity-1");
		result.setStatus("SUBMITTED");
		ResearchActivity activity = new ResearchActivity();
		activity.setId("activity-1");
		activity.setGroupId("group-1");
		when(results.findById("result-1")).thenReturn(Optional.of(result));
		when(activities.findById("activity-1")).thenReturn(Optional.of(activity));
		when(groups.findById("group-1")).thenReturn(Optional.of(new ResearchGroup()));

		assertThatThrownBy(() -> service.transitionResult("result-1", "ARCHIVED", auth))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("只有已发布成果可以归档");
	}

	@Test
	void reusesIdempotentResearchResultSubmission() {
		ResearchResult result = new ResearchResult();
		result.setId("result-1");
		result.setActivityId("activity-1");
		result.setStatus("SUBMITTED");
		ResearchActivity activity = new ResearchActivity();
		activity.setId("activity-1");
		activity.setGroupId("group-1");
		TeachingReviewRecord review = new TeachingReviewRecord();
		review.setId("review-1");
		when(results.findById("result-1")).thenReturn(Optional.of(result));
		when(activities.findById("activity-1")).thenReturn(Optional.of(activity));
		when(groups.findById("group-1")).thenReturn(Optional.of(new ResearchGroup()));
		when(reviews.findIdempotent("RESEARCH_RESULT", "result-1", "retry-key", auth))
				.thenReturn(review);
		when(results.save(result)).thenReturn(result);

		ResearchResult saved = service.submitResult("result-1", "retry-key", auth);

		assertThat(saved.getReviewRecordId()).isEqualTo("review-1");
		verify(reviews, never()).submit(anyString(), anyString(), any(), anyMap(), any());
	}

	@Test
	void rejectsManualSourceOnTrustedWrongAnswerEndpoint() {
		WrongAnswerConfirmed event = new WrongAnswerConfirmed(
				"event-1",
				"student-1",
				"course-1",
				"semester-1",
				null,
				"manual-1",
				null,
				null,
				"MANUAL",
				null,
				null);

		assertThatThrownBy(() -> service.onWrongAnswerConfirmed(event, auth))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("错题确认事件来源只能是作业或考试");
	}

	@Test
	void cancelsScheduledActivityOnlyWithReason() {
		ResearchActivity activity = new ResearchActivity();
		activity.setId("activity-1");
		activity.setGroupId("group-1");
		activity.setStatus("SCHEDULED");
		when(activities.findById("activity-1")).thenReturn(Optional.of(activity));
		when(groups.findById("group-1")).thenReturn(Optional.of(new ResearchGroup()));
		when(activities.save(any(ResearchActivity.class))).thenAnswer(invocation -> invocation.getArgument(0));

		assertThatThrownBy(() -> service.cancelActivity("activity-1",
				new ActivityCancelRequest(" "), auth))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("取消活动必须填写原因");

		ResearchActivity cancelled = service.cancelActivity("activity-1",
				new ActivityCancelRequest("场地临时不可用"), auth);
		assertThat(cancelled.getStatus()).isEqualTo("CANCELLED");
		assertThat(cancelled.getCancelReason()).isEqualTo("场地临时不可用");
	}

	@Test
	void rejectsGroupAccessOutsideTeacherScope() {
		when(scopes.resolve("teacher-1")).thenReturn(new EducationDataScope(
				false, Set.of(), Set.of(), Set.of(), Set.of("teacher-1"), Set.of()));
		ResearchGroup group = new ResearchGroup();
		group.setId("group-1");
		group.setLeaderTeacherId("teacher-2");
		when(groups.findById("group-1")).thenReturn(Optional.of(group));
		when(groupMembers.findByGroupId("group-1")).thenReturn(java.util.List.of());

		assertThatThrownBy(() -> service.groupMembers("group-1", auth))
				.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void failsClosedWhenTeacherIdentityBindingIsUnavailable() {
		ResearchGroup group = new ResearchGroup();
		group.setId("group-1");
		when(groups.findById("group-1")).thenReturn(Optional.of(group));
		when(activities.save(any(ResearchActivity.class))).thenAnswer(invocation -> invocation.getArgument(0));

		assertThatThrownBy(() -> service.createActivity("group-1",
				new ActivityRequest("活动", null, null, null, null), auth))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("教师身份绑定服务未配置");
	}

	@Test
	void deniesTeacherPersonalErrorBookRows() {
		when(scopes.resolve("teacher-1")).thenReturn(new EducationDataScope(
				false, Set.of(), Set.of(), Set.of(), Set.of("teacher-1"), Set.of()));

		assertThatThrownBy(() -> service.items(null, auth))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessage("教师只能查看错题聚合统计");
	}
}
