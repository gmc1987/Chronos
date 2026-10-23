package com.chronos.education.operationagent.service;

import java.time.Year;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Builds a read-only planning snapshot before an LLM is asked to select tools.
 * The snapshot prevents the planner from blindly replaying the entire opening
 * workflow when a school has already completed part of it.
 */
@Service
public class OperationAgentPlanningContextService {
	private final JdbcTemplate jdbc;

	public OperationAgentPlanningContextService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public Map<String, Object> inspect(String semesterCode, Integer admissionYear) {
		String semester = semesterCode == null ? "" : semesterCode.trim();
		int year = admissionYear == null ? Year.now().getValue() : admissionYear;
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("semesterCode", semester.isBlank() ? null : semester);
		result.put("admissionYear", year);
		result.put("termExists", semester.isBlank() ? null
				: count("select count(*) from edu_academic_term where term_code=?", semester) > 0);
		result.put("activeClassCount", count("select count(*) from edu_administrative_class where status='ACTIVE'"));
		result.put("admissionCandidateCount", count(
				"select count(*) from edu_admission_candidate where admission_year=?", year));
		result.put("unassignedAdmissionCount", count(
				"select count(*) from edu_admission_candidate where admission_year=? and planned_class_id is null", year));
		result.put("unenrolledAdmissionCount", count(
				"select count(*) from edu_admission_candidate where admission_year=? and admission_status<>'ENROLLED'", year));
		result.put("activeStudentCount", count(
				"select count(*) from edu_student_profile where enrollment_status='ACTIVE'"));
		result.put("activeTeacherCount", count(
				"select count(*) from edu_teacher_profile where enabled=true and employment_status='ACTIVE'"));
		result.put("teachingAssignmentCount", semester.isBlank() ? null : count("""
				select count(*) from edu_teacher_teaching_assignment assignment
				join edu_academic_term term on term.id=assignment.academic_term_id
				where term.term_code=? and assignment.enabled=true
				""", semester));
		result.put("courseOfferingCount", semester.isBlank() ? null : count(
				"select count(*) from edu_course_offering where semester_code=? and status='ACTIVE'", semester));
		result.put("scheduleCandidateCount", semester.isBlank() ? null : count(
				"select count(*) from edu_schedule_candidate_plan where semester_code=?", semester));
		result.put("publishedScheduleVersionCount", semester.isBlank() ? null : count(
				"select count(*) from edu_schedule_plan_version where semester_code=? and status='PUBLISHED'", semester));
		return result;
	}

	private long count(String sql, Object... args) {
		Long value = jdbc.queryForObject(sql, Long.class, args);
		return value == null ? 0L : value;
	}
}
