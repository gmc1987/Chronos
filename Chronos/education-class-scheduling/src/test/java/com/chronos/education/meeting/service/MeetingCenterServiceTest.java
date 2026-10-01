package com.chronos.education.meeting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.education.meeting.dao.MeetingActionItemRepository;
import com.chronos.education.meeting.dao.MeetingMaterialRepository;
import com.chronos.education.meeting.dao.MeetingMinutesRepository;
import com.chronos.education.meeting.dao.MeetingParticipantRepository;
import com.chronos.education.meeting.dao.MeetingRepository;
import com.chronos.education.meeting.dao.MeetingRoomRepository;
import com.chronos.education.meeting.model.Meeting;
import com.chronos.education.meeting.model.MeetingParticipant;
import com.chronos.file.service.ManagedFileService;
import com.chronos.service.iService.IAuditLogService;

import jakarta.persistence.EntityManager;

@ExtendWith(MockitoExtension.class)
class MeetingCenterServiceTest {
	@Mock
	private MeetingRoomRepository rooms;
	@Mock
	private MeetingRepository meetings;
	@Mock
	private MeetingParticipantRepository participants;
	@Mock
	private MeetingMaterialRepository materials;
	@Mock
	private MeetingMinutesRepository minutes;
	@Mock
	private MeetingActionItemRepository actionItems;
	@Mock
	private IAdminUserRepository users;
	@Mock
	private ManagedFileService managedFiles;
	@Mock
	private MeetingNotificationService notifications;
	@Mock
	private IAuditLogService auditLogs;
	@Mock
	private EntityManager entityManager;

	@InjectMocks
	private MeetingCenterService service;

	@Test
	void invitedParticipantCanCheckInWithinWindow() {
		Meeting meeting = publishedMeeting(
				LocalDateTime.now().minusMinutes(5),
				LocalDateTime.now().plusMinutes(55));
		MeetingParticipant participant = participant("teacher01");
		stubViewRelations(meeting, participant);

		var result = service.checkIn(meeting.getId(), "teacher01");

		assertThat(result.currentParticipant().getCheckedInAt()).isNotNull();
		assertThat(result.currentParticipant().getCheckInMethod()).isEqualTo("PORTAL");
		verify(participants).save(participant);
		verify(auditLogs).log("MEETING_CHECK_IN", "MEETING", meeting.getId());
	}

	@Test
	void participantCannotCheckInBeforeWindow() {
		Meeting meeting = publishedMeeting(
				LocalDateTime.now().plusHours(2),
				LocalDateTime.now().plusHours(3));
		when(meetings.findById(meeting.getId())).thenReturn(Optional.of(meeting));

		assertThatThrownBy(() -> service.checkIn(meeting.getId(), "teacher01"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("签到仅在");
	}

	@Test
	void nonParticipantCannotCheckIn() {
		Meeting meeting = publishedMeeting(
				LocalDateTime.now().minusMinutes(5),
				LocalDateTime.now().plusMinutes(55));
		when(meetings.findById(meeting.getId())).thenReturn(Optional.of(meeting));
		when(participants.findByMeetingIdAndUsername(meeting.getId(), "outsider"))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.checkIn(meeting.getId(), "outsider"))
				.isInstanceOf(AccessDeniedException.class);
	}

	private void stubViewRelations(
			Meeting meeting,
			MeetingParticipant participant) {
		when(meetings.findById(meeting.getId())).thenReturn(Optional.of(meeting));
		when(participants.findByMeetingIdAndUsername(
				meeting.getId(), participant.getUsername()))
				.thenReturn(Optional.of(participant));
		when(participants.findByMeetingIdOrderByCreateTimeAsc(meeting.getId()))
				.thenReturn(List.of(participant));
		when(materials.findByMeetingIdOrderByCreateTimeAsc(meeting.getId()))
				.thenReturn(List.of());
		when(minutes.findByMeetingId(meeting.getId())).thenReturn(Optional.empty());
		when(actionItems.findByMeetingIdOrderByCreateTimeAsc(meeting.getId()))
				.thenReturn(List.of());
		when(participants.save(any(MeetingParticipant.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
	}

	private Meeting publishedMeeting(
			LocalDateTime start,
			LocalDateTime end) {
		Meeting meeting = new Meeting();
		meeting.setId("meeting-01");
		meeting.setTitle("教学工作会议");
		meeting.setOrganizerUsername("organizer");
		meeting.setMeetingType("ONLINE");
		meeting.setStartTime(start);
		meeting.setEndTime(end);
		meeting.setStatus("PUBLISHED");
		return meeting;
	}

	private MeetingParticipant participant(String username) {
		MeetingParticipant participant = new MeetingParticipant();
		participant.setId("participant-01");
		participant.setMeetingId("meeting-01");
		participant.setUsername(username);
		return participant;
	}
}
