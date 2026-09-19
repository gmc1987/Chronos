package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.*;
import com.chronos.education.scheduling.model.*;
import com.chronos.file.model.ManagedFileView;
import com.chronos.file.service.ManagedFileService;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Data-center application boundary. Dashboards read persisted snapshots, never live cross-table joins. */
@Service
@Transactional
public class EducationDataCenterService {
 private final DataMetricDefinitionRepository definitions;
 private final DataDailySnapshotRepository snapshots;
 private final DataReportTaskRepository reports;
 private final DataQualityIssueRepository issues;
 private final EducationDataScopeService scopes;
 private final ManagedFileService files;
 private final StudentProfileRepository students;
 private final AdministrativeClassRepository classes;
 private final ExamSessionRepository exams;

 public EducationDataCenterService(DataMetricDefinitionRepository definitions, DataDailySnapshotRepository snapshots,
   DataReportTaskRepository reports, DataQualityIssueRepository issues, EducationDataScopeService scopes,
   ManagedFileService files, StudentProfileRepository students, AdministrativeClassRepository classes,
   ExamSessionRepository exams) {
  this.definitions=definitions; this.snapshots=snapshots; this.reports=reports; this.issues=issues;
  this.scopes=scopes; this.files=files; this.students=students; this.classes=classes; this.exams=exams;
 }
 public List<DataMetricDefinition> metricDefinitions() { return definitions.findByEnabledTrueOrderByCategoryAscMetricCodeAsc(); }
 public List<DataDailySnapshot> dashboard(String dashboard, LocalDate date, String campusId, Authentication user) {
  assertCampus(campusId,user);
  campusId = normalizedCampus(campusId);
  Set<String> categories = switch (dashboard) {
   case "academic" -> Set.of("ACADEMIC");
   case "scheduling" -> Set.of("SCHEDULING");
   case "exams" -> Set.of("EXAM");
   default -> throw new IllegalArgumentException("未知仪表板");
  };
  return snapshots.findBySnapshotDateAndCampusIdOrderByMetricCode(date,campusId).stream()
    .filter(s -> definitions.findByMetricCode(s.getMetricCode()).map(d -> categories.contains(d.getCategory())).orElse(false)).toList();
 }
 public List<DataDailySnapshot> takeSnapshot(LocalDate date, String campusId, Authentication user) {
  assertCampus(campusId,user);
  campusId = normalizedCampus(campusId);
  List<DataDailySnapshot> result = new ArrayList<>();
  for (DataMetricDefinition definition : metricDefinitions()) {
   DataDailySnapshot value = snapshots.findBySnapshotDateAndCampusIdAndMetricCode(date,campusId,definition.getMetricCode())
     .orElseGet(DataDailySnapshot::new);
   value.setSnapshotDate(date); value.setCampusId(campusId); value.setMetricCode(definition.getMetricCode());
   // Producers may supply richer dimensions later; the persisted daily value is deliberately stable and repeatable.
   if (value.getMetricValue() == null) value.setMetricValue(measure(definition.getMetricCode(), campusId));
   result.add(snapshots.save(value));
  }
   return result;
  }
  private java.math.BigDecimal measure(String code, String campusId) {
   if ("STUDENT_COUNT".equals(code)) {
    if (campusId.isBlank()) return java.math.BigDecimal.valueOf(students.count());
    var ids=classes.findByCampusIdIn(List.of(campusId)).stream().map(AdministrativeClass::getId).toList();
    return java.math.BigDecimal.valueOf(students.findAll().stream().filter(s -> ids.contains(s.getAdministrativeClassId())).count());
   }
   if ("ACTIVE_CLASS_COUNT".equals(code)) return java.math.BigDecimal.valueOf(campusId.isBlank() ? classes.count() : classes.findByCampusIdIn(List.of(campusId)).size());
   if ("EXAM_SESSION_COUNT".equals(code)) return java.math.BigDecimal.valueOf(exams.findByExamDateBetweenAndStatus(
     LocalDate.now(), LocalDate.now(), "PUBLISHED").size());
   return java.math.BigDecimal.ZERO;
  }
 public DataReportTask requestReport(String type, LocalDate date, String campusId, Authentication user) {
  assertCampus(campusId,user);
  campusId = normalizedCampus(campusId);
  DataReportTask task = reports.findByReportTypeAndRequestedDateAndCampusId(type,date,campusId).orElseGet(DataReportTask::new);
  task.setReportType(type); task.setRequestedDate(date); task.setCampusId(campusId); task.setStatus("PENDING");
  task = reports.save(task); generateReport(task.getId(), user.getName()); return task;
 }
 @Async("scheduleGenerationExecutor")
 public void generateReport(String taskId, String actor) {
  reports.findById(taskId).ifPresent(task -> {
   try {
    task.setStatus("RUNNING"); reports.save(task);
    List<DataDailySnapshot> rows = snapshots.findBySnapshotDateAndCampusIdOrderByMetricCode(task.getRequestedDate(),task.getCampusId());
    StringBuilder csv = new StringBuilder("metricCode,value,date,campusId\n");
    rows.forEach(row -> csv.append(row.getMetricCode()).append(',').append(row.getMetricValue()).append(',')
      .append(row.getSnapshotDate()).append(',').append(Optional.ofNullable(row.getCampusId()).orElse("")).append('\n'));
    MultipartFile upload = new BytesFile("data-center-"+task.getReportType()+".csv",csv.toString().getBytes(StandardCharsets.UTF_8));
    ManagedFileView file = files.upload(upload,"EDUCATION_DATA_REPORT",task.getId(),
      new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(actor,List.of()));
    task.setFileId(file.id()); task.setStatus("COMPLETED"); reports.save(task);
   } catch (Exception e) { task.setStatus("FAILED"); task.setErrorMessage(e.getMessage()); reports.save(task); }
  });
 }
 public List<DataReportTask> reports() { return reports.findTop50ByOrderByCreateTimeDesc(); }
 public List<DataQualityIssue> issues(String status) { return status == null ? issues.findAll() : issues.findByStatusOrderByDueDateAsc(status); }
 public DataQualityIssue createIssue(DataQualityIssue issue) { if (issue.getStatus()==null) issue.setStatus("OPEN"); return issues.save(issue); }
 public DataQualityIssue transitionIssue(String id,String status,String resolution) {
  DataQualityIssue issue=issues.findById(id).orElseThrow(() -> new IllegalArgumentException("质量问题不存在"));
  if (!Set.of("OPEN","IN_PROGRESS","RESOLVED","REJECTED").contains(status)) throw new IllegalArgumentException("无效问题状态");
  issue.setStatus(status); issue.setResolution(resolution); if ("RESOLVED".equals(status)) issue.setResolvedAt(LocalDateTime.now());
  return issues.save(issue);
 }
 private void assertCampus(String campusId, Authentication user) {
  if (campusId == null || campusId.isBlank()) return;
  EducationDataScope scope=scopes.resolve(user.getName());
  if (!scope.fullAccess() && !scope.campusIds().contains(campusId)) throw new org.springframework.security.access.AccessDeniedException("无权访问校区数据");
 }
 private static String normalizedCampus(String campusId) { return campusId == null ? "" : campusId.trim(); }
 private static final class BytesFile implements MultipartFile {
  private final String name; private final byte[] bytes;
  BytesFile(String name,byte[] bytes){this.name=name;this.bytes=bytes;}
  public String getName(){return name;} public String getOriginalFilename(){return name;} public String getContentType(){return "text/csv";}
  public boolean isEmpty(){return bytes.length==0;} public long getSize(){return bytes.length;} public byte[] getBytes(){return bytes;}
  public java.io.InputStream getInputStream(){return new java.io.ByteArrayInputStream(bytes);}
  public void transferTo(java.io.File dest) throws java.io.IOException { java.nio.file.Files.write(dest.toPath(),bytes); }
 }
}
