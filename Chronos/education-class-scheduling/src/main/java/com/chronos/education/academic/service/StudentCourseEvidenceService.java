package com.chronos.education.academic.service;

import com.chronos.education.scheduling.dao.CourseCatalogRepository;
import com.chronos.education.scheduling.dao.StudentProfileRepository;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.service.iService.IAuditLogService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StudentCourseEvidenceService {
	private final JdbcTemplate jdbc;
	private final CourseCatalogRepository courses;
	private final StudentProfileRepository students;
	private final EducationDataScopeService scopes;
	private final IAuditLogService audit;

	public record ImportRow(@NotBlank @Size(max = 64) String studentNo, @NotBlank @Size(max = 64) String courseCode,
			@NotNull Boolean passed, @NotBlank @Size(max = 255) String reference) {}
	public record Evidence(String courseCode, boolean passed, String reference) {}
	private record StudentCourse(String studentId, String courseId) {}

	@Transactional
	public int importEvidence(String schoolId, List<ImportRow> input, String actor) {
		var scope = scopes.resolve(actor);
		scopes.assertSchoolAccess(scope, schoolId);
		var seen = new HashSet<StudentCourse>();
		var ordered = input.stream().sorted(Comparator.comparing(ImportRow::studentNo)
				.thenComparing(ImportRow::courseCode)).toList();
		for (var row : ordered) {
			var student = students.findByStudentNo(row.studentNo().strip())
					.orElseThrow(() -> new IllegalArgumentException("学生不存在：" + row.studentNo()));
			scopes.assertStudentInSchool(scope, schoolId, student);
			var course = courses.findByCourseCode(row.courseCode().strip())
					.filter(item -> schoolId.equals(item.getOrganizationId()))
					.orElseThrow(() -> new IllegalArgumentException("本校课程不存在：" + row.courseCode()));
			if (!seen.add(new StudentCourse(student.getId(), course.getId()))) {
				throw new IllegalArgumentException("一个批次内禁止重复提交同一学生的同一门课程");
			}
			jdbc.update("""
					INSERT INTO edu_student_course_evidence (school_id,student_id,course_id,reference,passed,updated_by)
					VALUES (?,?,?,?,?,?)
					ON CONFLICT (school_id,student_id,course_id) DO UPDATE SET
					    reference=excluded.reference, passed=excluded.passed,
					    updated_by=excluded.updated_by, updated_at=current_timestamp
					""", schoolId, student.getId(), course.getId(), row.reference().strip(), row.passed(), actor);
		}
		audit.log(actor, "EDU_STUDENT_COURSE_EVIDENCE_IMPORT", "rows=" + input.size(), schoolId);
		return input.size();
	}

	@Transactional(readOnly = true)
	public List<Evidence> results(String schoolId, String studentId) {
		return jdbc.query("""
				SELECT c.course_code,e.passed,e.reference
				FROM edu_student_course_evidence e JOIN edu_course_catalog c ON c.id=e.course_id
				WHERE e.school_id=? AND e.student_id=? AND c.organization_id=e.school_id
				ORDER BY c.course_code
				""", (row, index) -> new Evidence(row.getString(1), row.getBoolean(2), row.getString(3)),
				schoolId, studentId);
	}
}
