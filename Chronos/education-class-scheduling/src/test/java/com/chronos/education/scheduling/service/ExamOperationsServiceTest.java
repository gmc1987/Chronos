package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.chronos.education.scheduling.dao.ClassroomRepository;
import com.chronos.education.scheduling.dao.ExamAccommodationRepository;
import com.chronos.education.scheduling.dao.ExamAdmissionTicketRepository;
import com.chronos.education.scheduling.dao.ExamCandidateRepository;
import com.chronos.education.scheduling.dao.ExamIncidentActionRepository;
import com.chronos.education.scheduling.dao.ExamIncidentRepository;
import com.chronos.education.scheduling.dao.ExamMaterialHandoverRepository;
import com.chronos.education.scheduling.dao.ExamMaterialLedgerRepository;
import com.chronos.education.scheduling.dao.ExamPlanRepository;
import com.chronos.education.scheduling.dao.ExamRegistrationRepository;
import com.chronos.education.scheduling.dao.ExamRoomRepository;
import com.chronos.education.scheduling.dao.ExamSessionRepository;
import com.chronos.education.scheduling.dao.StudentProfileRepository;
import com.chronos.education.scheduling.model.ExamAdmissionTicket;
import com.chronos.education.scheduling.model.ExamCandidate;
import com.chronos.education.scheduling.model.ExamIncident;
import com.chronos.education.scheduling.model.ExamMaterialLedger;
import com.chronos.education.scheduling.model.ExamOperationsCommands;
import com.chronos.education.scheduling.model.ExamPlan;
import com.chronos.education.scheduling.model.ExamRoom;
import com.chronos.education.scheduling.model.ExamSession;
import com.chronos.education.scheduling.model.StudentProfile;
import com.chronos.education.scheduling.model.ExamRegistration;
import com.chronos.education.scheduling.model.Classroom;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ExamOperationsServiceTest {
	@Mock private ExamRegistrationRepository registrations;
	@Mock private ExamAccommodationRepository accommodations;
	@Mock private ExamAdmissionTicketRepository tickets;
	@Mock private ExamMaterialLedgerRepository ledgers;
	@Mock private ExamMaterialHandoverRepository handovers;
	@Mock private ExamIncidentRepository incidents;
	@Mock private ExamIncidentActionRepository incidentActions;
	@Mock private ExamPlanRepository plans;
	@Mock private ExamSessionRepository sessions;
	@Mock private ExamRoomRepository rooms;
	@Mock private ExamCandidateRepository candidates;
	@Mock private ClassroomRepository classrooms;
	@Mock private StudentProfileRepository students;
	@Spy private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
	@InjectMocks private ExamOperationsService service;

	@Test
	void registrationIsIdempotentAndLocksTheSession() {
		ExamSession session = session("DRAFT");
		ExamPlan plan = plan("DRAFT", 0);
		ExamRegistration existing = new ExamRegistration();
		existing.setId("registration-1");
		existing.setSessionId("session-1");
		existing.setStudentId("student-1");
		when(sessions.findLockedById("session-1")).thenReturn(Optional.of(session));
		when(plans.findById("plan-1")).thenReturn(Optional.of(plan));
		when(registrations.findBySessionIdAndStudentId("session-1", "student-1"))
				.thenReturn(Optional.of(existing));

		assertThat(service.register(
				new ExamOperationsCommands.Registration("session-1", "student-1"), "student-1"))
				.isSameAs(existing);
		verify(sessions).findLockedById("session-1");
	}

	@Test
	void registrationRejectsInactiveStudentAndCapacityOverflow() {
		ExamSession session = session("DRAFT");
		when(sessions.findLockedById("session-1")).thenReturn(Optional.of(session));
		when(plans.findById("plan-1")).thenReturn(Optional.of(plan("DRAFT", 0)));
		when(registrations.findBySessionIdAndStudentId("session-1", "student-1"))
				.thenReturn(Optional.empty());
		StudentProfile inactive = new StudentProfile();
		inactive.setId("student-1");
		inactive.setEnrollmentStatus("WITHDRAWN");
		when(students.findById("student-1")).thenReturn(Optional.of(inactive));

		assertThatThrownBy(() -> service.register(
				new ExamOperationsCommands.Registration("session-1", "student-1"), "admin"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("学生当前不具备考试资格");

		inactive.setEnrollmentStatus("ACTIVE");
		ExamRoom room = new ExamRoom();
		room.setClassroomId("classroom-1");
		Classroom classroom = new Classroom();
		classroom.setId("classroom-1");
		classroom.setEnabled(true);
		classroom.setCapacity(1);
		when(rooms.findBySessionId("session-1")).thenReturn(List.of(room));
		when(classrooms.findById("classroom-1")).thenReturn(Optional.of(classroom));
		when(registrations.countBySessionIdAndStatusIn("session-1", List.of("SUBMITTED", "APPROVED")))
				.thenReturn(1L);

		assertThatThrownBy(() -> service.register(
				new ExamOperationsCommands.Registration("session-1", "student-1"), "admin"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("考试场次报名人数已达到容量上限");
	}

	@Test
	void handoverRequiresTwoPeopleAndRecordsDiscrepancy() {
		ExamMaterialLedger ledger = new ExamMaterialLedger();
		ledger.setId("ledger-1");
		ledger.setPlannedQuantity(5);
		ledger.setReceivedQuantity(0);
		ledger.setStatus("OPEN");
		when(ledgers.findById("ledger-1")).thenReturn(Optional.of(ledger));
		when(ledgers.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		assertThatThrownBy(() -> service.handover(
				new ExamOperationsCommands.Handover("ledger-1", "alice", 5, null), "alice"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("物资交接人和接收人必须是不同人员");

		ExamMaterialLedger saved = service.handover(
				new ExamOperationsCommands.Handover("ledger-1", "bob", 4, "封签破损短少1份"), "alice");
		assertThat(saved.getStatus()).isEqualTo("DISCREPANCY");
		assertThat(saved.getReceivedQuantity()).isEqualTo(4);
		verify(handovers).save(any());
	}

	@Test
	void incidentMustFollowWorkflowAndCloseWithConclusion() {
		ExamIncident incident = new ExamIncident();
		incident.setId("incident-1");
		incident.setStatus("REPORTED");
		when(incidents.findById("incident-1")).thenReturn(Optional.of(incident));
		when(incidentActions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		service.actOnIncident("incident-1",
				new ExamOperationsCommands.IncidentAction("REVIEW", "已核查", null), "reviewer");
		service.actOnIncident("incident-1",
				new ExamOperationsCommands.IncidentAction("DISPOSE", "已处置", null), "operator");
		assertThatThrownBy(() -> service.actOnIncident("incident-1",
				new ExamOperationsCommands.IncidentAction("CLOSE", " ", null), "operator"))
				.isInstanceOf(IllegalArgumentException.class);

		service.actOnIncident("incident-1",
				new ExamOperationsCommands.IncidentAction("CLOSE", "结论已确认", null), "manager");
		assertThat(incident.getStatus()).isEqualTo("CLOSED");
		assertThat(incident.getConclusion()).isEqualTo("结论已确认");
		assertThat(incident.getClosedAt()).isNotNull();
	}

	@Test
	void generatingNewTicketVersionRevokesOldTicket() {
		ExamPlan plan = plan("PUBLISHED", 2);
		ExamSession session = session("PUBLISHED");
		ExamRoom room = new ExamRoom();
		room.setId("room-1");
		room.setSessionId("session-1");
		room.setClassroomId("classroom-1");
		ExamCandidate candidate = new ExamCandidate();
		candidate.setId("candidate-1");
		candidate.setRoomId("room-1");
		candidate.setStudentId("student-1");
		candidate.setSeatNo(8);
		ExamAdmissionTicket old = new ExamAdmissionTicket();
		old.setId("ticket-old");
		old.setCandidateId("candidate-1");
		old.setPublishedVersion(1);
		old.setStatus("ISSUED");
		when(plans.findById("plan-1")).thenReturn(Optional.of(plan));
		when(sessions.findByPlanIdOrderByExamDateAscStartTimeAsc("plan-1")).thenReturn(List.of(session));
		when(rooms.findBySessionId("session-1")).thenReturn(List.of(room));
		when(candidates.findByRoomIdOrderBySeatNoAsc("room-1")).thenReturn(List.of(candidate));
		when(tickets.findByCandidateIdAndPublishedVersion("candidate-1", 2))
				.thenReturn(Optional.empty());
		when(tickets.findByCandidateIdOrderByPublishedVersionDesc("candidate-1"))
				.thenReturn(List.of(old));
		when(tickets.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		List<ExamAdmissionTicket> result = service.generateTickets("plan-1", "exam-admin");

		assertThat(result).hasSize(1);
		assertThat(result.get(0).getPublishedVersion()).isEqualTo(2);
		assertThat(old.getStatus()).isEqualTo("REVOKED");
		assertThat(old.getRevokedAt()).isNotNull();
	}

	private ExamSession session(String status) {
		ExamSession value = new ExamSession();
		value.setId("session-1");
		value.setPlanId("plan-1");
		value.setStatus(status);
		return value;
	}

	private ExamPlan plan(String status, int version) {
		ExamPlan value = new ExamPlan();
		value.setId("plan-1");
		value.setStatus(status);
		value.setPublishedVersion(version);
		return value;
	}
}
