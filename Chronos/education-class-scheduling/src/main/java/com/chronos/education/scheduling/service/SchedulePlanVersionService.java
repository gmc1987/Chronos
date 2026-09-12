package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.dao.ScheduleEntryRepository;
import com.chronos.education.scheduling.dao.SchedulePlanVersionRepository;
import com.chronos.education.scheduling.model.ScheduleEntry;
import com.chronos.education.scheduling.model.SchedulePlanVersion;
import com.chronos.education.scheduling.model.SchedulePlanVersionView;
import com.chronos.service.iService.IAuditLogService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理课表发布历史。发布和回滚均追加版本，保证审计链不可被覆盖。 */
@Service
public class SchedulePlanVersionService {
	private final ScheduleEntryRepository entries;
	private final SchedulePlanVersionRepository versions;
	private final AcademicTermRepository terms;
	private final IAuditLogService audit;
	private final EntityManager entityManager;
	private final SchedulePublicationNotificationService publicationNotifications;
	private final ScheduleQualityAnalysisService qualityAnalysis;
	private final ScheduleQualityRiskNotificationService qualityNotifications;
	private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

	public SchedulePlanVersionService(
			ScheduleEntryRepository entries,
			SchedulePlanVersionRepository versions,
			AcademicTermRepository terms,
			IAuditLogService audit,
			EntityManager entityManager,
			SchedulePublicationNotificationService publicationNotifications,
			ScheduleQualityAnalysisService qualityAnalysis,
			ScheduleQualityRiskNotificationService qualityNotifications) {
		this.entries = entries;
		this.versions = versions;
		this.terms = terms;
		this.audit = audit;
		this.entityManager = entityManager;
		this.publicationNotifications = publicationNotifications;
		this.qualityAnalysis = qualityAnalysis;
		this.qualityNotifications = qualityNotifications;
	}

	@Transactional(readOnly = true)
	public List<SchedulePlanVersionView> versions(String semesterCode) {
		return versions.findBySemesterCodeOrderByVersionNoDesc(required(semesterCode)).stream()
				.map(SchedulePlanVersionView::from)
				.toList();
	}

	@Transactional
	public SchedulePlanVersion publish(String semesterCode, String actor) {
		String semester = required(semesterCode);
		lockTerm(semester);
		List<ScheduleEntry> current = entries.findBySemesterCodeOrderByDayOfWeekAscPeriodNoAsc(semester);
		if (current.isEmpty()) {
			throw new IllegalStateException("当前学期没有可发布的课表");
		}
		List<String> blockers = qualityAnalysis.publishBlockers(semester);
		if (!blockers.isEmpty()) {
			qualityNotifications.notifyBlocked(actor, semester, blockers);
			throw new IllegalStateException(
					"课表存在发布级硬风险：" + String.join("；", blockers));
		}
		SchedulePlanVersion version = createVersion(
				semester,
				current,
				actor,
				null,
				"EDUCATION_SCHEDULE_PUBLISH");
		publicationNotifications.enqueue(version, current, false);
		return version;
	}

	@Transactional
	public SchedulePlanVersion rollback(String versionId, String actor) {
		SchedulePlanVersion source = versions.findById(versionId)
				.orElseThrow(() -> new IllegalArgumentException("课表版本不存在"));
		lockTerm(source.getSemesterCode());
		List<ScheduleEntry> snapshot = read(source.getSnapshotJson());
		entries.deleteAllForRollback(source.getSemesterCode());
		entityManager.clear();
		for (ScheduleEntry entry : snapshot) {
			insertSnapshotEntry(entry);
		}
		entityManager.flush();
		SchedulePlanVersion version = createVersion(
				source.getSemesterCode(),
				snapshot,
				actor,
				source.getVersionNo(),
				"EDUCATION_SCHEDULE_ROLLBACK");
		publicationNotifications.enqueue(version, snapshot, true);
		return version;
	}

	@Transactional(readOnly = true)
	public List<ScheduleEntry> latestPublishedEntries(String semesterCode) {
		return versions.findFirstBySemesterCodeOrderByVersionNoDesc(semesterCode)
				.map(value -> read(value.getSnapshotJson()))
				.orElseGet(List::of);
	}

	private SchedulePlanVersion createVersion(
			String semester,
			List<ScheduleEntry> snapshot,
			String actor,
			Integer sourceVersion,
			String auditAction) {
		int next = versions.findFirstBySemesterCodeOrderByVersionNoDesc(semester)
				.map(value -> value.getVersionNo() + 1)
				.orElse(1);
		SchedulePlanVersion version = new SchedulePlanVersion();
		version.setSemesterCode(semester);
		version.setVersionNo(next);
		version.setSourceVersionNo(sourceVersion);
		version.setEntryCount(snapshot.size());
		version.setSnapshotJson(write(snapshot));
		version.setPublishedBy(actor);
		version.setPublishedAt(LocalDateTime.now());
		version = versions.save(version);
		audit.log(actor, auditAction,
				"semester=" + semester + ", version=" + next + ", sourceVersion=" + sourceVersion);
		return version;
	}

	private String write(List<ScheduleEntry> snapshot) {
		try {
			return json.writeValueAsString(snapshot);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("课表版本快照生成失败", exception);
		}
	}

	private List<ScheduleEntry> read(String snapshot) {
		try {
			return json.readValue(snapshot, new TypeReference<>() { });
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("课表版本快照损坏，无法恢复", exception);
		}
	}

	private String required(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("学期编码不能为空");
		}
		return value.trim();
	}

	private void lockTerm(String semesterCode) {
		terms.findForUpdateByTermCode(semesterCode)
				.orElseThrow(() -> new IllegalArgumentException("学期不存在：" + semesterCode));
	}

	private void insertSnapshotEntry(ScheduleEntry entry) {
		// Hibernate 会把“带生成型 ID 的反序列化实体”视为 detached，无法用 persist 恢复。
		// 参数化原生插入可保留原 ID，从而保持调课台账等外部引用稳定。
		entityManager.createNativeQuery("""
				insert into edu_schedule_entry (
				    id, create_by, create_time, last_update_by, last_update_time,
				    semester_code, offering_id, classroom_id, day_of_week, period_no,
				    duration_periods, week_pattern, start_week, end_week, status,
				    substitute_teacher_id, source_adjustment_instance_id, locked
				) values (
				    :id, :createBy, :createTime, :lastUpdateBy, :lastUpdateTime,
				    :semesterCode, :offeringId, :classroomId, :dayOfWeek, :periodNo,
				    :durationPeriods, :weekPattern, :startWeek, :endWeek, :status,
				    :substituteTeacherId, :sourceAdjustmentInstanceId, :locked
				)
				""")
				.setParameter("id", entry.getId())
				.setParameter("createBy", entry.getCreateBy())
				.setParameter("createTime", entry.getCreateTime())
				.setParameter("lastUpdateBy", entry.getLastUpdateBy())
				.setParameter("lastUpdateTime", entry.getLastUpdateTime())
				.setParameter("semesterCode", entry.getSemesterCode())
				.setParameter("offeringId", entry.getOfferingId())
				.setParameter("classroomId", entry.getClassroomId())
				.setParameter("dayOfWeek", entry.getDayOfWeek())
				.setParameter("periodNo", entry.getPeriodNo())
				.setParameter("durationPeriods", entry.getDurationPeriods())
				.setParameter("weekPattern", entry.getWeekPattern())
				.setParameter("startWeek", entry.getStartWeek())
				.setParameter("endWeek", entry.getEndWeek())
				.setParameter("status", entry.getStatus())
				.setParameter("substituteTeacherId", entry.getSubstituteTeacherId())
				.setParameter("sourceAdjustmentInstanceId", entry.getSourceAdjustmentInstanceId())
				.setParameter("locked", entry.getLocked())
				.executeUpdate();
	}
}
