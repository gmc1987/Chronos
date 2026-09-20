package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.chronos.education.scheduling.dao.*;
import com.chronos.education.scheduling.model.DataQualityIssue;
import com.chronos.education.scheduling.model.DataDailySnapshot;
import com.chronos.education.scheduling.model.DataMetricDefinition;
import com.chronos.file.service.ManagedFileService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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
 void snapshotPersistsStableMetricAndSourceVersionForRepeatableDashboardReads() {
  DataMetricDefinitionRepository definitions=mock(DataMetricDefinitionRepository.class);
  DataDailySnapshotRepository snapshots=mock(DataDailySnapshotRepository.class);
  DataMetricDefinition definition=new DataMetricDefinition();
  definition.setMetricCode("QA_STUDENT_COUNT_20260920"); definition.setCategory("ACADEMIC");
  definition.setSourceVersion("qa-v1"); definition.setEnabled(true);
  when(definitions.findByEnabledTrueOrderByCategoryAscMetricCodeAsc()).thenReturn(List.of(definition));
  when(snapshots.findBySnapshotDateAndCampusIdAndMetricCode(
    LocalDate.of(2026,9,20),"QA-CAMPUS-20260920",definition.getMetricCode())).thenReturn(Optional.empty());
  when(snapshots.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
  EducationDataScopeService scopes=mock(EducationDataScopeService.class);
  when(scopes.resolve("QA-DATA-20260920")).thenReturn(new com.chronos.education.scheduling.model.EducationDataScope(
    false,java.util.Set.of("QA-CAMPUS-20260920"),java.util.Set.of(),java.util.Set.of(),java.util.Set.of()));
  EducationDataCenterService service=new EducationDataCenterService(
    definitions,snapshots,mock(DataReportTaskRepository.class),mock(DataQualityIssueRepository.class),
    scopes,mock(ManagedFileService.class),mock(StudentProfileRepository.class),
    mock(AdministrativeClassRepository.class),mock(ExamSessionRepository.class));

  List<DataDailySnapshot> result=service.takeSnapshot(
    LocalDate.of(2026,9,20),"QA-CAMPUS-20260920",
    new UsernamePasswordAuthenticationToken("QA-DATA-20260920",List.of()));

  assertThat(result).singleElement().satisfies(snapshot -> {
   assertThat(snapshot.getCampusId()).isEqualTo("QA-CAMPUS-20260920");
   assertThat(snapshot.getMetricCode()).isEqualTo("QA_STUDENT_COUNT_20260920");
   assertThat(snapshot.getSourceVersion()).isEqualTo("qa-v1");
   assertThat(snapshot.getMetricValue()).isEqualByComparingTo(BigDecimal.ZERO);
  });
  verify(snapshots).save(any(DataDailySnapshot.class));
 }

 @Test
 void snapshotRejectsCampusOutsideRestrictedScope() {
  EducationDataScopeService scopes=mock(EducationDataScopeService.class);
  when(scopes.resolve("QA-DATA-20260920")).thenReturn(new com.chronos.education.scheduling.model.EducationDataScope(
    false,java.util.Set.of("QA-ALLOWED-CAMPUS-20260920"),java.util.Set.of(),java.util.Set.of(),java.util.Set.of()));
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
}
