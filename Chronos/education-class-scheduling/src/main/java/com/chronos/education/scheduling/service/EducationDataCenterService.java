package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.*;
import com.chronos.education.scheduling.model.*;
import com.chronos.file.model.ManagedFileView;
import com.chronos.file.service.ManagedFileService;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
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
  final String requestedCampus = campusId;
  Set<String> categories = switch (dashboard) {
   case "academic" -> Set.of("ACADEMIC");
   case "scheduling" -> Set.of("SCHEDULING");
   case "exams" -> Set.of("EXAM");
   default -> throw new IllegalArgumentException("未知仪表板");
  };
  List<DataDailySnapshot> existing = snapshots.findBySnapshotDateAndCampusIdOrderByMetricCode(date,campusId).stream()
    .filter(s -> definitions.findByMetricCode(s.getMetricCode()).map(d -> categories.contains(d.getCategory())).orElse(false)).toList();
  return existing;
 }
 public List<DataDailySnapshot> takeSnapshot(LocalDate date, String campusId, Authentication user) {
  assertCampus(campusId,user);
  campusId = normalizedCampus(campusId);
  EducationDataScope scope = scopes.resolve(user.getName());
  List<DataDailySnapshot> result = new ArrayList<>();
  for (DataMetricDefinition definition : metricDefinitions()) {
   Optional<java.math.BigDecimal> measured = measure(definition.getMetricCode(), campusId, date, scope);
   if (measured.isEmpty()) {
    continue;
   }
   DataDailySnapshot value = snapshots.findBySnapshotDateAndCampusIdAndMetricCode(date,campusId,definition.getMetricCode())
     .orElseGet(DataDailySnapshot::new);
   value.setSnapshotDate(date); value.setCampusId(campusId); value.setMetricCode(definition.getMetricCode());
   value.setMetricValue(measured.get());
   value.setSourceVersion(definition.getSourceVersion());
   result.add(snapshots.save(value));
  }
   return result;
  }
  private Optional<java.math.BigDecimal> measure(
    String code,
    String campusId,
    LocalDate date,
    EducationDataScope scope) {
   if ("STUDENT_COUNT".equals(code)) {
    List<String> classIds = visibleClassIds(campusId, scope);
    return Optional.of(java.math.BigDecimal.valueOf(students.findAll().stream()
      .filter(s -> classIds.contains(s.getAdministrativeClassId()))
      .filter(s -> "ACTIVE".equals(s.getEnrollmentStatus()))
      .count()));
   }
   if ("ACTIVE_CLASS_COUNT".equals(code)) {
    return Optional.of(java.math.BigDecimal.valueOf(visibleClassIds(campusId, scope).stream()
      .map(classes::findById)
      .flatMap(Optional::stream)
      .filter(value -> "ACTIVE".equals(value.getStatus()))
      .count()));
   }
   if ("EXAM_SESSION_COUNT".equals(code) && scope.fullAccess() && campusId.isBlank()) {
    return Optional.of(java.math.BigDecimal.valueOf(exams.findByExamDateBetweenAndStatus(
      date, date, "PUBLISHED").size()));
   }
   return Optional.empty();
  }

  private List<String> visibleClassIds(String campusId, EducationDataScope scope) {
   if (!campusId.isBlank()) {
    return classes.findByCampusIdIn(List.of(campusId)).stream()
      .map(AdministrativeClass::getId)
      .toList();
   }
   if (scope.fullAccess()) {
    return classes.findAll().stream()
      .filter(value -> "ACTIVE".equals(value.getStatus()))
      .map(AdministrativeClass::getId)
      .toList();
   }
   if (scope.administrativeClassIds().isEmpty()
     && scope.gradeIds().isEmpty()
     && scope.campusIds().isEmpty()) {
    return List.of();
   }
   return classes.findVisible(
     scope.administrativeClassIds().stream().toList(),
     scope.gradeIds().stream().toList(),
     scope.campusIds().stream().toList()).stream()
     .filter(value -> "ACTIVE".equals(value.getStatus()))
     .map(AdministrativeClass::getId)
     .toList();
  }
 public DataReportTask requestReport(String type, LocalDate date, String campusId, Authentication user) {
  assertCampus(campusId,user);
  campusId = normalizedCampus(campusId);
  DataReportTask task = reports.findByReportTypeAndRequestedDateAndCampusIdAndRequestedBy(type,date,campusId,user.getName()).orElseGet(DataReportTask::new);
  task.setReportType(type); task.setRequestedDate(date); task.setCampusId(campusId); task.setStatus("PENDING");
  task.setRequestedBy(user.getName()); task.setProgress(0); task.setExpiresAt(LocalDateTime.now().plusDays(7));
  task = reports.save(task); generateReport(task.getId(), user.getName()); return task;
 }
 @Scheduled(cron = "${chronos.education.data-center.snapshot-cron:0 15 1 * * *}")
 public void scheduledDailySnapshot() { takeSnapshot(LocalDate.now().minusDays(1), "", systemAuthentication()); }
 public DataReportTask retryReport(String id, Authentication user) {
  DataReportTask task=reports.findById(id).orElseThrow(() -> new IllegalArgumentException("报告任务不存在"));
  if (!"FAILED".equals(task.getStatus()) || (!task.getRequestedBy().equals(user.getName()) && !isPrivileged(user)))
   throw new org.springframework.security.access.AccessDeniedException("报告不可重试");
  task.setStatus("PENDING"); task.setRetryCount(task.getRetryCount()+1); task.setProgress(0); task.setErrorMessage(null);
  task=reports.save(task); generateReport(task.getId(),user.getName()); return task;
 }
 @Async("scheduleGenerationExecutor")
 public void generateReport(String taskId, String actor) {
  reports.findById(taskId).ifPresent(task -> {
   try {
    task.setStatus("RUNNING"); task.setProgress(10); reports.save(task);
    List<DataDailySnapshot> rows = snapshots.findBySnapshotDateAndCampusIdOrderByMetricCode(task.getRequestedDate(),task.getCampusId());
    StringBuilder csv = new StringBuilder("metricCode,value,date,campusId\n");
    rows.forEach(row -> csv.append(row.getMetricCode()).append(',').append(row.getMetricValue()).append(',')
      .append(row.getSnapshotDate()).append(',').append(Optional.ofNullable(row.getCampusId()).orElse("")).append('\n'));
    MultipartFile upload = new BytesFile("data-center-"+task.getReportType()+".csv",csv.toString().getBytes(StandardCharsets.UTF_8));
    ManagedFileView file = files.upload(upload,"EDUCATION_DATA_REPORT",task.getId(),
      new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(actor,List.of()));
    task.setFileId(file.id()); task.setProgress(100); task.setStatus("COMPLETED"); reports.save(task);
   } catch (Exception e) { task.setStatus("FAILED"); task.setProgress(0); task.setErrorMessage(e.getMessage()); reports.save(task); }
  });
 }
 public List<DataReportTask> reports(Authentication user) {
  return reports.findTop50ByOrderByCreateTimeDesc().stream()
    .filter(t -> isPrivileged(user) || user.getName().equals(t.getRequestedBy())).toList();
 }
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
 private boolean isPrivileged(Authentication user) {
  return user.getAuthorities().stream().anyMatch(a -> Set.of("education:data-center:report","education:data-center:quality","ROLE_PLATFORM_ADMIN").contains(a.getAuthority()));
 }
 private Authentication systemAuthentication() {
  return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("SYSTEM", List.of());
 }
 private static final class BytesFile implements MultipartFile {
  private final String name; private final byte[] bytes;
  BytesFile(String name,byte[] bytes){this.name=name;this.bytes=bytes;}
  public String getName(){return name;} public String getOriginalFilename(){return name;} public String getContentType(){return "text/csv";}
  public boolean isEmpty(){return bytes.length==0;} public long getSize(){return bytes.length;} public byte[] getBytes(){return bytes;}
  public java.io.InputStream getInputStream(){return new java.io.ByteArrayInputStream(bytes);}
  public void transferTo(java.io.File dest) throws java.io.IOException { java.nio.file.Files.write(dest.toPath(),bytes); }
 }
}
