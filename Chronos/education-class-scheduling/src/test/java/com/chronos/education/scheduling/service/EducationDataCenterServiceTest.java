package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.chronos.education.scheduling.dao.*;
import com.chronos.education.scheduling.model.DataQualityIssue;
import com.chronos.file.service.ManagedFileService;
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
}
