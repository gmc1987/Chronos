package com.chronos.education.grade.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.chronos.education.grade.dao.AssessmentComponentRepository;
import com.chronos.education.grade.dao.AssessmentSchemeRepository;
import com.chronos.education.grade.dao.CourseGradeRepository;
import com.chronos.education.grade.dao.GradeItemRepository;
import com.chronos.education.grade.dao.GradePublishSnapshotRepository;
import com.chronos.education.grade.dao.GradebookRepository;
import com.chronos.education.grade.dao.GradebookStudentRepository;
import com.chronos.education.grade.dto.GradeDtos.ComponentCommand;
import com.chronos.education.grade.dto.GradeDtos.GradeItemCommand;
import com.chronos.education.grade.dto.GradeDtos.ItemsCommand;
import com.chronos.education.grade.dto.GradeDtos.SchemeCommand;
import com.chronos.education.grade.dto.GradeDtos.GradebookDetailResponse;
import com.chronos.education.grade.model.AssessmentComponent;
import com.chronos.education.grade.model.AssessmentScheme;
import com.chronos.education.grade.model.Gradebook;
import com.chronos.education.grade.model.GradebookStudent;
import com.chronos.education.grade.model.GradeItem;
import com.chronos.education.grade.model.GradePublishSnapshot;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.StudentProfileRepository;
import com.chronos.education.scheduling.dao.TeachingClassMemberRepository;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.Idao.workflow.IWorkflowInstanceRepository;
import com.chronos.Idao.workflow.IWorkflowTaskRepository;
import com.chronos.service.iService.IAuditLogService;
import com.chronos.workflow.WorkflowService;
import com.chronos.model.workflow.WorkflowInstance;
import com.chronos.model.workflow.WorkflowTask;
import com.chronos.education.grade.service.DomainEventOutboxService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

@ExtendWith(MockitoExtension.class)
class GradeCenterValidationTest {
	@Mock AssessmentSchemeRepository schemes;
	@Mock AssessmentComponentRepository components;
	@Mock GradebookRepository gradebooks;
	@Mock GradebookStudentRepository students;
	@Mock GradeItemRepository items;
	@Mock CourseGradeRepository courseGrades;
	@Mock GradePublishSnapshotRepository snapshots;
	@Mock CourseOfferingRepository offerings;
	@Mock TeachingClassMemberRepository members;
	@Mock StudentProfileRepository profiles;
	@Mock WorkflowService workflows;
	@Mock IWorkflowTaskRepository workflowTasks;
	@Mock IWorkflowInstanceRepository workflowInstances;
	@Mock EducationDataScopeService dataScopes;
	@Mock GradeNotificationService notifications;
	@Mock IAuditLogService audit;
	@Mock ObjectMapper json;
	@Mock DomainEventOutboxService domainEvents;
	@InjectMocks GradeCenterService service;

	@Test
	void rejectsWeightsThatDoNotTotalOneHundred() {
		SchemeCommand command = new SchemeCommand(
				"offering-1", "方案", BigDecimal.valueOf(100), BigDecimal.valueOf(60),
				List.of(new ComponentCommand("final", "期末", "MANUAL",
						BigDecimal.valueOf(60), BigDecimal.valueOf(100), 1)), null);

		assertThrows(IllegalArgumentException.class, () -> service.createScheme(command, "teacher"));
	}

	@Test
	void knowledgeAnalysisFailsClosedWithMachineReadableDependencies() {
		var response = service.knowledgeAnalysis("teacher");

		org.junit.jupiter.api.Assertions.assertFalse(response.available());
		org.junit.jupiter.api.Assertions.assertEquals("KNOWLEDGE_LINKAGE_UNAVAILABLE", response.reasonCode());
		org.junit.jupiter.api.Assertions.assertTrue(response.dependencies().stream()
				.anyMatch(dependency -> "GRADE_ITEM_QUESTION_REFERENCE".equals(dependency.code())
						&& "MISSING".equals(dependency.status())));
		org.mockito.Mockito.verify(dataScopes).resolve("teacher");
	}

	@Test
	void rejectsNonManualSourcesInFirstSlice() {
		SchemeCommand command = new SchemeCommand(
				"offering-1", "方案", BigDecimal.valueOf(100), BigDecimal.valueOf(60),
				List.of(new ComponentCommand("exam", "考试", "EXAM",
						BigDecimal.valueOf(100), BigDecimal.valueOf(100), 1)), null);

		assertThrows(IllegalArgumentException.class, () -> service.createScheme(command, "teacher"));
	}

	@Test
	void persistsGradebookWhenSavingItems() {
		Gradebook gradebook = new Gradebook();
		gradebook.setId("gradebook-1");
		gradebook.setSchemeId("scheme-1");
		gradebook.setTeacherId("teacher-1");
		gradebook.setStatus("EDITING");
		gradebook.setRowVersion(3L);
		AssessmentComponent component = new AssessmentComponent();
		component.setId("component-1");
		component.setMaxScore(BigDecimal.valueOf(100));
		GradebookStudent student = new GradebookStudent();
		student.setStudentId("student-1");
		when(gradebooks.findById("gradebook-1")).thenReturn(Optional.of(gradebook));
		when(dataScopes.resolve("teacher")).thenReturn(new EducationDataScope(
				false, Set.of(), Set.of(), Set.of(), Set.of("teacher-1"), Set.of()));
		when(components.findBySchemeIdOrderBySortOrder("scheme-1")).thenReturn(List.of(component));
		when(students.findByGradebookId("gradebook-1")).thenReturn(List.of(student));
		when(items.findByGradebookId("gradebook-1")).thenReturn(List.of());
		service.saveItems("gradebook-1",
				new ItemsCommand(List.of(new GradeItemCommand("component-1", "student-1",
						BigDecimal.valueOf(88), "NORMAL", null)), 3L),
				"teacher");
		verify(gradebooks).saveAndFlush(gradebook);
	}

	@Test
	void publishingAlreadyPublishedGradebookIsIdempotent() {
		Gradebook gradebook = new Gradebook();
		gradebook.setId("gradebook-1");
		gradebook.setStatus("PUBLISHED");
		when(gradebooks.findById("gradebook-1")).thenReturn(Optional.of(gradebook));
		org.junit.jupiter.api.Assertions.assertSame(gradebook, service.publish("gradebook-1", "publisher"));
	}

	@Test
	void rejectedGradebookCanBeEditedAndSubmittedAgain() {
		Gradebook gradebook = gradebook("gradebook-1", "REVIEWING");
		gradebook.setSchemeId("scheme-1");
		gradebook.setTeacherId("teacher-1");
		gradebook.setSubmissionNo(1);
		GradebookStudent student = student("student-1");
		GradeItem item = item("component-1", "student-1");
		WorkflowTask task = new WorkflowTask();
		task.setId("task-1");
		task.setInstanceId("instance-1");
		task.setNodeKey("review");
		WorkflowInstance running = new WorkflowInstance();
		running.setBusinessKey("gradebook-1");
		running.setStatus("RUNNING");
		WorkflowInstance rejected = new WorkflowInstance();
		rejected.setBusinessKey("gradebook-1");
		rejected.setStatus("REJECTED");
		WorkflowInstance resubmission = new WorkflowInstance();
		resubmission.setId("instance-2");
		resubmission.setBusinessKey("gradebook-1");
		resubmission.setStatus("RUNNING");
		when(gradebooks.findById("gradebook-1")).thenReturn(Optional.of(gradebook));
		when(dataScopes.resolve("teacher")).thenReturn(new EducationDataScope(
				false, Set.of(), Set.of(), Set.of(), Set.of("teacher-1"), Set.of()));
		when(dataScopes.resolve("reviewer")).thenReturn(new EducationDataScope(
				false, Set.of(), Set.of(), Set.of(), Set.of("reviewer-1"), Set.of()));
		when(workflowTasks.findById("task-1")).thenReturn(Optional.of(task));
		when(workflows.instance("instance-1")).thenReturn(running);
		when(workflows.rejectTask("task-1", "", "补录成绩", "reviewer")).thenReturn(rejected);
		when(items.findByGradebookId("gradebook-1")).thenReturn(List.of(item));
		when(components.findBySchemeIdOrderBySortOrder("scheme-1")).thenReturn(List.of(component("component-1")));
		when(students.findByGradebookId("gradebook-1")).thenReturn(List.of(student));
		when(workflows.startByCode(any(), any(), any(), any())).thenReturn(resubmission);

		service.review("gradebook-1", "task-1", false, "补录成绩", "reviewer");
		service.saveItems("gradebook-1",
				new ItemsCommand(List.of(new GradeItemCommand("component-1", "student-1",
						BigDecimal.valueOf(95), "NORMAL", "复核后调整")), 0L),
				"teacher");
		service.submit("gradebook-1", "teacher");

		verify(workflows).rejectTask("task-1", "", "补录成绩", "reviewer");
		verify(workflows).startByCode("EDU_GRADEBOOK_REVIEW", "gradebook-1",
				java.util.Map.of("gradebookId", "gradebook-1", "submitter", "teacher"), "teacher");
		verify(gradebooks, org.mockito.Mockito.atLeast(2)).save(gradebook);
	}

	@Test
	void publishedGradebookCannotBeEdited() {
		Gradebook gradebook = gradebook("gradebook-1", "PUBLISHED");
		gradebook.setSchemeId("scheme-1");
		gradebook.setTeacherId("teacher-1");
		when(gradebooks.findById("gradebook-1")).thenReturn(Optional.of(gradebook));
		when(dataScopes.resolve("teacher")).thenReturn(new EducationDataScope(
				false, Set.of(), Set.of(), Set.of(), Set.of("teacher-1"), Set.of()));

		assertThrows(IllegalStateException.class, () -> service.saveItems("gradebook-1",
				new ItemsCommand(List.of(), 0L), "teacher"));
	}

	@Test
	void importsValidRowsAndReportsInvalidRowsWithoutChangingPublishedState() throws Exception {
		Gradebook gradebook = gradebook("gradebook-1", "EDITING");
		gradebook.setSchemeId("scheme-1");
		gradebook.setTeacherId("teacher-1");
		AssessmentComponent component = component("component-1");
		component.setCode("final");
		component.setMaxScore(BigDecimal.valueOf(100));
		GradebookStudent student = student("student-1");
		student.setStudentNo("S001");
		when(gradebooks.findById("gradebook-1")).thenReturn(Optional.of(gradebook));
		when(dataScopes.resolve("teacher")).thenReturn(new EducationDataScope(
				false, Set.of(), Set.of(), Set.of(), Set.of("teacher-1"), Set.of()));
		when(students.findByGradebookId("gradebook-1")).thenReturn(List.of(student));
		when(components.findBySchemeIdOrderBySortOrder("scheme-1")).thenReturn(List.of(component));
		when(items.findByGradebookId("gradebook-1")).thenReturn(List.of());

		MockMultipartFile file = new MockMultipartFile("file", "grades.xlsx",
				"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
				excel());

		var result = service.importItems("gradebook-1", file, "teacher");
		assertEquals(2, result.totalRows());
		assertEquals(1, result.importedRows());
		assertEquals(1, result.errors().size());
	}

	private byte[] excel() throws Exception {
		try (XSSFWorkbook workbook = new XSSFWorkbook();
				java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
			var sheet = workbook.createSheet();
			sheet.createRow(0).createCell(0).setCellValue("studentNo");
			sheet.getRow(0).createCell(1).setCellValue("final");
			sheet.createRow(1).createCell(0).setCellValue("S001");
			sheet.getRow(1).createCell(1).setCellValue(88);
			sheet.createRow(2).createCell(0).setCellValue("UNKNOWN");
			sheet.getRow(2).createCell(1).setCellValue(90);
			workbook.write(output);
			return output.toByteArray();
		}
	}

	@Test
	void gradebookDetailContainsSafeMatrixDataAndSnapshotMetadata() {
		Gradebook gradebook = gradebook("gradebook-1", "EDITING");
		gradebook.setOfferingId("offering-1");
		gradebook.setSchemeId("scheme-1");
		gradebook.setTeacherId("teacher-1");
		AssessmentComponent component = component("component-1");
		component.setCode("final");
		component.setName("期末");
		GradebookStudent student = student("student-1");
		GradeItem item = item("component-1", "student-1");
		GradePublishSnapshot snapshot = new GradePublishSnapshot();
		snapshot.setVersionNo(2);
		snapshot.setSnapshotHash("hash-2");
		snapshot.setPublishedBy("publisher");
		when(gradebooks.findById("gradebook-1")).thenReturn(Optional.of(gradebook));
		when(dataScopes.resolve("teacher")).thenReturn(new EducationDataScope(
				false, Set.of(), Set.of(), Set.of(), Set.of("teacher-1"), Set.of()));
		when(students.findByGradebookId("gradebook-1")).thenReturn(List.of(student));
		when(components.findBySchemeIdOrderBySortOrder("scheme-1")).thenReturn(List.of(component));
		when(items.findByGradebookId("gradebook-1")).thenReturn(List.of(item));
		when(snapshots.findByGradebookIdOrderByVersionNoDesc("gradebook-1")).thenReturn(List.of(snapshot));

		GradebookDetailResponse response = service.getGradebook("gradebook-1", "teacher");

		org.junit.jupiter.api.Assertions.assertEquals("EDITING", response.status());
		org.junit.jupiter.api.Assertions.assertEquals("student-1", response.students().get(0).studentId());
		org.junit.jupiter.api.Assertions.assertEquals("component-1", response.components().get(0).id());
		org.junit.jupiter.api.Assertions.assertEquals("student-1", response.items().get(0).studentId());
		org.junit.jupiter.api.Assertions.assertEquals("hash-2", response.snapshots().get(0).snapshotHash());
	}

	@Test
	void snapshotHashIsRecomputableAndHistoryRemainsNewestFirst() {
		GradePublishSnapshot newest = new GradePublishSnapshot();
		newest.setVersionNo(2);
		newest.setSnapshotJson("snapshot-v2");
		newest.setSnapshotHash(sha256("snapshot-v2"));
		GradePublishSnapshot oldest = new GradePublishSnapshot();
		oldest.setVersionNo(1);
		oldest.setSnapshotJson("snapshot-v1");
		oldest.setSnapshotHash(sha256("snapshot-v1"));
		Gradebook gradebook = gradebook("gradebook-1", "PUBLISHED");
		gradebook.setTeacherId("teacher-1");
		when(gradebooks.findById("gradebook-1")).thenReturn(Optional.of(gradebook));
		when(dataScopes.resolve("teacher")).thenReturn(new EducationDataScope(
				false, Set.of(), Set.of(), Set.of(), Set.of("teacher-1"), Set.of()));
		when(snapshots.findByGradebookIdOrderByVersionNoDesc("gradebook-1"))
				.thenReturn(List.of(newest, oldest));

		List<GradePublishSnapshot> history = service.snapshots("gradebook-1", "teacher");

		org.junit.jupiter.api.Assertions.assertEquals(List.of(2, 1),
				history.stream().map(GradePublishSnapshot::getVersionNo).toList());
		org.junit.jupiter.api.Assertions.assertEquals(history.get(0).getSnapshotHash(),
				sha256(history.get(0).getSnapshotJson()));
	}

	@Test
	void duplicatePublishDoesNotCreateSnapshotOrNotifyAgain() throws Exception {
		Gradebook gradebook = gradebook("gradebook-1", "APPROVED");
		gradebook.setSchemeId("scheme-1");
		gradebook.setOfferingId("offering-1");
		gradebook.setSubmissionNo(2);
		AssessmentComponent component = component("component-1");
		component.setWeight(BigDecimal.valueOf(100));
		GradebookStudent student = student("student-1");
		GradeItem item = item("component-1", "student-1");
		AssessmentScheme scheme = new AssessmentScheme();
		scheme.setId("scheme-1");
		scheme.setPassScore(BigDecimal.valueOf(60));
		CourseOffering offering = new CourseOffering();
		offering.setId("offering-1");
		when(gradebooks.findById("gradebook-1")).thenReturn(Optional.of(gradebook));
		when(students.findByGradebookId("gradebook-1")).thenReturn(List.of(student));
		when(components.findBySchemeIdOrderBySortOrder("scheme-1")).thenReturn(List.of(component));
		when(items.findByGradebookId("gradebook-1")).thenReturn(List.of(item));
		when(schemes.findById("scheme-1")).thenReturn(Optional.of(scheme));
		when(offerings.findById("offering-1")).thenReturn(Optional.of(offering));
		when(json.writeValueAsString(any())).thenReturn("snapshot");

		service.publish("gradebook-1", "publisher");
		service.publish("gradebook-1", "publisher");

		verify(snapshots).save(any(GradePublishSnapshot.class));
		verify(notifications).published("gradebook-1", "student-1", "offering-1", "2");
		verifyNoMoreInteractions(snapshots, notifications);
	}

	private static Gradebook gradebook(String id, String status) {
		Gradebook gradebook = new Gradebook();
		gradebook.setId(id);
		gradebook.setStatus(status);
		gradebook.setRowVersion(0L);
		return gradebook;
	}

	private static GradebookStudent student(String id) {
		GradebookStudent student = new GradebookStudent();
		student.setId("roster-" + id);
		student.setStudentId(id);
		student.setStudentNo("NO-" + id);
		student.setStudentName("学生");
		student.setEnrollmentStatus("ENROLLED");
		student.setSourceMemberId("member-" + id);
		return student;
	}

	private static GradeItem item(String componentId, String studentId) {
		GradeItem item = new GradeItem();
		item.setId("item-1");
		item.setComponentId(componentId);
		item.setStudentId(studentId);
		item.setConvertedScore(BigDecimal.valueOf(88));
		return item;
	}

	private static AssessmentComponent component(String id) {
		AssessmentComponent component = new AssessmentComponent();
		component.setId(id);
		component.setSchemeId("scheme-1");
		component.setMaxScore(BigDecimal.valueOf(100));
		component.setWeight(BigDecimal.valueOf(100));
		component.setSortOrder(1);
		return component;
	}

	private static String sha256(String value) {
		try {
			byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			StringBuilder result = new StringBuilder();
			for (byte valueByte : digest) {
				result.append(String.format("%02x", valueByte));
			}
			return result.toString();
		} catch (java.security.NoSuchAlgorithmException exception) {
			throw new AssertionError(exception);
		}
	}
}
