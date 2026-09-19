package com.chronos.education.grade.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chronos.education.grade.dto.GradeDtos;
import com.chronos.education.grade.model.GradePublishSnapshot;
import com.chronos.education.grade.model.Gradebook;
import com.chronos.education.grade.service.GradeCenterService;
import com.chronos.education.scheduling.dao.EducationUserBindingRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class GradeControllerHttpTest {
	private final GradeCenterService service = mock(GradeCenterService.class);
	private final EducationUserBindingRepository bindings = mock(EducationUserBindingRepository.class);
	private final UsernamePasswordAuthenticationToken authentication =
			new UsernamePasswordAuthenticationToken("teacher-1", "n/a");
	private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
			new GradeCenterController(service, mock(org.springframework.context.ApplicationEventPublisher.class)),
			new GradePortalController(service, bindings))
			.setControllerAdvice(new GradeApiExceptionHandler())
			.defaultRequest(get("/").principal(authentication))
			.build();

	@BeforeEach
	void authenticate() {
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}

	@AfterEach
	void clearAuthentication() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void gradebookDetailReturnsSafeDtoWithoutInternalIdentifiers() throws Exception {
		GradebookControllerFixtures fixture = new GradebookControllerFixtures();
		when(service.getGradebook("book-1", "teacher-1")).thenReturn(fixture.detail());

		mockMvc.perform(get("/admin/education/grades/gradebooks/book-1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value("book-1"))
				.andExpect(jsonPath("$.data.students[0].studentId").value("student-1"))
				.andExpect(jsonPath("$.data.schoolId").doesNotExist())
				.andExpect(jsonPath("$.data.workflowInstanceId").doesNotExist());
	}

	@Test
	void unauthorizedGradebookAccessIsForbidden() throws Exception {
		when(service.getGradebook("book-1", "teacher-1"))
				.thenThrow(new org.springframework.security.access.AccessDeniedException("无权访问该成绩册"));

		mockMvc.perform(get("/admin/education/grades/gradebooks/book-1"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("403"));
	}

	@Test
	void optimisticLockConflictIsMappedToHttp409() throws Exception {
		when(service.saveItems(eq("book-1"), org.mockito.ArgumentMatchers.any(), eq("teacher-1")))
				.thenThrow(new ObjectOptimisticLockingFailureException(Gradebook.class, "book-1"));

		mockMvc.perform(put("/admin/education/grades/gradebooks/book-1/items")
						.contentType("application/json")
						.content("{\"items\":[],\"rowVersion\":1}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("409"));
	}

	@Test
	void snapshotsExposeVersionedStructureAndRequireOwnership() throws Exception {
		GradePublishSnapshot snapshot = new GradePublishSnapshot();
		snapshot.setGradebookId("book-1");
		snapshot.setVersionNo(2);
		snapshot.setSnapshotHash("hash-2");
		snapshot.setPublishedBy("teacher-1");
		when(service.snapshots("book-1", "teacher-1")).thenReturn(List.of(snapshot));

		mockMvc.perform(get("/admin/education/grades/gradebooks/book-1/snapshots"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].versionNo").value(2))
				.andExpect(jsonPath("$.data[0].snapshotHash").value("hash-2"));

		when(service.snapshots("book-2", "teacher-1"))
				.thenThrow(new org.springframework.security.access.AccessDeniedException("无权访问该成绩册"));
		mockMvc.perform(get("/admin/education/grades/gradebooks/book-2/snapshots"))
				.andExpect(status().isForbidden());
	}

	@Test
	void publishingAlreadyPublishedGradebookKeepsSuccessfulHttpResponse() throws Exception {
		Gradebook published = new Gradebook();
		published.setId("book-1");
		published.setOfferingId("offering-1");
		published.setSubmissionNo(2);
		published.setStatus("PUBLISHED");
		GradePublishSnapshot snapshot = new GradePublishSnapshot();
		snapshot.setSnapshotHash("hash-2");
		when(service.publish("book-1", "teacher-1")).thenReturn(published);
		when(service.snapshots("book-1", "teacher-1")).thenReturn(List.of(snapshot));

		mockMvc.perform(post("/admin/education/grades/gradebooks/book-1/publish"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("PUBLISHED"));

		verify(service).publish("book-1", "teacher-1");
	}

	@Test
	void portalCannotReadAnotherStudentsGrade() throws Exception {
		when(bindings.findByUsernameAndProfileType("teacher-1", "STUDENT"))
				.thenReturn(java.util.Optional.empty());

		mockMvc.perform(get("/portal/education/grades/grade-1"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("403"));
	}

	private static final class GradebookControllerFixtures {
		private GradeDtos.GradebookDetailResponse detail() {
			return new GradeDtos.GradebookDetailResponse(
					"book-1",
					"offering-1",
					"scheme-1",
					"EDITING",
					1,
					3L,
					null,
					null,
					List.of(new GradeDtos.StudentSnapshot(
							"snapshot-1",
							"student-1",
							"S001",
							"Student One",
							"class-1",
							"ENROLLED",
							"member-1",
							null,
							null,
							1,
							"student-hash")),
					List.of(),
					List.of(),
					List.of());
		}
	}
}
