package com.chronos.education.scheduling.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class TeachingHoursEvidenceStore {
	private final JdbcTemplate jdbc;
	private static final RowMapper<Evidence> EVIDENCE = (row, index) -> {
		int minutes = row.getInt("actual_minutes");
		Integer actualMinutes = row.wasNull() ? null : minutes;
		return new Evidence(row.getString("occurrence_key"), row.getInt("revision"), row.getString("campus_id"),
				actualMinutes, row.getString("reason"), row.getString("recorded_by"),
				row.getTimestamp("recorded_at").toLocalDateTime(), row.getString("snapshot_json"));
	};

	public record Evidence(String occurrenceKey, int revision, String campusId, Integer actualMinutes,
			String reason, String recordedBy, LocalDateTime recordedAt, String snapshot) {
	}

	List<Evidence> latest(String organization, String term, LocalDate start, LocalDate end) {
		return jdbc.query("""
				SELECT DISTINCT ON (occurrence_key) * FROM edu_teaching_hours_confirmation
				 WHERE organization_id = ? AND semester_code = ? AND occurrence_date BETWEEN ? AND ?
				 ORDER BY occurrence_key, revision DESC
				""", EVIDENCE, organization, term, start, end);
	}

	List<Evidence> history(String organization, String term, String key) {
		return jdbc.query("""
				SELECT * FROM edu_teaching_hours_confirmation
				 WHERE organization_id = ? AND semester_code = ? AND occurrence_key = ?
				 ORDER BY revision DESC
				""", EVIDENCE, organization, term, key);
	}

	Optional<Evidence> version(String term, String key) {
		return jdbc.query("""
				SELECT * FROM edu_teaching_hours_confirmation WHERE semester_code = ? AND occurrence_key = ?
				 ORDER BY revision DESC LIMIT 1
				""", EVIDENCE, term, key).stream().findFirst();
	}

	void append(String term, String key, int revision, String organization, String campus,
			String entry, LocalDate date, Integer minutes, String reason, String actor, String snapshot) {
		jdbc.update("""
				INSERT INTO edu_teaching_hours_confirmation
				(semester_code, occurrence_key, revision, organization_id, campus_id, source_entry_id,
				occurrence_date, actual_minutes, reason, recorded_by, snapshot_json)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", term, key, revision, organization, campus, entry, date, minutes, reason, actor, snapshot);
	}

	boolean hasConfirmation(String term, String entryId, LocalDate date) {
		if (date == null) return false;
		return Boolean.TRUE.equals(jdbc.queryForObject("""
				SELECT EXISTS (
				    SELECT 1 FROM (
				        SELECT DISTINCT ON (occurrence_key) actual_minutes
				          FROM edu_teaching_hours_confirmation
				         WHERE semester_code = ? AND source_entry_id = ? AND occurrence_date = ?
				         ORDER BY occurrence_key, revision DESC
				    ) current_revisions WHERE actual_minutes IS NOT NULL
				)
				""", Boolean.class, term, entryId, date));
	}
}
