package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.dao.AdministrativeClassRepository;
import com.chronos.education.scheduling.dao.CombinedOfferingSourceClassRepository;
import com.chronos.education.scheduling.model.AcademicTerm;
import com.chronos.education.scheduling.model.AdministrativeClass;
import com.chronos.education.scheduling.model.CombinedOfferingSourceClass;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.education.scheduling.model.ScheduleEntry;
import com.chronos.education.scheduling.model.ScheduleEntryView;
import com.chronos.education.scheduling.model.ScheduleOccurrenceView;
import com.chronos.service.iService.IAuditLogService;

class TeachingSuspensionServiceTest {
	private static final String TERM = "SCHOOL:2026-2027-1";
	private static final LocalDate DATE = LocalDate.of(2026, 11, 19);
	private static final TeachingSuspensionService.Selection SELECTION = new TeachingSuspensionService.Selection(
			"school", TERM, DATE, DATE, null, "major", "class-a");

	private final AcademicTermRepository terms = mock(AcademicTermRepository.class);
	private final AdministrativeClassRepository classes = mock(AdministrativeClassRepository.class);
	private final CombinedOfferingSourceClassRepository combinedClasses = mock(CombinedOfferingSourceClassRepository.class);
	private final ClassSchedulingService scheduling = mock(ClassSchedulingService.class);
	private final ScheduleOccurrenceService calendar = mock(ScheduleOccurrenceService.class);
	private final SchedulePlanVersionService versions = mock(SchedulePlanVersionService.class);
	private final EducationDataScopeService scopes = mock(EducationDataScopeService.class);
	private final EducationResourceTransactionLock lock = mock(EducationResourceTransactionLock.class);
	private final IAuditLogService audit = mock(IAuditLogService.class);
	private final TeachingSuspensionService service = new TeachingSuspensionService(
			terms, classes, combinedClasses, scheduling, calendar, versions, scopes, lock, audit);
	private final ScheduleEntryView published = entry(1L);

	@BeforeEach
	void setUp() {
		var term = new AcademicTerm();
		term.setOrganizationId("school");
		term.setStartDate(DATE.minusWeeks(10));
		term.setEndDate(DATE.plusWeeks(2));
		when(terms.findByTermCode(TERM)).thenReturn(Optional.of(term));
		var scope = new EducationDataScope(false, Set.of("school"), Set.of(), Set.of(), Set.of());
		when(scopes.resolve("operator")).thenReturn(scope);
		when(scopes.scheduleOrganizationCampuses(scope, "school")).thenReturn(Set.of("school"));

		var selectedClass = new AdministrativeClass();
		selectedClass.setId("class-a");
		selectedClass.setCampusId("school");
		selectedClass.setMajorId("major");
		when(classes.findByCampusIdIn(List.of("school"))).thenReturn(List.of(selectedClass));
		var offering = new CourseOffering();
		offering.setId("offering");
		offering.setCampusId("school");
		offering.setAdministrativeClassId("class-a");
		when(scheduling.offerings(TERM)).thenReturn(List.of(offering));
		when(scheduling.schedule(TERM)).thenReturn(List.of(published));

		List<ScheduleEntry> snapshot = List.of(new ScheduleEntry());
		when(versions.latestPublishedEntries(TERM)).thenReturn(snapshot);
		when(calendar.publishedOccurrencesBetween(TERM, DATE, DATE, snapshot)).thenReturn(List.of(
				new ScheduleOccurrenceView("entry@date", DATE, published, "SCHEDULED", null,
						null, null, 1, "room", null)));
	}

	@Test
	void combinedClassSpanningUnselectedClassesIsNeverSilentlySuspended() {
		var otherClass = new CombinedOfferingSourceClass();
		otherClass.setAdministrativeClassId("class-b");
		when(combinedClasses.findByOfferingIdOrderByAdministrativeClassId("offering"))
				.thenReturn(List.of(otherClass));
		var preview = service.preview(SELECTION, "operator");

		assertThat(preview.impacts()).singleElement().satisfies(impact ->
				assertThat(impact.conflict()).contains("所选范围外"));
		assertThatThrownBy(() -> service.apply(new TeachingSuspensionService.Apply(
				SELECTION, preview.revision(), "临时停课"), "operator"))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("冲突");
		verifyNoInteractions(audit);
	}

	@Test
	void applyRechecksPublishedEntryAfterLockAndRejectsStalePreview() {
		var preview = service.preview(SELECTION, "operator");
		assertThat(preview.impacts().getFirst().conflict()).isNull();
		when(scheduling.schedule(TERM)).thenReturn(List.of(entry(2L)));

		assertThatThrownBy(() -> service.apply(new TeachingSuspensionService.Apply(
				SELECTION, preview.revision(), "临时停课"), "operator"))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("重新预览");
		verify(lock).lockSemester(TERM);
		verifyNoInteractions(audit);
	}

	private ScheduleEntryView entry(long version) {
		return new ScheduleEntryView("entry", TERM, "offering", "COURSE", "课程", "班级",
				"教师", "room", "教室", 4, 1, 1, "ALL", 1, 20, "SCHEDULED", false, version);
	}
}
