package com.chronos.education.scheduling.service;

import java.time.LocalDate;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.model.AcademicTerm;
import com.chronos.service.iService.IAuditLogService;

/**
 * 学期归档的人工确认边界。归档只冻结学期本身，不删除课表、成绩和考试历史数据。
 */
@Service
public class SemesterArchiveService {
	private final AcademicTermRepository terms;
	private final JdbcTemplate jdbc;
	private final IAuditLogService audit;

	public SemesterArchiveService(
			AcademicTermRepository terms,
			JdbcTemplate jdbc,
			IAuditLogService audit) {
		this.terms = terms;
		this.jdbc = jdbc;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public ArchiveReadiness readiness(String termId) {
		AcademicTerm term = requireTerm(termId);
		String semester = term.getTermCode();
		long offerings = count(
				"select count(*) from edu_course_offering where semester_code=? and status='ACTIVE'",
				semester);
		long gradebooks = count("""
				select count(*) from edu_gradebook gradebook
				join edu_course_offering offering on offering.id=gradebook.offering_id
				where offering.semester_code=?
				""", semester);
		long publishedGradebooks = count("""
				select count(*) from edu_gradebook gradebook
				join edu_course_offering offering on offering.id=gradebook.offering_id
				where offering.semester_code=? and gradebook.status='PUBLISHED'
				""", semester);
		long examPlans = count("select count(*) from edu_exam_plan where semester_code=?", semester);
		long publishedExamPlans = count(
				"select count(*) from edu_exam_plan where semester_code=? and status='PUBLISHED'",
				semester);
		long pendingStudentChanges = count(
				"select count(*) from edu_student_status_change where status in ('PENDING','APPROVED_PENDING')");
		long deadEvents = count("select count(*) from edu_domain_event_outbox where status='DEAD'");
		boolean ended = term.getEndDate() != null && term.getEndDate().isBefore(LocalDate.now());
		boolean current = Boolean.TRUE.equals(term.getCurrentTerm());
		boolean businessReady = offerings > 0
				&& gradebooks > 0 && gradebooks == publishedGradebooks
				&& examPlans > 0 && examPlans == publishedExamPlans
				&& pendingStudentChanges == 0 && deadEvents == 0;
		boolean ready = !"ARCHIVED".equals(term.getStatus()) && !current && ended && businessReady;
		String message = archiveMessage(term, ended, current, offerings, gradebooks,
				publishedGradebooks, examPlans, publishedExamPlans, pendingStudentChanges, deadEvents);
		return new ArchiveReadiness(term.getId(), semester, term.getStatus(), current, ended,
				offerings, gradebooks, publishedGradebooks, examPlans, publishedExamPlans,
				pendingStudentChanges, deadEvents, businessReady, ready, message);
	}

	@Transactional
	public AcademicTerm archive(String termId, String actor) {
		ArchiveReadiness readiness = readiness(termId);
		if (!readiness.readyForArchive()) {
			throw new IllegalStateException(readiness.message());
		}
		AcademicTerm term = requireTerm(termId);
		term.setStatus("ARCHIVED");
		term.setCurrentTerm(false);
		AcademicTerm saved = terms.save(term);
		audit.log(actor, "EDUCATION_SEMESTER_ARCHIVE", "semester=" + term.getTermCode());
		return saved;
	}

	private String archiveMessage(
			AcademicTerm term,
			boolean ended,
			boolean current,
			long offerings,
			long gradebooks,
			long publishedGradebooks,
			long examPlans,
			long publishedExamPlans,
			long pendingStudentChanges,
			long deadEvents) {
		if ("ARCHIVED".equals(term.getStatus())) return "该学期已经归档";
		if (current) return "当前启用学期不能归档，请先启用下一学期";
		if (!ended) return "学期尚未结束，不能提前归档";
		if (offerings == 0) return "当前学期没有有效开课任务";
		if (gradebooks == 0 || gradebooks != publishedGradebooks) return "仍有成绩册尚未发布";
		if (examPlans == 0 || examPlans != publishedExamPlans) return "仍有考试计划尚未发布";
		if (pendingStudentChanges > 0) return "仍有待处理的学籍异动";
		if (deadEvents > 0) return "仍有失败的领域事件需要处理";
		return "归档准备度检查通过";
	}

	private AcademicTerm requireTerm(String termId) {
		return terms.findById(termId)
				.orElseThrow(() -> new IllegalArgumentException("学期不存在"));
	}

	private long count(String sql, Object... args) {
		Long value = jdbc.queryForObject(sql, Long.class, args);
		return value == null ? 0 : value;
	}

	public record ArchiveReadiness(
			String termId,
			String semesterCode,
			String status,
			boolean currentTerm,
			boolean termEnded,
			long activeOfferings,
			long gradebooks,
			long publishedGradebooks,
			long examPlans,
			long publishedExamPlans,
			long pendingStudentStatusChanges,
			long deadDomainEvents,
			boolean businessReady,
			boolean readyForArchive,
			String message) {
	}
}
