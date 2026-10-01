package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.chronos.education.scheduling.dao.*;
import com.chronos.education.scheduling.model.DataQualityIssue;
import com.chronos.education.scheduling.model.DataDailySnapshot;
import com.chronos.education.scheduling.model.DataMetricDefinition;
import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.education.scheduling.model.AdministrativeClass;
import com.chronos.file.service.ManagedFileService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.junit.jupiter.api.Test;

class EducationDataCenterServiceTest {
 @Test
 void qualityIssueLifecyclePersistsResolution() {
  DataQualityIssueRepository issues=mock(DataQualityIssueRepository.class);
  DataQualityIssue issue=new DataQualityIssue(); issue.setId("issue-1"); issue.setStatus("OPEN");
  when(issues.findById("issue-1")).thenReturn(java.util.Optional.of(issue));
  when(issues.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
  EducationDataCenterService service=new EducationDataCenterService(
    mock(DataMetricDefinitionRepository.class),mock(DataDailySnapshotRepository.class),
    mock(DataReportTaskRepository.class),issues,mock(EducationDataScopeService.class),mock(ManagedFileService.class),
    mock(StudentProfileRepository.class),mock(AdministrativeClassRepository.class),mock(ExamSessionRepository.class));
  DataQualityIssue updated=service.transitionIssue("issue-1","RESOLVED","fixed");
  assertThat(updated.getStatus()).isEqualTo("RESOLVED");
  assertThat(updated.getResolution()).isEqualTo("fixed");
  assertThat(updated.getResolvedAt()).isNotNull();
  verify(issues).save(issue);
 }

 @Test
 void snapshotUsesRealSourcesAndSkipsUnavailableMetrics() {
  DataMetricDefinitionRepository definitions = mock(DataMetricDefinitionRepository.class);
  DataDailySnapshotRepository snapshots = mock(DataDailySnapshotRepository.class);
  EducationDataScopeService scopes = mock(EducationDataScopeService.class);
  StudentProfileRepository students = mock(StudentProfileRepository.class);
  AdministrativeClassRepository classes = mock(AdministrativeClassRepository.class);
  DataMetricDefinition studentsMetric = metric("STUDENT_COUNT");
  DataMetricDefinition utilizationMetric = metric("SCHEDULE_UTILIZATION");
  DataMetricDefinition conflictMetric = metric("SCHEDULE_CONFLICT_COUNT");
  DataMetricDefinition invigilationMetric = metric("INVIGILATION_LOAD");
  AdministrativeClass activeClass = new AdministrativeClass();
  activeClass.setId("class-1");
  activeClass.setStatus("ACTIVE");
  com.chronos.education.scheduling.model.StudentProfile student =
    new com.chronos.education.scheduling.model.StudentProfile();
  student.setAdministrativeClassId("class-1");
  student.setEnrollmentStatus("ACTIVE");
  when(definitions.findByEnabledTrueOrderByCategoryAscMetricCodeAsc())
    .thenReturn(List.of(studentsMetric, utilizationMetric, conflictMetric, invigilationMetric));
  when(scopes.resolve("admin")).thenReturn(new EducationDataScope(
    true, Set.of(), Set.of(), Set.of(), Set.of(), Set.of()));
  when(classes.findAll()).thenReturn(List.of(activeClass));
  when(students.findAll()).thenReturn(List.of(student));
  when(snapshots.findBySnapshotDateAndCampusIdAndMetricCode(
    any(), any(), any())).thenReturn(java.util.Optional.empty());
  when(snapshots.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

  EducationDataCenterService service = new EducationDataCenterService(
    definitions, snapshots, mock(DataReportTaskRepository.class),
    mock(DataQualityIssueRepository.class), scopes, mock(ManagedFileService.class),
    students, classes, mock(ExamSessionRepository.class));

  List<DataDailySnapshot> result = service.takeSnapshot(
    LocalDate.of(2026, 9, 20), null,
    new UsernamePasswordAuthenticationToken("admin", "n/a"));

  assertThat(result).hasSize(1);
  assertThat(result.get(0).getMetricCode()).isEqualTo("STUDENT_COUNT");
  assertThat(result.get(0).getMetricValue()).isEqualByComparingTo("1");
  verify(snapshots, times(1)).save(any(DataDailySnapshot.class));
 }

 @Test
 void repeatedSnapshotUpdatesTheSamePersistedRow() {
  DataMetricDefinitionRepository definitions = mock(DataMetricDefinitionRepository.class);
  DataDailySnapshotRepository snapshots = mock(DataDailySnapshotRepository.class);
  EducationDataScopeService scopes = mock(EducationDataScopeService.class);
  StudentProfileRepository students = mock(StudentProfileRepository.class);
  AdministrativeClassRepository classes = mock(AdministrativeClassRepository.class);
  DataMetricDefinition metric = metric("STUDENT_COUNT");
  AdministrativeClass activeClass = new AdministrativeClass();
  activeClass.setId("class-1");
  activeClass.setStatus("ACTIVE");
  DataDailySnapshot existing = new DataDailySnapshot();
  existing.setMetricCode("STUDENT_COUNT");
  when(definitions.findByEnabledTrueOrderByCategoryAscMetricCodeAsc()).thenReturn(List.of(metric));
  when(scopes.resolve("admin")).thenReturn(new EducationDataScope(
    true, Set.of(), Set.of(), Set.of(), Set.of(), Set.of()));
  when(classes.findAll()).thenReturn(List.of(activeClass));
  when(students.findAll()).thenReturn(List.of());
  when(snapshots.findBySnapshotDateAndCampusIdAndMetricCode(
    any(), any(), eq("STUDENT_COUNT"))).thenReturn(java.util.Optional.of(existing));
  when(snapshots.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

  EducationDataCenterService service = new EducationDataCenterService(
    definitions, snapshots, mock(DataReportTaskRepository.class),
    mock(DataQualityIssueRepository.class), scopes, mock(ManagedFileService.class),
    students, classes, mock(ExamSessionRepository.class));
  LocalDate date = LocalDate.of(2026, 9, 20);

  List<DataDailySnapshot> first = service.takeSnapshot(
    date, null, new UsernamePasswordAuthenticationToken("admin", "n/a"));
  List<DataDailySnapshot> second = service.takeSnapshot(
    date, null, new UsernamePasswordAuthenticationToken("admin", "n/a"));

  assertThat(first).containsExactly(existing);
  assertThat(second).containsExactly(existing);
  verify(snapshots, times(2)).save(existing);
 }

 @Test
 void dashboardDoesNotInventZeroValuesBeforeSnapshotExists() {
  DataMetricDefinitionRepository definitions = mock(DataMetricDefinitionRepository.class);
  DataDailySnapshotRepository snapshots = mock(DataDailySnapshotRepository.class);
  EducationDataScopeService scopes = mock(EducationDataScopeService.class);
  when(scopes.resolve("admin")).thenReturn(new EducationDataScope(
    true, Set.of(), Set.of(), Set.of(), Set.of(), Set.of()));
  when(snapshots.findBySnapshotDateAndCampusIdOrderByMetricCode(any(), any()))
    .thenReturn(List.of());
  when(definitions.findByMetricCode("STUDENT_COUNT"))
    .thenReturn(java.util.Optional.of(metric("STUDENT_COUNT")));

  EducationDataCenterService service = new EducationDataCenterService(
    definitions, snapshots, mock(DataReportTaskRepository.class),
    mock(DataQualityIssueRepository.class), scopes, mock(ManagedFileService.class),
    mock(StudentProfileRepository.class), mock(AdministrativeClassRepository.class),
    mock(ExamSessionRepository.class));

  assertThat(service.dashboard("academic", LocalDate.of(2026, 9, 20), null,
    new UsernamePasswordAuthenticationToken("admin", "n/a"))).isEmpty();
 }

 @Test
 void courseAdjustmentProviderCountsPersistedRecordsForWholeCampus() {
  CourseAdjustmentRecordRepository records = mock(CourseAdjustmentRecordRepository.class);
  when(records.countByCreateTimeGreaterThanEqualAndCreateTimeLessThan(
    LocalDateTime.of(2026, 9, 20, 0, 0),
    LocalDateTime.of(2026, 9, 21, 0, 0))).thenReturn(3L);
  PersistedCourseAdjustmentCountProvider provider = new PersistedCourseAdjustmentCountProvider(records);

  assertThat(provider.measure(LocalDate.of(2026, 9, 20), "",
    new EducationDataScope(true, Set.of(), Set.of(), Set.of(), Set.of(), Set.of())))
    .hasValueSatisfying(value -> assertThat(value).isEqualByComparingTo("3"));
  assertThat(provider.measure(LocalDate.of(2026, 9, 20), "campus-a",
    new EducationDataScope(true, Set.of(), Set.of(), Set.of(), Set.of(), Set.of()))).isEmpty();
 }

 @Test
 void snapshotPersistsStableMetricAndSourceVersionForRepeatableDashboardReads() {
  DataMetricDefinitionRepository definitions=mock(DataMetricDefinitionRepository.class);
  DataDailySnapshotRepository snapshots=mock(DataDailySnapshotRepository.class);
  DataMetricDefinition definition=metric("STUDENT_COUNT");
  when(definitions.findByEnabledTrueOrderByCategoryAscMetricCodeAsc()).thenReturn(List.of(definition));
  when(snapshots.findBySnapshotDateAndCampusIdAndMetricCode(
    LocalDate.of(2026,9,20),"QA-CAMPUS-20260920","STUDENT_COUNT")).thenReturn(java.util.Optional.empty());
  when(snapshots.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
  EducationDataScopeService scopes=mock(EducationDataScopeService.class);
  when(scopes.resolve("QA-DATA-20260920")).thenReturn(new EducationDataScope(
    false,Set.of("QA-CAMPUS-20260920"),Set.of(),Set.of(),Set.of(),Set.of()));
  EducationDataCenterService service=new EducationDataCenterService(
    definitions,snapshots,mock(DataReportTaskRepository.class),mock(DataQualityIssueRepository.class),
    scopes,mock(ManagedFileService.class),mock(StudentProfileRepository.class),
    mock(AdministrativeClassRepository.class),mock(ExamSessionRepository.class));

  List<DataDailySnapshot> result=service.takeSnapshot(
    LocalDate.of(2026,9,20),"QA-CAMPUS-20260920",
    new UsernamePasswordAuthenticationToken("QA-DATA-20260920",List.of()));

  assertThat(result).singleElement().satisfies(snapshot -> {
   assertThat(snapshot.getCampusId()).isEqualTo("QA-CAMPUS-20260920");
   assertThat(snapshot.getMetricCode()).isEqualTo("STUDENT_COUNT");
   assertThat(snapshot.getSourceVersion()).isEqualTo("v1");
   assertThat(snapshot.getMetricValue()).isEqualByComparingTo(BigDecimal.ZERO);
  });
  verify(snapshots).save(any(DataDailySnapshot.class));
 }

 @Test
 void dashboardRejectsCampusOutsideRestrictedScope() {
  EducationDataScopeService scopes=mock(EducationDataScopeService.class);
  when(scopes.resolve("QA-DATA-20260920")).thenReturn(new EducationDataScope(
    false,Set.of("QA-ALLOWED-CAMPUS-20260920"),Set.of(),Set.of(),Set.of(),Set.of()));
  EducationDataCenterService service=new EducationDataCenterService(
    mock(DataMetricDefinitionRepository.class),mock(DataDailySnapshotRepository.class),
    mock(DataReportTaskRepository.class),mock(DataQualityIssueRepository.class),scopes,
    mock(ManagedFileService.class),mock(StudentProfileRepository.class),
    mock(AdministrativeClassRepository.class),mock(ExamSessionRepository.class));

  assertThatThrownBy(() -> service.dashboard("academic",LocalDate.of(2026,9,20),
    "QA-FORBIDDEN-CAMPUS-20260920",
    new UsernamePasswordAuthenticationToken("QA-DATA-20260920",List.of())))
    .isInstanceOf(AccessDeniedException.class);
 }

 private static DataMetricDefinition metric(String code) {
  DataMetricDefinition metric = new DataMetricDefinition();
  metric.setMetricCode(code);
  metric.setCategory(code.startsWith("SCHEDULE") ? "SCHEDULING" : "ACADEMIC");
  metric.setSourceVersion("v1");
  return metric;
 }
}
