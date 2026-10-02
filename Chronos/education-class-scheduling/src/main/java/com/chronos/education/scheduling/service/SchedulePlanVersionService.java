package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.ClassroomRepository;
import com.chronos.education.scheduling.dao.ScheduleEntryRepository;
import com.chronos.education.scheduling.dao.SchedulePlanVersionRepository;
import com.chronos.education.scheduling.dao.TeachingClassMemberRepository;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.ScheduleEntry;
import com.chronos.education.scheduling.model.SchedulePlanVersion;
import com.chronos.education.scheduling.model.SchedulePlanVersionView;
import com.chronos.service.iService.IAuditLogService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.stream.Collectors;
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
	private final CourseOfferingRepository offerings;
	private final ClassroomRepository classrooms;
	private final TeachingClassMemberRepository members;
	private final ExamResourceReservationService examReservations;
	private final EducationResourceTransactionLock resourceLock;
	private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

	public SchedulePlanVersionService(
			ScheduleEntryRepository entries,
			SchedulePlanVersionRepository versions,
			AcademicTermRepository terms,
			IAuditLogService audit,
			EntityManager entityManager,
			SchedulePublicationNotificationService publicationNotifications,
			ScheduleQualityAnalysisService qualityAnalysis,
			ScheduleQualityRiskNotificationService qualityNotifications,
			CourseOfferingRepository offerings,
			ClassroomRepository classrooms,
			TeachingClassMemberRepository members,
			ExamResourceReservationService examReservations,
			EducationResourceTransactionLock resourceLock) {
		this.entries = entries;
		this.versions = versions;
		this.terms = terms;
		this.audit = audit;
		this.entityManager = entityManager;
		this.publicationNotifications = publicationNotifications;
		this.qualityAnalysis = qualityAnalysis;
		this.qualityNotifications = qualityNotifications;
		this.offerings = offerings;
		this.classrooms = classrooms;
		this.members = members;
		this.examReservations = examReservations;
		this.resourceLock = resourceLock;
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
		resourceLock.lockSemester(semester);
		lockTerm(semester);
		List<ScheduleEntry> current = entries.findBySemesterCodeOrderByDayOfWeekAscPeriodNoAsc(semester);
		if (current.isEmpty()) {
			throw new IllegalStateException("当前学期没有可发布的课表");
		}
		assertExamAvailability(current);
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
		resourceLock.lockSemester(source.getSemesterCode());
		lockTerm(source.getSemesterCode());
		List<ScheduleEntry> snapshot = read(source.getSnapshotJson());
		assertExamAvailability(snapshot);
		List<ScheduleEntry> current = entries.findBySemesterCodeOrderByDayOfWeekAscPeriodNoAsc(
				source.getSemesterCode());
		Map<String, ScheduleEntry> currentById = current.stream()
				.collect(Collectors.toMap(ScheduleEntry::getId, entry -> entry));
		Map<String, Integer> originalPeriods = current.stream()
				.collect(Collectors.toMap(ScheduleEntry::getId, ScheduleEntry::getPeriodNo));
		Set<String> snapshotIds = snapshot.stream().map(ScheduleEntry::getId)
				.collect(Collectors.toSet());
		List<ScheduleEntry> retainedHistory = new ArrayList<>();
		Set<String> targetSlots = snapshot.stream()
				.map(this::uniqueSlot).collect(Collectors.toSet());
		for (ScheduleEntry entry : current) {
			if (!snapshotIds.contains(entry.getId()) && referenceCount(entry.getId()) > 0) {
				if (targetSlots.contains(uniqueSlot(entry))) {
					throw new IllegalStateException("回滚目标与被引用的历史课表时段冲突：" + entry.getId());
				}
				retainedHistory.add(entry);
			}
		}
		// 同一事务中先避开旧时段，保证相互交换的课表项也能通过唯一约束。
		int temporaryPeriod = current.stream().mapToInt(ScheduleEntry::getPeriodNo)
				.max().orElse(0) + 1;
		for (ScheduleEntry entry : current) {
			entry.setPeriodNo(temporaryPeriod++);
		}
		entityManager.flush();
		for (ScheduleEntry entry : current) {
			if (!snapshotIds.contains(entry.getId()) && !retainedHistory.contains(entry)) {
				entries.delete(entry);
			}
		}
		for (ScheduleEntry entry : snapshot) {
			ScheduleEntry existing = currentById.get(entry.getId());
			if (existing == null) {
				insertSnapshotEntry(entry);
			} else {
				copySnapshotFields(entry, existing);
			}
		}
		for (ScheduleEntry entry : retainedHistory) {
			entry.setStatus("CANCELLED");
			entry.setLastUpdateBy(actor);
			entry.setLastUpdateTime(LocalDateTime.now());
			// 被引用的条目必须保留原时段，才能解释考试停课和日期例外的来源。
			entry.setPeriodNo(originalPeriods.get(entry.getId()));
		}
		entityManager.flush();
		entityManager.clear();
		List<ScheduleEntry> restored = entries.findBySemesterCodeOrderByDayOfWeekAscPeriodNoAsc(
				source.getSemesterCode());
		SchedulePlanVersion version = createVersion(
				source.getSemesterCode(),
				restored,
				actor,
				source.getVersionNo(),
				"EDUCATION_SCHEDULE_ROLLBACK");
		publicationNotifications.enqueue(version, restored, true);
		return version;
	}

	private long referenceCount(String entryId) {
		Number count = (Number) entityManager.createNativeQuery("""
				select (select count(*) from edu_exam_course_suspension_item where source_entry_id = :id)
				     + (select count(*) from edu_schedule_date_exception where source_entry_id = :id)
				""").setParameter("id", entryId).getSingleResult();
		return count.longValue();
	}

	private String uniqueSlot(ScheduleEntry entry) {
		return entry.getOfferingId() + "|" + entry.getDayOfWeek() + "|"
				+ entry.getPeriodNo() + "|" + entry.getStartWeek() + "|" + entry.getEndWeek();
	}

	private void copySnapshotFields(ScheduleEntry source, ScheduleEntry target) {
		target.setOfferingId(source.getOfferingId());
		target.setClassroomId(source.getClassroomId());
		target.setDayOfWeek(source.getDayOfWeek());
		target.setPeriodNo(source.getPeriodNo());
		target.setDurationPeriods(source.getDurationPeriods());
		target.setWeekPattern(source.getWeekPattern());
		target.setStartWeek(source.getStartWeek());
		target.setEndWeek(source.getEndWeek());
		target.setStatus(source.getStatus());
		target.setSubstituteTeacherId(source.getSubstituteTeacherId());
		target.setSourceAdjustmentInstanceId(source.getSourceAdjustmentInstanceId());
		target.setLocked(source.getLocked());
		target.setLastUpdateBy(source.getLastUpdateBy());
		target.setLastUpdateTime(source.getLastUpdateTime());
	}

	@Transactional(readOnly = true)
	public List<ScheduleEntry> latestPublishedEntries(String semesterCode) {
		return versions.findFirstBySemesterCodeOrderByVersionNoDesc(semesterCode)
				.map(value -> read(value.getSnapshotJson()))
				.orElseGet(List::of);
	}

	@Transactional(readOnly = true)
	public ScheduleEntry requirePublishedEntry(String entryId) {
		ScheduleEntry current = entries.findById(entryId)
				.orElseThrow(() -> new IllegalArgumentException("课表项不存在"));
		return latestPublishedEntries(current.getSemesterCode()).stream()
				.filter(entry -> entryId.equals(entry.getId()))
				.filter(entry -> !"CANCELLED".equals(entry.getStatus()))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("课表项未包含在最新发布版本"));
	}

	/** 版本回滚走原生 SQL，须在恢复前单独校验考试资源占用。 */
	private void assertExamAvailability(List<ScheduleEntry> snapshot) {
		for (ScheduleEntry entry : snapshot) {
			if ("CANCELLED".equals(entry.getStatus())) {
				continue;
			}
			CourseOffering offering = offerings.findById(entry.getOfferingId())
					.orElseThrow(() -> new IllegalStateException(
							"课表版本引用的教学任务不存在"));
			String campusId = classrooms.findById(entry.getClassroomId())
					.orElseThrow(() -> new IllegalStateException("课表版本引用的教室不存在"))
					.getCampusId();
			Set<String> studentIds = members
					.findByOfferingIdOrderByCreateTime(entry.getOfferingId())
					.stream()
					.filter(member -> "ENROLLED".equals(member.getEnrollmentStatus()))
					.map(member -> member.getStudentId())
					.collect(Collectors.toSet());
			examReservations.assertWeeklyCourseAvailable(
					entry.getSemesterCode(), campusId,
					entry.getDayOfWeek(), entry.getPeriodNo(),
					entry.getDurationPeriods(), entry.getStartWeek(),
					entry.getEndWeek(), entry.getWeekPattern(),
					entry.getClassroomId(), offering.getTeacherId(), studentIds);
		}
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
