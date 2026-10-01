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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MultipartFile;

/** Data-center application boundary. Dashboards read persisted snapshots, never live cross-table joins. */
@Service
@Transactional
public class EducationDataCenterService {
 private final DataMetricDefinitionRepository definitions;
 private final DataDailySnapshotRepository snapshots;
 private final DataReportTaskRepository reports;
 private final DataQualityIssueRepository issues;
 private final DataQualityRuleRepository rules;
 private final EducationDataScopeService scopes;
 private final ManagedFileService files;
 private final StudentProfileRepository students;
 private final AdministrativeClassRepository classes;
 private final ExamSessionRepository exams;
 private final SchedulingUtilizationProvider schedulingUtilization;
 private final SchedulingConflictProvider schedulingConflicts;
 private final CourseAdjustmentCountProvider courseAdjustments;
 private final InvigilationWorkloadProvider invigilationWorkload;

 @org.springframework.beans.factory.annotation.Autowired
 public EducationDataCenterService(DataMetricDefinitionRepository definitions, DataDailySnapshotRepository snapshots,
   DataReportTaskRepository reports, DataQualityIssueRepository issues, EducationDataScopeService scopes,
   ManagedFileService files, StudentProfileRepository students, AdministrativeClassRepository classes,
   ExamSessionRepository exams, DataQualityRuleRepository rules,
   SchedulingUtilizationProvider schedulingUtilization, SchedulingConflictProvider schedulingConflicts,
   CourseAdjustmentCountProvider courseAdjustments, InvigilationWorkloadProvider invigilationWorkload) {
  this.definitions=definitions; this.snapshots=snapshots; this.reports=reports; this.issues=issues;
  this.rules=rules; this.scopes=scopes; this.files=files; this.students=students; this.classes=classes; this.exams=exams;
  this.schedulingUtilization=schedulingUtilization; this.schedulingConflicts=schedulingConflicts;
  this.courseAdjustments=courseAdjustments; this.invigilationWorkload=invigilationWorkload;
 }
 public EducationDataCenterService(DataMetricDefinitionRepository definitions, DataDailySnapshotRepository snapshots,
   DataReportTaskRepository reports, DataQualityIssueRepository issues, EducationDataScopeService scopes,
   ManagedFileService files, StudentProfileRepository students, AdministrativeClassRepository classes,
   ExamSessionRepository exams, DataQualityRuleRepository rules) {
  this(definitions,snapshots,reports,issues,scopes,files,students,classes,exams,rules,
    (date,campus,scope) -> Optional.empty(), (date,campus,scope) -> Optional.empty(),
    (date,campus,scope) -> Optional.empty(), (date,campus,scope) -> Optional.empty());
 }
 public EducationDataCenterService(DataMetricDefinitionRepository definitions, DataDailySnapshotRepository snapshots,
   DataReportTaskRepository reports, DataQualityIssueRepository issues, EducationDataScopeService scopes,
   ManagedFileService files, StudentProfileRepository students, AdministrativeClassRepository classes,
   ExamSessionRepository exams) {
  this(definitions, snapshots, reports, issues, scopes, files, students, classes, exams, null,
    (date,campus,scope) -> Optional.empty(), (date,campus,scope) -> Optional.empty(),
    (date,campus,scope) -> Optional.empty(), (date,campus,scope) -> Optional.empty());
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
   if ("SCHEDULE_UTILIZATION".equals(code)) return schedulingUtilization.measure(date,campusId,scope);
   if ("SCHEDULE_CONFLICT_COUNT".equals(code)) return schedulingConflicts.measure(date,campusId,scope);
   if ("COURSE_ADJUSTMENT_COUNT".equals(code)) return courseAdjustments.measure(date,campusId,scope);
   if ("INVIGILATION_LOAD".equals(code)) return invigilationWorkload.measure(date,campusId,scope);
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
  if ("COMPLETED".equals(task.getStatus()) && task.getFileId() != null
    && (task.getExpiresAt() == null || task.getExpiresAt().isAfter(LocalDateTime.now()))) {
   return task;
  }
  task.setReportType(type); task.setRequestedDate(date); task.setCampusId(campusId); task.setStatus("PENDING");
  task.setRequestedBy(user.getName()); task.setProgress(0); task.setExpiresAt(LocalDateTime.now().plusDays(7));
  task = reports.save(task); generateReport(task.getId(), user.getName()); return task;
 }
 @Scheduled(cron = "${chronos.education.data-center.snapshot-cron:0 15 1 * * *}")
 public void scheduledDailySnapshot() { takeSnapshot(LocalDate.now().minusDays(1), "", systemAuthentication()); }
 public DataReportTask retryReport(String id, Authentication user) {
  DataReportTask task=reports.findById(id).orElseThrow(() -> new IllegalArgumentException("报告任务不存在"));
  if (!"FAILED".equals(task.getStatus()) || task.getRetryCount() >= 3
    || (!task.getRequestedBy().equals(user.getName()) && !isPrivileged(user)))
   throw new org.springframework.security.access.AccessDeniedException("报告不可重试");
  task.setStatus("PENDING"); task.setRetryCount(task.getRetryCount()+1); task.setProgress(0); task.setErrorMessage(null);
  task=reports.save(task); generateReport(task.getId(),user.getName()); return task;
 }
 @Async("scheduleGenerationExecutor")
 public void generateReport(String taskId, String actor) {
  reports.findById(taskId).ifPresent(task -> {
   try {
    if (!"PENDING".equals(task.getStatus())) return;
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
    .filter(t -> isPrivileged(user) || user.getName().equals(t.getRequestedBy())
      || canReadCampus(t.getCampusId(), user))
    .peek(this::expireIfNeeded).toList();
 }
 public com.chronos.file.service.ManagedFileService.FileContent downloadReport(
   String id, Authentication user) {
  DataReportTask task = reports.findById(id).orElseThrow(() -> new IllegalArgumentException("报告任务不存在"));
  expireIfNeeded(task);
  if (!"COMPLETED".equals(task.getStatus()) || task.getFileId() == null) {
   throw new IllegalStateException("报告尚未完成");
  }
  assertCampus(task.getCampusId(), user);
  return files.read(task.getFileId(), user);
 }
 public List<DataQualityIssue> issues(String status, Authentication user) {
  List<DataQualityIssue> values = status == null ? issues.findAll() : issues.findByStatusOrderByDueDateAsc(status);
  return values.stream().filter(issue -> canReadCampus(issue.getCampusId(), user)).toList();
 }
 public List<DataQualityIssue> issues(String status) {
  return status == null ? issues.findAll() : issues.findByStatusOrderByDueDateAsc(status);
 }
 public DataQualityIssue createIssue(DataQualityIssue issue, Authentication user) {
  assertCampus(issue.getCampusId(), user);
  if (issue.getStatus()==null) issue.setStatus("OPEN");
  return issues.save(issue);
 }
 public DataQualityIssue createIssue(DataQualityIssue issue) {
  if (issue.getStatus() == null) issue.setStatus("OPEN");
  return issues.save(issue);
 }
 public DataQualityIssue transitionIssue(String id,String status,String resolution, Authentication user) {
  DataQualityIssue issue=issues.findById(id).orElseThrow(() -> new IllegalArgumentException("质量问题不存在"));
  if (!canReadCampus(issue.getCampusId(), user)) throw new AccessDeniedException("无权访问该质量问题");
  if (!Set.of("OPEN","ASSIGNED","IN_PROGRESS","RESOLVED","CLOSED","REJECTED").contains(status))
   throw new IllegalArgumentException("无效问题状态");
  if ("CLOSED".equals(status) && !"RESOLVED".equals(issue.getStatus()))
   throw new IllegalStateException("只有已解决的问题才能关闭");
  issue.setStatus(status); issue.setResolution(resolution); if ("RESOLVED".equals(status)) issue.setResolvedAt(LocalDateTime.now());
  return issues.save(issue);
 }
 public DataQualityIssue transitionIssue(String id, String status, String resolution) {
  DataQualityIssue issue = issues.findById(id).orElseThrow(() -> new IllegalArgumentException("质量问题不存在"));
  if (!Set.of("OPEN","ASSIGNED","IN_PROGRESS","RESOLVED","CLOSED","REJECTED").contains(status))
   throw new IllegalArgumentException("无效问题状态");
  issue.setStatus(status);
  issue.setResolution(resolution);
  if ("RESOLVED".equals(status)) issue.setResolvedAt(LocalDateTime.now());
  return issues.save(issue);
 }
 public List<DataQualityIssue> scanQuality(LocalDate date, String campusId, Authentication user) {
  assertCampus(campusId, user);
  if (rules == null) return List.of();
  String normalized = normalizedCampus(campusId);
  List<DataQualityIssue> found = new ArrayList<>();
  for (DataQualityRule rule : rules.findByEnabledTrueOrderByRuleCode()) {
   if (rule.getMetricCode() == null) continue;
   snapshots.findBySnapshotDateAndCampusIdAndMetricCode(date, normalized, rule.getMetricCode())
    .filter(snapshot -> matches(rule.getExpression(), snapshot.getMetricValue()))
    .ifPresent(snapshot -> {
     if (issues.findFirstByRuleIdAndCampusIdAndMetricCodeAndStatusIn(
       rule.getId(), normalized, rule.getMetricCode(), List.of("OPEN","ASSIGNED","IN_PROGRESS","RESOLVED")).isEmpty()) {
      DataQualityIssue issue = new DataQualityIssue();
      issue.setRuleId(rule.getId()); issue.setCampusId(normalized); issue.setMetricCode(rule.getMetricCode());
      issue.setTitle(rule.getRuleName()); issue.setDescription(rule.getExpression());
      issue.setSeverity(rule.getSeverity()); issue.setStatus("OPEN"); issue.setDetectedDate(date);
      found.add(issues.save(issue));
     }
    });
  }
  return found;
 }
 private boolean matches(String expression, java.math.BigDecimal value) {
  java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^\\s*(<=|>=|=|<|>)\\s*(-?\\d+(?:\\.\\d+)?)\\s*$").matcher(expression);
  if (!matcher.matches()) throw new IllegalArgumentException("不支持的质量规则表达式");
  int comparison = value.compareTo(new java.math.BigDecimal(matcher.group(2)));
  return switch (matcher.group(1)) { case "<" -> comparison < 0; case "<=" -> comparison <= 0;
   case ">" -> comparison > 0; case ">=" -> comparison >= 0; default -> comparison == 0; };
 }
 private void assertCampus(String campusId, Authentication user) {
  if (campusId == null || campusId.isBlank()) return;
  EducationDataScope scope=scopes.resolve(user.getName());
  if (!scope.fullAccess() && !scope.campusIds().contains(campusId)) throw new org.springframework.security.access.AccessDeniedException("无权访问校区数据");
 }
 private boolean canReadCampus(String campusId, Authentication user) {
  return campusId == null || campusId.isBlank() || scopes.resolve(user.getName()).fullAccess()
    || scopes.resolve(user.getName()).campusIds().contains(campusId)
    || (user.getName().equals("SYSTEM"));
 }
 private void expireIfNeeded(DataReportTask task) {
  if ("COMPLETED".equals(task.getStatus()) && task.getExpiresAt() != null
    && task.getExpiresAt().isBefore(LocalDateTime.now())) {
   task.setStatus("EXPIRED"); reports.save(task);
  }
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
