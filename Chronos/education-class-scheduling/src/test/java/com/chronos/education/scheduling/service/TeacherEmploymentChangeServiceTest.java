package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chronos.Idao.IEmployeeAssignmentRepository;
import com.chronos.Idao.IOrganizationRepository;
import com.chronos.education.scheduling.dao.AdministrativeClassRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.EducationUserBindingRepository;
import com.chronos.education.scheduling.dao.TeacherAcademicProfileRepository;
import com.chronos.education.scheduling.dao.TeacherEmploymentChangeRepository;
import com.chronos.education.scheduling.dao.TeacherTeachingAssignmentRepository;
import com.chronos.education.scheduling.model.TeacherAcademicProfile;
import com.chronos.education.scheduling.model.TeacherEmploymentChange;
import com.chronos.education.scheduling.model.TeacherTeachingAssignment;
import com.chronos.education.scheduling.model.dto.TeacherEmploymentChangeCommand;
import com.chronos.model.pojo.EmployeeAssignment;
import com.chronos.model.pojo.Organization;
import com.chronos.service.iService.IAuditLogService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TeacherEmploymentChangeServiceTest {
	private TeacherAcademicProfileRepository teachers;
	private TeacherEmploymentChangeRepository changes;
	private TeacherTeachingAssignmentRepository duties;
	private IOrganizationRepository organizations;
	private IEmployeeAssignmentRepository employeeAssignments;
	private TeacherEmploymentChangeService service;

	@BeforeEach
	void setUp() {
		teachers = mock(TeacherAcademicProfileRepository.class);
		changes = mock(TeacherEmploymentChangeRepository.class);
		duties = mock(TeacherTeachingAssignmentRepository.class);
		organizations = mock(IOrganizationRepository.class);
		employeeAssignments = mock(IEmployeeAssignmentRepository.class);
		service = new TeacherEmploymentChangeService(
				teachers, changes, duties, mock(CourseOfferingRepository.class),
				mock(AdministrativeClassRepository.class), mock(EducationUserBindingRepository.class),
				organizations, employeeAssignments, mock(IAuditLogService.class));
		when(changes.save(any(TeacherEmploymentChange.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void transferRegistrationRejectsUnknownSchool() {
		when(teachers.findLockedById("teacher-1")).thenReturn(Optional.of(activeTeacher()));
		when(organizations.findById("missing")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.register("teacher-1",
				new TeacherEmploymentChangeCommand("TRANSFER", "missing", LocalDate.now(), "调动"),
				"admin"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("目标学校不存在");
	}

	@Test
	void transferApplyWaitsForTeachingHandover() {
		arrangeDueTransfer();
		when(duties.findByTeacherIdAndEnabledTrue("teacher-1"))
				.thenReturn(List.of(mock(TeacherTeachingAssignment.class)));

		assertThatThrownBy(() -> service.apply("change-1", "SYSTEM"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("完成交接");
		assertThat(teachers.findLockedById("teacher-1").orElseThrow().getDepartmentId())
				.isEqualTo("old-school");
	}

	@Test
	void transferApplyRequiresIamAssignmentAtTargetSchool() {
		arrangeDueTransfer();
		when(employeeAssignments.findCurrentPrimaryAssignment("employee-1", LocalDate.now()))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.apply("change-1", "SYSTEM"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("机构员工管理");
	}

	@Test
	void transferApplyUpdatesTeacherOnlyAfterHandoverAndIamAlignment() {
		arrangeDueTransfer();
		EmployeeAssignment assignment = new EmployeeAssignment();
		assignment.setOrganizationId("new-school");
		when(employeeAssignments.findCurrentPrimaryAssignment("employee-1", LocalDate.now()))
				.thenReturn(Optional.of(assignment));

		TeacherEmploymentChange applied = service.apply("change-1", "SYSTEM");
		assertThat(applied.getStatus()).isEqualTo("APPLIED");
		assertThat(teachers.findLockedById("teacher-1").orElseThrow().getDepartmentId())
				.isEqualTo("new-school");
		verify(teachers).save(any(TeacherAcademicProfile.class));
	}

	private void arrangeDueTransfer() {
		TeacherAcademicProfile teacher = activeTeacher();
		when(teachers.findLockedById("teacher-1")).thenReturn(Optional.of(teacher));
		TeacherEmploymentChange change = new TeacherEmploymentChange();
		change.setId("change-1");
		change.setTeacherId("teacher-1");
		change.setChangeType("TRANSFER");
		change.setFromStatus("ACTIVE");
		change.setToStatus("ACTIVE");
		change.setFromDepartmentId("old-school");
		change.setToDepartmentId("new-school");
		change.setEffectiveDate(LocalDate.now());
		change.setStatus("SCHEDULED");
		when(changes.findLockedById("change-1")).thenReturn(Optional.of(change));
		Organization target = new Organization();
		target.setOrganizationType("SCHOOL");
		target.setStatus(1);
		when(organizations.findById("new-school")).thenReturn(Optional.of(target));
	}

	private TeacherAcademicProfile activeTeacher() {
		TeacherAcademicProfile teacher = new TeacherAcademicProfile();
		teacher.setId("teacher-1");
		teacher.setEmployeeId("employee-1");
		teacher.setDepartmentId("old-school");
		teacher.setEmploymentStatus("ACTIVE");
		teacher.setEnabled(true);
		return teacher;
	}
}
