package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.dao.AdministrativeClassRepository;
import com.chronos.education.scheduling.dao.AdmissionCandidateRepository;
import com.chronos.education.scheduling.dao.CourseCatalogRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.StudentProfileRepository;
import com.chronos.education.scheduling.dao.TeacherAcademicProfileRepository;
import com.chronos.education.scheduling.dao.TeacherTeachingAssignmentRepository;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.dto.AdmissionDtos.EnrollmentCommand;
import com.chronos.education.scheduling.model.dto.AdmissionDtos.ReviewCommand;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 人工页面与 Agent 共用的开学准备批处理。dryRun 只生成候选统计，execute 才调用领域服务写入。
 */
@Service
public class SemesterPreparationExecutionService {
	private final AdmissionCandidateRepository admissionCandidates;
	private final AdministrativeClassRepository administrativeClasses;
	private final StudentProfileRepository students;
	private final AdmissionService admissions;
	private final AcademicTermRepository terms;
	private final TeacherTeachingAssignmentRepository teachingAssignments;
	private final CourseCatalogRepository courses;
	private final CourseOfferingRepository offerings;
	private final TeacherAcademicProfileRepository teachers;
	private final ClassSchedulingService scheduling;
	private final AcademicDataService academicData;
	private final JdbcTemplate jdbc;

	public SemesterPreparationExecutionService(
			AdmissionCandidateRepository admissionCandidates,
			AdministrativeClassRepository administrativeClasses,
			StudentProfileRepository students,
			AdmissionService admissions,
			AcademicTermRepository terms,
			TeacherTeachingAssignmentRepository teachingAssignments,
			CourseCatalogRepository courses,
			CourseOfferingRepository offerings,
			TeacherAcademicProfileRepository teachers,
			ClassSchedulingService scheduling,
			AcademicDataService academicData,
			JdbcTemplate jdbc) {
		this.admissionCandidates = admissionCandidates;
		this.administrativeClasses = administrativeClasses;
		this.students = students;
		this.admissions = admissions;
		this.terms = terms;
		this.teachingAssignments = teachingAssignments;
		this.courses = courses;
		this.offerings = offerings;
		this.teachers = teachers;
		this.scheduling = scheduling;
		this.academicData = academicData;
		this.jdbc = jdbc;
	}

	/** Agent 入口：按年度执行同一套分班规则。 */
	@Transactional
	public Map<String, Object> assignClasses(int year, int batchLimit, int targetClassSize, boolean dryRun) {
		int limit = bounded(batchLimit, 100, 500);
		int classSize = bounded(targetClassSize, 50, 200);
		var candidates = admissionCandidates.findAll().stream()
				.filter(value -> Integer.valueOf(year).equals(value.getAdmissionYear()))
				.filter(value -> "ADMITTED".equals(value.getAdmissionStatus()))
				.filter(value -> value.getAdmittedMajorId() != null && value.getPlannedClassId() == null)
				.sorted(Comparator.comparing(value -> value.getCandidateNo()))
				.limit(limit).toList();
		var allClasses = administrativeClasses.findAll();
		var classesByMajor = allClasses.stream()
				.filter(value -> "ACTIVE".equals(value.getStatus()))
				.filter(value -> Integer.valueOf(year).equals(value.getGradeYear()))
				.filter(value -> value.getMajorId() != null)
				.collect(Collectors.groupingBy(value -> value.getMajorId()));
		Map<String, Long> loads = new LinkedHashMap<>();
		for (var value : allClasses) {
			loads.put(value.getId(), students.countByAdministrativeClassId(value.getId())
					+ count("select count(*) from edu_admission_candidate where planned_class_id=? and admission_status in ('ADMITTED','VERIFIED')", value.getId()));
		}
		int assigned = 0;
		int noClass = 0;
		List<Map<String, Object>> assignments = new ArrayList<>();
		for (var candidate : candidates) {
			var target = classesByMajor.getOrDefault(candidate.getAdmittedMajorId(), List.of()).stream()
					.filter(value -> loads.getOrDefault(value.getId(), 0L) < classSize)
					.min(Comparator.comparingLong(value -> loads.getOrDefault(value.getId(), 0L)))
					.orElse(null);
			if (target == null) {
				noClass++;
				continue;
			}
			if (!dryRun) admissions.assignPlannedClass(candidate.getId(), target.getId());
			assignments.add(Map.of(
					"candidateId", candidate.getId(),
					"candidateNo", candidate.getCandidateNo(),
					"studentName", candidate.getStudentName(),
					"classId", target.getId(),
					"className", target.getClassName()));
			loads.put(target.getId(), loads.getOrDefault(target.getId(), 0L) + 1);
			assigned++;
		}
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("dryRun", dryRun);
		result.put("admissionYear", year);
		result.put("candidateCount", candidates.size());
		result.put("assigned", assigned);
		result.put("noAvailableClass", noClass);
		result.put("targetClassSize", classSize);
		result.put("assignments", assignments);
		return result;
	}

	/** Agent 入口：预览或批量完成资格复查。 */
	@Transactional
	public Map<String, Object> reviewAdmissions(int year, int batchLimit, boolean dryRun, String actor) {
		int limit = bounded(batchLimit, 100, 500);
		var candidates = admissionCandidates.findAll().stream()
				.filter(value -> Integer.valueOf(year).equals(value.getAdmissionYear()))
				.filter(value -> "ADMITTED".equals(value.getAdmissionStatus()))
				.sorted(Comparator.comparing(value -> value.getCandidateNo())).limit(limit).toList();
		int eligible = 0;
		List<Map<String, Object>> candidatesToReview = new ArrayList<>();
		for (var candidate : candidates) {
			if (candidate.getAdmittedMajorId() == null || candidate.getPlannedClassId() == null) continue;
			eligible++;
			candidatesToReview.add(Map.of("candidateId", candidate.getId(), "candidateNo", candidate.getCandidateNo(),
					"studentName", candidate.getStudentName(), "plannedClassId", candidate.getPlannedClassId()));
			if (!dryRun) admissions.review(candidate.getId(), new ReviewCommand(true, "开学准备批次确认后批量复查"), actor);
		}
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("dryRun", dryRun);
		result.put("admissionYear", year);
		result.put("candidateCount", candidates.size());
		result.put("eligible", eligible);
		result.put("succeeded", dryRun ? 0 : eligible);
		result.put("skipped", candidates.size() - eligible);
		result.put("batchLimit", limit);
		result.put("candidates", candidatesToReview);
		return result;
	}

	/** Agent 入口：预览或批量建档、生成学号并开通账号。 */
	@Transactional
	public Map<String, Object> enrollAdmissions(int year, int batchLimit, boolean dryRun) {
		int limit = bounded(batchLimit, 50, 200);
		var candidates = admissionCandidates.findAll().stream()
				.filter(value -> Integer.valueOf(year).equals(value.getAdmissionYear()))
				.filter(value -> "VERIFIED".equals(value.getAdmissionStatus()))
				.sorted(Comparator.comparing(value -> value.getCandidateNo())).limit(limit).toList();
		int eligible = 0;
		List<Map<String, Object>> candidatesToEnroll = new ArrayList<>();
		for (var candidate : candidates) {
			if (candidate.getPlannedClassId() == null) continue;
			eligible++;
			candidatesToEnroll.add(Map.of("candidateId", candidate.getId(), "candidateNo", candidate.getCandidateNo(),
					"studentName", candidate.getStudentName(), "plannedClassId", candidate.getPlannedClassId()));
			if (!dryRun) admissions.enroll(candidate.getId(), new EnrollmentCommand(candidate.getPlannedClassId()));
		}
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("dryRun", dryRun);
		result.put("admissionYear", year);
		result.put("candidateCount", candidates.size());
		result.put("eligible", eligible);
		result.put("succeeded", dryRun ? 0 : eligible);
		result.put("skipped", candidates.size() - eligible);
		result.put("batchLimit", limit);
		result.put("candidates", candidatesToEnroll);
		return result;
	}

	/** Agent 入口：由确认后的任教关系生成教学任务和行政班成员。 */
	@Transactional
	public Map<String, Object> generateOfferings(String semesterCode, int batchLimit, boolean dryRun) {
		int limit = bounded(batchLimit, 100, 500);
		var term = terms.findByTermCode(semesterCode)
				.orElseThrow(() -> new IllegalArgumentException("学期不存在"));
		var existing = new ArrayList<>(offerings.findBySemesterCodeOrderByOfferingCode(semesterCode));
		var catalogBySubject = courses.findAll().stream()
				.filter(value -> Boolean.TRUE.equals(value.getEnabled()) && value.getSubjectId() != null)
				.collect(Collectors.groupingBy(value -> value.getSubjectId()));
		var assignments = teachingAssignments.findAllByOrderByCreateTimeDesc().stream()
				.filter(value -> term.getId().equals(value.getAcademicTermId()))
				.filter(value -> Boolean.TRUE.equals(value.getEnabled()))
				.filter(value -> value.getAdministrativeClassId() != null)
				.limit(limit).toList();
		List<String> createdIds = new ArrayList<>();
		int creatable = 0;
		int ambiguousCourse = 0;
		int alreadyExists = 0;
		int missingContext = 0;
		List<Map<String, Object>> proposals = new ArrayList<>();
		for (var assignment : assignments) {
			var matchingCourses = catalogBySubject.getOrDefault(assignment.getSubjectId(), List.of());
			if (matchingCourses.size() != 1) {
				ambiguousCourse++;
				continue;
			}
			var course = matchingCourses.getFirst();
			boolean found = existing.stream().anyMatch(value ->
					Objects.equals(value.getAdministrativeClassId(), assignment.getAdministrativeClassId())
							&& Objects.equals(value.getTeacherId(), assignment.getTeacherId())
							&& Objects.equals(value.getCourseCode(), course.getCourseCode()));
			if (found) {
				alreadyExists++;
				continue;
			}
			var sourceClass = administrativeClasses.findById(assignment.getAdministrativeClassId()).orElse(null);
			var teacher = teachers.findById(assignment.getTeacherId()).orElse(null);
			if (sourceClass == null || teacher == null) {
				missingContext++;
				continue;
			}
			var classStudents = students.findByAdministrativeClassId(sourceClass.getId()).stream()
					.filter(value -> "ACTIVE".equals(value.getEnrollmentStatus())).toList();
			if (classStudents.isEmpty()) {
				missingContext++;
				continue;
			}
			creatable++;
			proposals.add(Map.of(
					"assignmentId", assignment.getId(),
					"className", sourceClass.getClassName(),
					"courseName", course.getCourseName(),
					"teacherName", teacher.getTeacherName(),
					"studentCount", classStudents.size(),
					"weeklyLessons", Math.max(1, assignment.getWeeklyLessons())));
			if (dryRun) continue;
			CourseOffering command = new CourseOffering();
			command.setSemesterCode(semesterCode);
			command.setOfferingCode("SP-" + semesterCode.replace("-", "") + "-" + assignment.getId().substring(0, 8));
			command.setCourseCode(course.getCourseCode());
			command.setCourseName(course.getCourseName());
			command.setTeachingClassName(sourceClass.getClassName() + "-" + course.getCourseName());
			command.setTeacherId(teacher.getId());
			command.setTeacherName(teacher.getTeacherName());
			command.setStudentCount(classStudents.size());
			command.setWeeklyLessons(Math.max(1, assignment.getWeeklyLessons()));
			command.setPreferredDurationPeriods(1);
			command.setWeekPattern("ALL");
			command.setRequiredRoomType(course.getRequiredRoomType());
			command.setCampusId(sourceClass.getCampusId());
			command.setAdministrativeClassId(sourceClass.getId());
			command.setStatus("ACTIVE");
			CourseOffering saved = scheduling.saveOffering(null, command);
			for (var student : classStudents) academicData.enrollFromAdministrativeClass(saved.getId(), student.getId());
			createdIds.add(saved.getId());
			existing.add(saved);
		}
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("dryRun", dryRun);
		result.put("semesterCode", semesterCode);
		result.put("assignments", assignments.size());
		result.put("creatable", creatable);
		result.put("created", createdIds.size());
		result.put("createdOfferingIds", createdIds);
		result.put("alreadyExists", alreadyExists);
		result.put("ambiguousCourse", ambiguousCourse);
		result.put("missingContext", missingContext);
		result.put("proposals", proposals);
		return result;
	}

	private long count(String sql, Object... args) {
		Long value = jdbc.queryForObject(sql, Long.class, args);
		return value == null ? 0L : value;
	}

	private int bounded(int value, int fallback, int maximum) {
		return Math.max(1, Math.min(value <= 0 ? fallback : value, maximum));
	}
}
