package com.chronos.education.operationagent.service;

import java.time.Year;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.chronos.workflow.WorkflowService;
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
	private final WorkflowService workflows;

	public OperationAgentPlanningContextService(JdbcTemplate jdbc, WorkflowService workflows) {
		this.jdbc = jdbc;
		this.workflows = workflows;
	}

	public List<Map<String, Object>> availableWorkflowSchemas(String actor, String reference) {
		return workflows.available(actor).stream()
				.filter(row -> reference == null || List.of("id", "flowCode", "flowName").stream()
						.anyMatch(key -> reference.equalsIgnoreCase(String.valueOf(row.get(key)))))
				.limit(30)
				.map(row -> {
					Map<String, Object> schema = new LinkedHashMap<>();
					List.of("id", "flowCode", "flowName").forEach(key -> schema.put(key, row.get(key)));
					if (reference != null) {
						var startForm = workflows.startForm(String.valueOf(row.get("id")), actor);
						if (startForm.get("fields") instanceof List<?> fields) {
							schema.put("fields", fields.stream().filter(Map.class::isInstance)
									.map(item -> (Map<?, ?>) item)
									.map(field -> Map.of("fieldKey", field.get("fieldKey"),
											"fieldLabel", field.get("fieldLabel"), "fieldType", field.get("fieldType"),
											"required", field.get("required"))).toList());
						}
					}
					return schema;
				}).toList();
	}

	public Map<String, Object> inspect(String semesterCode, Integer admissionYear) {
		return inspect(semesterCode, admissionYear, null);
	}

	public Map<String, Object> inspect(String semesterCode, Integer admissionYear, String organizationId) {
		String semester = semesterCode == null ? "" : semesterCode.trim();
		int year = admissionYear == null ? Year.now().getValue() : admissionYear;
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("semesterCode", semester.isBlank() ? null : semester);
		result.put("admissionYear", year);
		result.put("organizationId", organizationId);
		result.put("termExists", semester.isBlank() ? null : organizationId == null
				? count("select count(*) from edu_academic_term where term_code=?", semester) > 0
				: count("select count(*) from edu_academic_term where term_code=? and organization_id=?", semester, organizationId) > 0);
		result.put("activeClassCount", organizationId == null
				? count("select count(*) from edu_administrative_class where status='ACTIVE'")
				: count("select count(*) from edu_administrative_class where status='ACTIVE' and campus_id=?", organizationId));
		result.put("admissionCandidateCount", count(
				organizationId == null ? "select count(*) from edu_admission_candidate where admission_year=?"
						: "select count(*) from edu_admission_candidate where admission_year=? and organization_id=?",
				organizationId == null ? new Object[] { year } : new Object[] { year, organizationId }));
		result.put("unassignedAdmissionCount", count(
				organizationId == null
						? "select count(*) from edu_admission_candidate where admission_year=? and planned_class_id is null"
						: "select count(*) from edu_admission_candidate where admission_year=? and planned_class_id is null and organization_id=?",
				organizationId == null ? new Object[] { year } : new Object[] { year, organizationId }));
		result.put("unenrolledAdmissionCount", count(
				organizationId == null
						? "select count(*) from edu_admission_candidate where admission_year=? and admission_status<>'ENROLLED'"
						: "select count(*) from edu_admission_candidate where admission_year=? and admission_status<>'ENROLLED' and organization_id=?",
				organizationId == null ? new Object[] { year } : new Object[] { year, organizationId }));
		result.put("activeStudentCount", organizationId == null
				? count("select count(*) from edu_student_profile where enrollment_status='ACTIVE'")
				: count("select count(*) from edu_student_profile student join edu_administrative_class class on class.id=student.administrative_class_id where student.enrollment_status='ACTIVE' and class.campus_id=?", organizationId));
		result.put("activeTeacherCount", organizationId == null
				? count("select count(*) from edu_teacher_profile where enabled=true and employment_status='ACTIVE'")
				: count("select count(distinct teacher.id) from edu_teacher_profile teacher join t_employee_assignment assignment on assignment.employee_id=teacher.employee_id where teacher.enabled=true and teacher.employment_status='ACTIVE' and assignment.status=1 and assignment.organization_id=?", organizationId));
		result.put("teachingAssignmentCount", semester.isBlank() ? null : count("""
				select count(*) from edu_teacher_teaching_assignment assignment
				join edu_academic_term term on term.id=assignment.academic_term_id
				where term.term_code=? and assignment.enabled=true
				  and (cast(? as varchar) is null or term.organization_id=?)
				""", semester, organizationId, organizationId));
		result.put("courseOfferingCount", semester.isBlank() ? null : count(
				"select count(*) from edu_course_offering where semester_code=? and status='ACTIVE' and (cast(? as varchar) is null or campus_id=?)", semester, organizationId, organizationId));
		result.put("scheduleCandidateCount", semester.isBlank() ? null : count(
				"select count(*) from edu_schedule_candidate_plan candidate join edu_academic_term term on term.term_code=candidate.semester_code where candidate.semester_code=? and (cast(? as varchar) is null or term.organization_id=?)", semester, organizationId, organizationId));
		result.put("publishedScheduleVersionCount", semester.isBlank() ? null : count(
				"select count(*) from edu_schedule_plan_version version join edu_academic_term term on term.term_code=version.semester_code where version.semester_code=? and version.status='PUBLISHED' and (cast(? as varchar) is null or term.organization_id=?)", semester, organizationId, organizationId));
		return result;
	}

	private long count(String sql, Object... args) {
		Long value = jdbc.queryForObject(sql, Long.class, args);
		return value == null ? 0L : value;
	}
}
