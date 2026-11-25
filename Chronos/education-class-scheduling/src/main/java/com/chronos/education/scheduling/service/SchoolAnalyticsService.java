package com.chronos.education.scheduling.service;

import com.chronos.Idao.IOrganizationRepository;
import com.chronos.security.DirectoryDataScopeGuard;
import com.chronos.service.iService.IAuditLogService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SchoolAnalyticsService {
	private final JdbcTemplate jdbc;
	private final DirectoryDataScopeGuard scopes;
	private final IOrganizationRepository organizations;
	private final IAuditLogService audit;

	public record DailyRollup(@NotNull LocalDate date, @NotNull @PositiveOrZero Integer studentCount,
			@NotNull @PositiveOrZero Integer teacherCount, @NotNull @PositiveOrZero Integer classCount) {}

	@Transactional
	public int importDailyRollups(String schoolId, List<DailyRollup> rows, Authentication actor) {
		requireSchool(schoolId, actor);
		var dates = new HashSet<LocalDate>();
		if (rows.stream().anyMatch(row -> !dates.add(row.date()))) {
			throw new IllegalArgumentException("同一批指标不能重复填写日期");
		}
		// Match the primary key order so overlapping imports take row locks consistently.
		var sorted = rows.stream().sorted(java.util.Comparator.comparing(DailyRollup::date)).toList();
		jdbc.batchUpdate("""
				INSERT INTO edu_delivery_daily_rollup (school_id,snapshot_date,student_count,teacher_count,class_count,updated_by)
				VALUES (?,?,?,?,?,?)
				ON CONFLICT (school_id,snapshot_date) DO UPDATE SET student_count=excluded.student_count,
				    teacher_count=excluded.teacher_count,class_count=excluded.class_count,
				    updated_by=excluded.updated_by,updated_at=current_timestamp
				""", sorted.stream().map(row -> new Object[] {schoolId, row.date(), row.studentCount(),
						row.teacherCount(), row.classCount(), actor.getName()}).toList());
		audit.log(actor.getName(), "EDU_ANALYTICS_DAILY_IMPORT", "days=" + rows.size(), schoolId);
		return rows.size();
	}

	@Transactional(readOnly = true)
	public List<DailyRollup> dailyRollups(String schoolId, LocalDate start, LocalDate end, Authentication actor) {
		requireSchool(schoolId, actor);
		if (end.isBefore(start) || ChronoUnit.DAYS.between(start, end) > 365) {
			throw new IllegalArgumentException("请选择起止顺序正确且不超过 366 天的日期范围");
		}
		return jdbc.query("""
				SELECT snapshot_date,student_count,teacher_count,class_count
				FROM edu_delivery_daily_rollup WHERE school_id=? AND snapshot_date BETWEEN ? AND ?
				ORDER BY snapshot_date DESC
				""", (row, index) -> new DailyRollup(row.getDate("snapshot_date").toLocalDate(),
				row.getInt("student_count"), row.getInt("teacher_count"), row.getInt("class_count")), schoolId, start, end);
	}

	private void requireSchool(String schoolId, Authentication actor) {
		scopes.requireOrganization(actor, schoolId);
		organizations.findById(schoolId)
				.filter(org -> "SCHOOL".equals(org.getOrganizationType()) && Integer.valueOf(1).equals(org.getStatus()))
				.orElseThrow(() -> new IllegalArgumentException("请选择启用的学校"));
	}
}
