package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import com.chronos.education.scheduling.dao.TeacherTeachingAssignmentRepository;
import com.chronos.education.scheduling.model.Subject;
import com.chronos.education.scheduling.service.AcademicCatalogImportService.Kind;

class AcademicCatalogImportServiceTest {
	@Test
	void previewAndRejectedBatchDoNotWriteSubjects() throws IOException {
		var academic = mock(AcademicDataService.class);
		when(academic.subjects()).thenReturn(List.of());
		var service = new AcademicCatalogImportService(academic, mock(EducationDataScopeService.class),
				mock(TeacherTeachingAssignmentRepository.class), mock(EducationResourceTransactionLock.class));

		var preview = service.run(Kind.SUBJECTS, workbook(service, 1), true, "school-a", null);
		assertThat(preview.validCount()).isEqualTo(1);
		var rejected = service.run(Kind.SUBJECTS, workbook(service, 2), false, "school-a", null);
		assertThat(rejected.errors()).singleElement().asString().contains("第 3 行", "重复");
		verify(academic, never()).saveSubject(isNull(), any(Subject.class));

		var imported = service.run(Kind.SUBJECTS, workbook(service, 1), false, "school-a", null);
		assertThat(imported.errors()).isEmpty();
		var subject = org.mockito.ArgumentCaptor.forClass(Subject.class);
		verify(academic).saveSubject(isNull(), subject.capture());
		assertThat(subject.getValue().getOrganizationId()).isEqualTo("school-a");
	}

	@Test
	void blankClassGradeUsesEnabledGradeAlreadyUsedByTargetSchool() throws IOException {
		var academic = mock(AcademicDataService.class);
		var localGrade = grade("local");
		var otherGrade = grade("other");
		var existingClass = new com.chronos.education.scheduling.model.AdministrativeClass();
		existingClass.setCampusId("school-a");
		existingClass.setGradeYear(2026);
		existingClass.setGradeId(localGrade.getId());
		when(academic.visibleGrades(org.mockito.ArgumentMatchers.anySet())).thenReturn(List.of(localGrade, otherGrade));
		when(academic.administrativeClasses()).thenReturn(List.of(existingClass));
		var service = new AcademicCatalogImportService(academic, mock(EducationDataScopeService.class),
				mock(TeacherTeachingAssignmentRepository.class), mock(EducationResourceTransactionLock.class));
		var actor = mock(org.springframework.security.core.Authentication.class);
		try (var book = new XSSFWorkbook(new ByteArrayInputStream(service.template(Kind.CLASSES)));
				var output = new ByteArrayOutputStream()) {
			var row = book.getSheetAt(0).createRow(1);
			row.createCell(0).setCellValue("CLASS001");
			row.createCell(1).setCellValue("测试班");
			row.createCell(2).setCellValue(2026);
			book.write(output);
			var file = new MockMultipartFile("file", "classes.xlsx", null, output.toByteArray());
			assertThat(service.run(Kind.CLASSES, file, true, "school-b", actor).errors())
					.singleElement().asString().contains("多个可用年级");
			assertThat(service.run(Kind.CLASSES, file, false, "school-a", actor).errors()).isEmpty();
			verify(academic).saveAdministrativeClass(isNull(), argThat(item -> "local".equals(item.getGradeId())));
		}
	}

	@Test
	void courseColumnBackfillsExistingAssignmentAndRepeatedImportDoesNotChangeIt() throws IOException {
		var academic = mock(AcademicDataService.class);
		var assignments = mock(TeacherTeachingAssignmentRepository.class);
		var service = new AcademicCatalogImportService(academic, mock(EducationDataScopeService.class),
				assignments, mock(EducationResourceTransactionLock.class));
		var actor = mock(org.springframework.security.core.Authentication.class);
		var term = new com.chronos.education.scheduling.model.AcademicTerm();
		term.setId("term");
		term.setTermCode("2026-2027-1");
		var teacher = new com.chronos.education.scheduling.model.TeacherAcademicProfile();
		teacher.setId("teacher");
		teacher.setTeacherNo("202600001");
		var subject = new Subject();
		subject.setId("subject");
		subject.setSubjectCode("MATH");
		var course = new com.chronos.education.scheduling.model.CourseCatalog();
		course.setId("course");
		course.setCourseCode("MATH01");
		course.setSubjectId(subject.getId());
		var existing = new com.chronos.education.scheduling.model.TeacherTeachingAssignment();
		existing.setId("assignment");
		existing.setAcademicTermId(term.getId());
		existing.setTeacherId(teacher.getId());
		existing.setSubjectId(subject.getId());
		existing.setWeeklyLessons(6);
		existing.setEnabled(false);
		when(academic.terms(any())).thenReturn(List.of(term));
		when(academic.teachers(any(), eq("school-a"))).thenReturn(List.of(teacher));
		when(academic.subjects()).thenReturn(List.of(subject));
		when(academic.visibleCourses(any())).thenReturn(List.of(course));
		when(assignments.findByAcademicTermIdInOrderByCreateTimeDesc(List.of("term"))).thenReturn(List.of(existing));
		when(assignments.bindMissingCourse("assignment", "course")).thenReturn(1);
		try (var book = new XSSFWorkbook(new ByteArrayInputStream(service.template(Kind.ASSIGNMENTS)));
				var output = new ByteArrayOutputStream()) {
			var row = book.getSheetAt(0).createRow(1);
			String[] values = {"2026-2027-1", "202600001", "MATH", "", "", "2", "TEACHER", "MATH01"};
			for (int index = 0; index < values.length; index++) row.createCell(index).setCellValue(values[index]);
			book.write(output);
			var file = new MockMultipartFile("file", "assignments.xlsx", null, output.toByteArray());
			assertThat(service.run(Kind.ASSIGNMENTS, file, true, "school-a", actor, false).errors())
					.singleElement().asString().contains("修改权限");
			assertThat(service.run(Kind.ASSIGNMENTS, file, true, "school-a", actor, true).errors()).isEmpty();
			verify(assignments, never()).bindMissingCourse(any(), any());
			assertThat(service.run(Kind.ASSIGNMENTS, file, false, "school-a", actor, true).errors()).isEmpty();
			existing.setCourseId("course");
			assertThat(service.run(Kind.ASSIGNMENTS, file, false, "school-a", actor, true).errors()).isEmpty();
			verify(assignments).bindMissingCourse("assignment", "course");
			verify(academic, never()).saveTeachingAssignment(any(), any());
		}
	}

	private com.chronos.education.scheduling.model.EducationGrade grade(String id) {
		var grade = new com.chronos.education.scheduling.model.EducationGrade();
		grade.setId(id);
		grade.setEnrollmentYear(2026);
		grade.setEnabled(true);
		return grade;
	}

	private MockMultipartFile workbook(AcademicCatalogImportService service, int rows) throws IOException {
		try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(service.template(Kind.SUBJECTS)));
				var output = new ByteArrayOutputStream()) {
			for (int index = 1; index <= rows; index++) {
				var row = workbook.getSheetAt(0).createRow(index);
				row.createCell(0).setCellValue("SUB001");
				row.createCell(1).setCellValue("测试学科");
			}
			workbook.write(output);
			return new MockMultipartFile("file", "subjects.xlsx", null, output.toByteArray());
		}
	}
}
