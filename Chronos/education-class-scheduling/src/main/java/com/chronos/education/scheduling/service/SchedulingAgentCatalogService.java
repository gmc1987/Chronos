package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.model.AcademicTerm;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Bounded, school-wide metadata lookup for the governed scheduling Agent. */
@Service
@Transactional(readOnly = true)
public class SchedulingAgentCatalogService {
	private static final Map<String, String> QUERIES = Map.of(
			"TERM", """
					select term.id, term.termCode, term.termName
					from AcademicTerm term
					where term.status = 'ACTIVE'
						and (:keyword = '' or lower(term.termCode) like :pattern escape '!'
							or lower(term.termName) like :pattern escape '!')
					order by term.startDate desc, term.termCode
					""",
			"GRADE", """
					select grade.id, grade.gradeCode, grade.gradeName
					from EducationGrade grade
					where grade.enabled = true
						and (:keyword = '' or lower(grade.gradeCode) like :pattern escape '!'
							or lower(grade.gradeName) like :pattern escape '!')
					order by grade.gradeCode
					""",
			"CLASS", """
					select clazz.id, clazz.classCode, clazz.className
					from AdministrativeClass clazz
					where clazz.status = 'ACTIVE'
						and (:keyword = '' or lower(clazz.classCode) like :pattern escape '!'
							or lower(clazz.className) like :pattern escape '!')
					order by clazz.classCode
					""",
			"TEACHER", """
					select teacher.id, teacher.teacherNo, teacher.teacherName
					from TeacherAcademicProfile teacher
					where teacher.enabled = true
						and (:keyword = '' or lower(teacher.teacherNo) like :pattern escape '!'
							or lower(teacher.teacherName) like :pattern escape '!')
					order by teacher.teacherNo
					""",
			"ROOM", """
					select room.id, room.roomCode, room.roomName
					from Classroom room
					where room.enabled = true
						and (:keyword = '' or lower(room.roomCode) like :pattern escape '!'
							or lower(room.roomName) like :pattern escape '!')
					order by room.roomCode
					""",
			"COURSE", """
					select course.id, course.courseCode, course.courseName
					from CourseCatalog course
					where (:keyword = '' or lower(course.courseCode) like :pattern escape '!'
						or lower(course.courseName) like :pattern escape '!')
					order by course.courseCode
					""",
			"OFFERING", """
					select offering.id, offering.offeringCode, offering.teachingClassName
					from CourseOffering offering
					where offering.semesterCode = :semesterCode and offering.status = 'ACTIVE'
						and (:keyword = '' or lower(offering.offeringCode) like :pattern escape '!'
							or lower(offering.teachingClassName) like :pattern escape '!'
							or lower(offering.courseName) like :pattern escape '!')
					order by offering.offeringCode
					""",
			"BELL_PERIOD", """
					select period.id, period.periodNo,
						concat(schedule.campusId, ':', period.periodName)
					from BellPeriod period, BellSchedule schedule
					where period.bellScheduleId = schedule.id
						and schedule.academicTermId = :termId
						and schedule.status = 'ACTIVE' and period.schedulable = true
					order by schedule.campusId, period.periodNo
					""");

	private final EntityManager entityManager;
	private final EducationDataScopeService scopes;
	private final AcademicTermRepository terms;

	public SchedulingAgentCatalogService(
			EntityManager entityManager,
			EducationDataScopeService scopes,
			AcademicTermRepository terms) {
		this.entityManager = entityManager;
		this.scopes = scopes;
		this.terms = terms;
	}

	public Page lookup(
			String actor,
			String type,
			String semesterCode,
			String keyword,
			int page,
			int size) {
		scopes.assertFullAccess(scopes.resolve(actor));
		String query = QUERIES.get(type);
		if (query == null || page < 0 || size < 1 || size > 50
				|| page > 1000 || (keyword != null && keyword.length() > 80)) {
			throw new IllegalArgumentException("基础数据查询参数无效");
		}
		if (!"TERM".equals(type)) {
			requireTerm(semesterCode);
		}
		if ("BELL_PERIOD".equals(type) && keyword != null && !keyword.isBlank()) {
			throw new IllegalArgumentException("作息节次查询不支持关键字，请按页读取");
		}
		String normalized = keyword == null ? "" : keyword.strip().toLowerCase(java.util.Locale.ROOT);
		var statement = entityManager.createQuery(query, Object[].class);
		if (!"BELL_PERIOD".equals(type)) {
			statement.setParameter("keyword", normalized);
			statement.setParameter("pattern",
					"%" + normalized.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
		}
		if ("OFFERING".equals(type)) {
			statement.setParameter("semesterCode", semesterCode);
		} else if ("BELL_PERIOD".equals(type)) {
			statement.setParameter("termId", requireTerm(semesterCode).getId());
		}
		List<Item> rows = statement.setFirstResult(page * size).setMaxResults(size + 1)
				.getResultList().stream()
				.map(row -> new Item((String) row[0], String.valueOf(row[1]), (String) row[2]))
				.toList();
		return new Page(rows.stream().limit(size).toList(), page, size, rows.size() > size);
	}

	public Item resolve(
			String actor,
			String type,
			String semesterCode,
			String exactNameOrCode) {
		if (exactNameOrCode == null || exactNameOrCode.isBlank()) {
			throw new IllegalArgumentException("实体名称或编码不能为空");
		}
		if ("BELL_PERIOD".equals(type)) {
			throw new IllegalArgumentException("请先按学期和校区读取作息节次后选择编号");
		}
		// Page-based search is deliberately limited; ambiguity must be returned to the caller.
		Page page = lookup(actor, type, semesterCode, exactNameOrCode, 0, 50);
		String value = exactNameOrCode.strip();
		List<Item> matches = page.items().stream()
				.filter(item -> value.equals(item.code()) || value.equals(item.name()))
				.toList();
		if (page.hasMore() || matches.size() != 1) {
			throw new IllegalStateException(matches.isEmpty() ? "实体不存在或无权访问" : "实体存在多个匹配，请澄清");
		}
		return matches.getFirst();
	}

	public AcademicTerm requireTerm(String code) {
		if (code == null || code.isBlank()) {
			throw new IllegalArgumentException("学期不能为空");
		}
		return terms.findByTermCode(code)
				.filter(term -> "ACTIVE".equals(term.getStatus()))
				.orElseThrow(() -> new IllegalArgumentException("学期不存在或未启用"));
	}

	public record Item(String id, String code, String name) {
	}

	public record Page(List<Item> items, int page, int size, boolean hasMore) {
	}
}
