package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chronos.education.academic.dao.GraduationAuditRepository;
import com.chronos.education.scheduling.dao.AdministrativeClassRepository;
import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.dao.CombinedOfferingSourceClassRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.StudentProfileRepository;
import com.chronos.education.scheduling.dao.StudentStatusChangeRepository;
import com.chronos.education.scheduling.dao.TeachingClassMemberRepository;
import com.chronos.education.scheduling.model.AdministrativeClass;
import com.chronos.education.scheduling.model.CombinedOfferingSourceClass;
import com.chronos.education.scheduling.model.AcademicTerm;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.StudentProfile;
import com.chronos.education.scheduling.model.StudentStatusChange;
import com.chronos.education.scheduling.model.TeachingClassMember;
import com.chronos.education.scheduling.model.dto.StudentStatusChangeCommand;
import com.chronos.service.iService.IAuditLogService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StudentStatusChangeServiceTest {
	private StudentStatusChangeRepository changes;
	private StudentProfileRepository students;
	private AdministrativeClassRepository classes;
	private CombinedOfferingSourceClassRepository combinedSources;
	private CombinedOfferingService combinedOfferings;
	private AcademicDataService academicData;
	private CourseOfferingRepository offerings;
	private AcademicTermRepository terms;
	private TeachingClassMemberRepository members;
	private GraduationAuditRepository graduationAudits;
	private StudentStatusChangeService service;

	@BeforeEach
	void setUp() {
		changes = mock(StudentStatusChangeRepository.class);
		students = mock(StudentProfileRepository.class);
		classes = mock(AdministrativeClassRepository.class);
		combinedSources = mock(CombinedOfferingSourceClassRepository.class);
		combinedOfferings = mock(CombinedOfferingService.class);
		academicData = mock(AcademicDataService.class);
		offerings = mock(CourseOfferingRepository.class);
		terms = mock(AcademicTermRepository.class);
		members = mock(TeachingClassMemberRepository.class);
		graduationAudits = mock(GraduationAuditRepository.class);
		service = new StudentStatusChangeService(
				changes,
				students,
				classes,
				members,
				combinedSources,
				combinedOfferings,
				academicData,
				offerings,
				terms,
				graduationAudits,
				mock(IAuditLogService.class));
		when(changes.save(any(StudentStatusChange.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void suspendRequestCapturesCurrentStudentSnapshot() {
		StudentProfile student = activeStudent();
		when(students.findLockedById("student-1")).thenReturn(Optional.of(student));
		when(changes.existsByStudentIdAndStatus("student-1", "PENDING")).thenReturn(false);

		StudentStatusChange result = service.request(
				"student-1",
				new StudentStatusChangeCommand(
						"SUSPEND",
						null,
						LocalDate.of(2026, 10, 1),
						"因病休学"),
				"registrar");

		assertThat(result.getFromStatus()).isEqualTo("ACTIVE");
		assertThat(result.getToStatus()).isEqualTo("SUSPENDED");
		assertThat(result.getFromClassId()).isEqualTo("class-1");
		assertThat(result.getStatus()).isEqualTo("PENDING");
		assertThat(result.getRequestedBy()).isEqualTo("registrar");
	}

	@Test
	void approvalAppliesStatusAndWithdrawsActiveTeachingClasses() {
		StudentProfile student = activeStudent();
		StudentStatusChange change = pendingChange("SUSPEND", "SUSPENDED");
		TeachingClassMember member = new TeachingClassMember();
		member.setStudentId("student-1");
		member.setEnrollmentStatus("ENROLLED");
		member.setEnrollmentSource("MANUAL");
		when(changes.findLockedById("change-1")).thenReturn(Optional.of(change));
		when(students.findLockedById("student-1")).thenReturn(Optional.of(student));
		when(members.findByStudentIdAndEnrollmentStatus("student-1", "ENROLLED"))
				.thenReturn(List.of(member));

		StudentStatusChange result = service.approve("change-1", "同意", "registrar-manager");

		assertThat(student.getEnrollmentStatus()).isEqualTo("SUSPENDED");
		assertThat(member.getEnrollmentStatus()).isEqualTo("WITHDRAWN");
		assertThat(member.getWithdrawnAt()).isNotNull();
		assertThat(result.getStatus()).isEqualTo("APPROVED");
		assertThat(result.getAppliedAt()).isNotNull();
		verify(students).save(student);
		verify(members).save(member);
	}

	@Test
	void staleSnapshotCannotOverwriteChangedStudentProfile() {
		StudentProfile student = activeStudent();
		student.setAdministrativeClassId("class-2");
		StudentStatusChange change = pendingChange("SUSPEND", "SUSPENDED");
		when(changes.findLockedById("change-1")).thenReturn(Optional.of(change));
		when(students.findLockedById("student-1")).thenReturn(Optional.of(student));

		assertThatThrownBy(() -> service.approve("change-1", "同意", "registrar-manager"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("学生档案已发生变化，请驳回后重新申请");
	}

	@Test
	void futureApprovedChangeWaitsUntilEffectiveDate() {
		StudentStatusChange change = pendingChange("SUSPEND", "SUSPENDED");
		change.setEffectiveDate(LocalDate.now().plusDays(3));
		when(changes.findLockedById("change-1")).thenReturn(Optional.of(change));

		StudentStatusChange result = service.approve("change-1", "同意", "registrar-manager");

		assertThat(result.getStatus()).isEqualTo("APPROVED_PENDING");
		assertThat(result.getAppliedAt()).isNull();
		verify(changes).save(change);
	}

	@Test
	void approvedButNotYetEffectiveChangeBlocksAnotherRequest() {
		when(students.findLockedById("student-1")).thenReturn(Optional.of(activeStudent()));
		when(changes.existsByStudentIdAndStatus("student-1", "APPROVED_PENDING"))
				.thenReturn(true);

		assertThatThrownBy(() -> service.request("student-1",
				new StudentStatusChangeCommand("SUSPEND", null, LocalDate.now(), "再次申请"),
				"registrar"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("待生效");
	}

	@Test
	void futureTransferRejectsTargetClassChangedAfterApproval() {
		StudentProfile student = activeStudent();
		StudentStatusChange change = pendingChange("TRANSFER_CLASS", "ACTIVE");
		change.setToClassId("class-2");
		change.setStatus("APPROVED_PENDING");
		when(changes.findLockedById("change-1")).thenReturn(Optional.of(change));
		when(students.findLockedById("student-1")).thenReturn(Optional.of(student));
		when(classes.findById("class-1"))
				.thenReturn(Optional.of(classRecord("class-1", "campus-1", "grade-1", 2024)));
		when(classes.findById("class-2"))
				.thenReturn(Optional.of(classRecord("class-2", "campus-2", "grade-1", 2024)));

		assertThatThrownBy(() -> service.applyDue("change-1"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("同一校区");
		assertThat(student.getAdministrativeClassId()).isEqualTo("class-1");
	}

	@Test
	void approvedFutureChangeCanBeCancelledBeforeApplying() {
		StudentStatusChange change = pendingChange("SUSPEND", "SUSPENDED");
		change.setStatus("APPROVED_PENDING");
		when(changes.findLockedById("change-1")).thenReturn(Optional.of(change));

		assertThat(service.cancel("change-1", "registrar-manager").getStatus())
				.isEqualTo("CANCELLED");
		assertThat(change.getAppliedAt()).isNull();
	}

	@Test
	void appliedChangeCannotBeCancelled() {
		StudentStatusChange change = pendingChange("SUSPEND", "SUSPENDED");
		change.setStatus("APPROVED");
		when(changes.findLockedById("change-1")).thenReturn(Optional.of(change));

		assertThatThrownBy(() -> service.cancel("change-1", "registrar-manager"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("待生效");
	}

	@Test
	void transferClassRejectsAnotherSchoolOrGrade() {
		StudentProfile student = activeStudent();
		when(students.findLockedById("student-1")).thenReturn(Optional.of(student));
		when(classes.findById("class-1")).thenReturn(Optional.of(classRecord("class-1", "campus-1", "grade-1", 2024)));
		when(classes.findById("class-2")).thenReturn(Optional.of(classRecord("class-2", "campus-2", "grade-1", 2024)));
		when(classes.findById("class-3")).thenReturn(Optional.of(classRecord("class-3", "campus-1", "grade-2", 2025)));

		assertThatThrownBy(() -> service.request("student-1",
				new StudentStatusChangeCommand("TRANSFER_CLASS", "class-2", LocalDate.now(), "转班"), "registrar"))
				.hasMessage("目标行政班必须属于同一校区和专业");
		assertThatThrownBy(() -> service.request("student-1",
				new StudentStatusChangeCommand("TRANSFER_CLASS", "class-3", LocalDate.now(), "转班"), "registrar"))
				.hasMessage("转班必须在同一年级内办理");
	}

	@Test
	void retainGradeUpdatesEnrollmentYearWhenApproved() {
		StudentProfile student = activeStudent();
		AdministrativeClass target = classRecord("class-3", "campus-1", "grade-2", 2025);
		when(students.findLockedById("student-1")).thenReturn(Optional.of(student));
		when(classes.findById("class-1")).thenReturn(Optional.of(classRecord("class-1", "campus-1", "grade-1", 2024)));
		when(classes.findById("class-3")).thenReturn(Optional.of(target));
		StudentStatusChange change = service.request("student-1",
				new StudentStatusChangeCommand("RETAIN_GRADE", "class-3", LocalDate.now(), "留级"), "registrar");
		change.setId("change-1");
		when(changes.findLockedById("change-1")).thenReturn(Optional.of(change));
		when(students.findLockedById("student-1")).thenReturn(Optional.of(student));

		service.approve("change-1", "同意", "registrar-manager");

		assertThat(student.getGradeId()).isEqualTo("grade-2");
		assertThat(student.getGradeYear()).isEqualTo(2025);
		assertThat(student.getAdministrativeClassId()).isEqualTo("class-3");
	}

	@Test
	void resumeSynchronizesSourceClassOfferings() {
		StudentProfile student = activeStudent();
		student.setEnrollmentStatus("SUSPENDED");
		StudentStatusChange change = pendingChange("RESUME", "ACTIVE");
		change.setFromStatus("SUSPENDED");
		CombinedOfferingSourceClass source = new CombinedOfferingSourceClass();
		source.setOfferingId("offering-1");
		when(changes.findLockedById("change-1")).thenReturn(Optional.of(change));
		when(students.findLockedById("student-1")).thenReturn(Optional.of(student));
		when(combinedSources.findByAdministrativeClassId("class-1")).thenReturn(List.of(source));

		service.approve("change-1", "同意", "registrar-manager");

		assertThat(student.getEnrollmentStatus()).isEqualTo("ACTIVE");
		verify(combinedOfferings).sync("offering-1");
	}

	@Test
	void resumeRestoresOnlyMembershipsCapturedBySuspension() {
		StudentProfile student = activeStudent();
		TeachingClassMember member = new TeachingClassMember();
		member.setId("member-1");
		member.setStudentId("student-1");
		member.setOfferingId("offering-1");
		member.setEnrollmentStatus("ENROLLED");
		member.setEnrollmentSource("SOURCE_CLASS");
		StudentStatusChange suspend = pendingChange("SUSPEND", "SUSPENDED");
		when(changes.findLockedById("change-1")).thenReturn(Optional.of(suspend));
		when(students.findLockedById("student-1")).thenReturn(Optional.of(student));
		when(members.findByStudentIdAndEnrollmentStatus("student-1", "ENROLLED"))
				.thenReturn(List.of(member));
		service.approve("change-1", "同意", "registrar-manager");
		assertThat(suspend.getWithdrawnMembershipIds()).isEqualTo("member-1");

		StudentStatusChange resume = pendingChange("RESUME", "ACTIVE");
		resume.setFromStatus("SUSPENDED");
		when(changes.findLockedById("change-1")).thenReturn(Optional.of(resume));
		when(changes.findFirstByStudentIdAndChangeTypeAndStatusOrderByAppliedAtDesc(
				"student-1", "SUSPEND", "APPROVED")).thenReturn(Optional.of(suspend));
		when(members.findById("member-1")).thenReturn(Optional.of(member));
		service.approve("change-1", "同意", "registrar-manager");
		verify(academicData).restoreAfterStatusChange("offering-1", "student-1");
	}

	@Test
	void transferEnrollsTargetClassOfferingsInEffectiveTerm() {
		StudentProfile student = activeStudent();
		StudentStatusChange change = pendingChange("TRANSFER_CLASS", "ACTIVE");
		change.setToClassId("class-2");
		AcademicTerm term = new AcademicTerm();
		term.setTermCode("2026-2027-1");
		CourseOffering offering = new CourseOffering();
		offering.setId("target-offering");
		when(changes.findLockedById("change-1")).thenReturn(Optional.of(change));
		when(students.findLockedById("student-1")).thenReturn(Optional.of(student));
		when(classes.findById("class-1"))
				.thenReturn(Optional.of(classRecord("class-1", "campus-1", "grade-1", 2024)));
		when(classes.findById("class-2"))
				.thenReturn(Optional.of(classRecord("class-2", "campus-1", "grade-1", 2024)));
		when(terms.findByStartDateLessThanEqualAndEndDateGreaterThanEqualAndStatus(
				any(LocalDate.class), any(LocalDate.class), org.mockito.ArgumentMatchers.eq("ACTIVE")))
				.thenReturn(List.of(term));
		when(offerings.findByAdministrativeClassIdAndSemesterCodeAndStatus(
				"class-2", "2026-2027-1", "ACTIVE")).thenReturn(List.of(offering));

		service.approve("change-1", "同意", "registrar-manager");

		verify(academicData).enrollFromAdministrativeClass("target-offering", "student-1");
	}

	@Test
	void requesterCannotApproveOwnStatusChange() {
		StudentStatusChange change = pendingChange("SUSPEND", "SUSPENDED");
		change.setRequestedBy("registrar");
		when(changes.findLockedById("change-1")).thenReturn(Optional.of(change));

		assertThatThrownBy(() -> service.approve("change-1", "同意", "registrar"))
				.isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
				.hasMessage("学籍异动申请人不能审批自己的申请");
	}

	@Test
	void graduationRequiresApprovedGraduationAudit() {
		when(students.findLockedById("student-1")).thenReturn(Optional.of(activeStudent()));
		when(graduationAudits.existsByStudentIdAndStatus("student-1", "APPROVED"))
				.thenReturn(false);

		assertThatThrownBy(() -> service.request(
				"student-1",
				new StudentStatusChangeCommand("GRADUATE", null, LocalDate.now(), "完成学业"),
				"registrar"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("学生尚未通过毕业资格审核，不能办理毕业异动");
	}

	@Test
	void deferGraduationKeepsStudentActiveAndRecordsHistory() {
		StudentProfile student = activeStudent();
		when(students.findLockedById("student-1")).thenReturn(Optional.of(student));
		StudentStatusChange change = service.request(
				"student-1",
				new StudentStatusChangeCommand(
						"DEFER_GRADUATION", null, LocalDate.now(), "继续修读未完成课程"),
				"registrar");
		change.setId("change-1");
		when(changes.findLockedById("change-1")).thenReturn(Optional.of(change));

		service.approve("change-1", "同意延期", "registrar-manager");

		assertThat(student.getEnrollmentStatus()).isEqualTo("ACTIVE");
		assertThat(change.getStatus()).isEqualTo("APPROVED");
	}

	private StudentProfile activeStudent() {
		StudentProfile student = new StudentProfile();
		student.setId("student-1");
		student.setEnrollmentStatus("ACTIVE");
		student.setGradeId("grade-1");
		student.setGradeYear(2024);
		student.setMajorId("major-1");
		student.setAdministrativeClassId("class-1");
		return student;
	}

	private AdministrativeClass classRecord(String id, String campusId, String gradeId, int gradeYear) {
		AdministrativeClass value = new AdministrativeClass();
		value.setId(id);
		value.setCampusId(campusId);
		value.setMajorId("major-1");
		value.setGradeId(gradeId);
		value.setGradeYear(gradeYear);
		value.setStatus("ACTIVE");
		return value;
	}

	private StudentStatusChange pendingChange(String type, String targetStatus) {
		StudentStatusChange change = new StudentStatusChange();
		change.setId("change-1");
		change.setStudentId("student-1");
		change.setChangeType(type);
		change.setFromStatus("ACTIVE");
		change.setToStatus(targetStatus);
		change.setFromGradeId("grade-1");
		change.setToGradeId("grade-1");
		change.setFromMajorId("major-1");
		change.setToMajorId("major-1");
		change.setFromClassId("class-1");
		change.setToClassId("class-1");
		change.setStatus("PENDING");
		change.setEffectiveDate(LocalDate.now());
		return change;
	}
}
