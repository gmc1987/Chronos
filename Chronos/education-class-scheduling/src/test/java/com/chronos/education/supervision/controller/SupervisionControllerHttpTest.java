package com.chronos.education.supervision.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.chronos.education.supervision.model.SupervisionAssignment;
import com.chronos.education.supervision.model.SupervisionPlan;
import com.chronos.education.supervision.model.SupervisionRecord;
import com.chronos.education.supervision.service.SupervisionCenterService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SupervisionControllerHttpTest {
	private final SupervisionCenterService service = mock(SupervisionCenterService.class);
	private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SupervisionController(service))
			.defaultRequest(post("/").principal(new UsernamePasswordAuthenticationToken("QA-SUPERVISOR-20260920", "n/a")))
			.build();

	@Test
	void supervisorTaskLifecycleKeepsAuthenticatedActorAndSnapshotsEvaluation() throws Exception {
		SupervisionAssignment assignment = new SupervisionAssignment();
		assignment.setId("QA-ASSIGNMENT-20260920");
		assignment.setPlanId("QA-PLAN-20260920");
		assignment.setTeacherId("QA-TEACHER-20260920");
		assignment.setScheduleEntryId("QA-SCHEDULE-20260920");
		assignment.setStatus("ACCEPTED");
		assignment.setCheckedInAt(LocalDateTime.parse("2026-09-20T09:00:00"));
		when(service.accept("QA-ASSIGNMENT-20260920", "QA-SUPERVISOR-20260920")).thenReturn(assignment);
		when(service.checkIn("QA-ASSIGNMENT-20260920", "QA-SUPERVISOR-20260920")).thenReturn(assignment);

		SupervisionRecord record = new SupervisionRecord();
		record.setId("QA-RECORD-20260920");
		record.setAssignmentId(assignment.getId());
		record.setSupervisorId("QA-SUPERVISOR-20260920");
		record.setTeacherId(assignment.getTeacherId());
		record.setFormTemplateId("QA-FORM-20260920");
		record.setFormSnapshotJson("{\"score\":4}");
		record.setScheduleContextSnapshotJson("{\"lesson\":\"QA\"}");
		record.setSubmittedAt(LocalDateTime.parse("2026-09-20T09:30:00"));
		when(service.submit(eq(assignment.getId()), eq("QA-SUPERVISOR-20260920"),
				eq("{\"score\":4}"), eq("{\"lesson\":\"QA\"}"), eq("QA-FORM-20260920"))).thenReturn(record);

		mockMvc.perform(post("/portal/education/supervision/tasks/{id}/accept", assignment.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("ACCEPTED"));
		mockMvc.perform(post("/portal/education/supervision/tasks/{id}/check-in", assignment.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.checkedInAt").value("2026-09-20T09:00:00"));
		mockMvc.perform(post("/portal/education/supervision/tasks/{id}/submit", assignment.getId())
						.contentType("application/json")
						.content("""
								{"formTemplateId":"QA-FORM-20260920","formSnapshotJson":"{\\"score\\":4}","scheduleContextSnapshotJson":"{\\"lesson\\":\\"QA\\"}"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.formSnapshotJson").value("{\"score\":4}"));

		verify(service).accept(assignment.getId(), "QA-SUPERVISOR-20260920");
		verify(service).checkIn(assignment.getId(), "QA-SUPERVISOR-20260920");
		verify(service).submit(assignment.getId(), "QA-SUPERVISOR-20260920",
				"{\"score\":4}", "{\"lesson\":\"QA\"}", "QA-FORM-20260920");
	}

	@Test
	void restrictedSupervisorCannotBeReplacedByRequestBodyActor() throws Exception {
		SupervisionPlan plan = new SupervisionPlan();
		plan.setId("QA-PLAN-20260920");
		plan.setName("QA plan");
		plan.setStatus("PUBLISHED");
		plan.setStartDate(LocalDate.of(2026, 9, 20));
		plan.setEndDate(LocalDate.of(2026, 9, 21));
		when(service.publishPlan(plan.getId(), "QA-SUPERVISOR-20260920")).thenReturn(plan);

		mockMvc.perform(post("/admin/education/supervision/plans/{id}/publish", plan.getId())
						.contentType("application/json")
						.content("{\"actor\":\"QA-OTHER-20260920\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("PUBLISHED"));

		verify(service).publishPlan(plan.getId(), "QA-SUPERVISOR-20260920");
		verify(service, never()).publishPlan(plan.getId(), "QA-OTHER-20260920");
	}
}
