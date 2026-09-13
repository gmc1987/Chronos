package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.*;
import com.chronos.education.scheduling.model.*;
import com.chronos.education.scheduling.model.dto.TeachingPlanDtos.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.OptimisticLockException;
import com.chronos.file.dao.ManagedFileRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application service for the plan/lesson slice; all commands enforce the same row scope. */
@Service
@Transactional
public class TeachingPlanLessonService {
	private final TeachingPlanRepository plans;
	private final TeachingPlanItemRepository items;
	private final TeachingPlanVersionRepository planVersions;
	private final LessonPlanRepository lessons;
	private final LessonPlanVersionRepository lessonVersions;
	private final com.chronos.education.scheduling.dao.CourseOfferingRepository offerings;
	private final com.chronos.education.scheduling.dao.ScheduleEntryRepository schedules;
	private final EducationDataScopeService scopes;
	private final TeachingReviewService reviews;
	private final ObjectMapper json;
	private final ManagedFileRepository files;

	public TeachingPlanLessonService(TeachingPlanRepository plans, TeachingPlanItemRepository items,
			TeachingPlanVersionRepository planVersions, LessonPlanRepository lessons,
			LessonPlanVersionRepository lessonVersions,
			com.chronos.education.scheduling.dao.CourseOfferingRepository offerings,
			com.chronos.education.scheduling.dao.ScheduleEntryRepository schedules,
			EducationDataScopeService scopes, TeachingReviewService reviews, ObjectMapper json,
			ManagedFileRepository files) {
		this.plans = plans; this.items = items; this.planVersions = planVersions;
		this.lessons = lessons; this.lessonVersions = lessonVersions; this.offerings = offerings;
		this.schedules = schedules; this.scopes = scopes; this.reviews = reviews; this.json = json; this.files = files;
	}

	public TeachingPlan createPlan(PlanCreateRequest request, Authentication auth) {
		CourseOffering offering = offering(request.offeringId(), auth);
		if (!Objects.equals(request.academicTermId(), offering.getSemesterCode()))
			throw new IllegalArgumentException("学期不属于该教学班");
		validatePlanItems(request.items());
		TeachingPlan plan = new TeachingPlan();
		plan.setOfferingId(offering.getId()); plan.setSemesterId(request.academicTermId());
		plan.setCourseId(offering.getCourseCode()); plan.setCampusId(offering.getCampusId());
		plan.setOwnerTeacherId(offering.getTeacherId()); plan.setName(request.name());
		plan.setPlanType(request.planType().name()); plan.setTotalHours(request.totalHours());
		plan.setObjective(request.objective()); plan.setAssessmentMethod(request.assessmentMethod());
		plan.setRemarks(request.remarks()); plan.setCreateBy(auth.getName());
		TeachingPlan saved = plans.save(plan);
		saveItems(saved.getId(), request.items());
		return saved;
	}

	@Transactional(readOnly = true)
	public Map<String, Object> context(String semesterId, Authentication auth) {
		var scope = scopes.resolve(auth.getName());
		var values = scopes.visibleOfferings(scope, offerings.findAll()).stream()
				.filter(o -> semesterId == null || semesterId.isBlank() || semesterId.equals(o.getSemesterCode()))
				.toList();
		var schedule = values.stream().flatMap(o -> schedules.findByOfferingId(o.getId()).stream())
				.filter(e -> "PUBLISHED".equals(e.getStatus()) || "SCHEDULED".equals(e.getStatus())).toList();
		return Map.of("semesterId", semesterId == null ? "" : semesterId, "offerings", values, "scheduleEntries", schedule);
	}

	public TeachingPlan updatePlan(String id, PlanUpdateRequest request, Authentication auth) {
		TeachingPlan plan = plans.findById(id).orElseThrow(() -> new NoSuchElementException("教学计划不存在"));
		assertPlanEditable(plan, request.rowVersion(), auth);
		validatePlanItems(request.items());
		plan.setName(request.name()); plan.setPlanType(request.planType().name());
		plan.setTotalHours(request.totalHours()); plan.setObjective(request.objective());
		plan.setAssessmentMethod(request.assessmentMethod()); plan.setRemarks(request.remarks());
		items.deleteByPlanId(id); saveItems(id, request.items());
		return plans.save(plan);
	}

	@Transactional(readOnly = true)
	public TeachingPlan getPlan(String id, Authentication auth) {
		TeachingPlan plan = plans.findById(id).orElseThrow(() -> new NoSuchElementException("教学计划不存在"));
		scopes.assertOfferingAccess(scopes.resolve(auth.getName()), plan.getOfferingId());
		return plan;
	}

	@Transactional(readOnly = true)
	public List<TeachingPlanItem> planItems(String id, Authentication auth) {
		getPlan(id, auth); return items.findByPlanIdOrderBySortOrderAsc(id);
	}

	@Transactional(readOnly = true)
	public List<TeachingPlanVersion> planVersions(String id, Authentication auth) {
		getPlan(id, auth); return planVersions.findByPlanIdOrderByVersionNoDesc(id);
	}

	public TeachingReviewRecord submitPlan(String id, String idempotencyKey, Authentication auth) {
		TeachingPlan plan = getPlan(id, auth);
		assertDraft(plan.getStatus());
		if (plan.getObjective() == null || plan.getObjective().isBlank()
				|| plan.getAssessmentMethod() == null || plan.getAssessmentMethod().isBlank())
			throw new IllegalArgumentException("提交前必须填写目标和考核方式");
		TeachingPlanVersion version = new TeachingPlanVersion();
		version.setPlanId(id); version.setVersionNo(plan.getCurrentVersionNo() + 1);
		version.setSnapshotJson(snapshot(plan, items.findByPlanIdOrderBySortOrderAsc(id)));
		version.setSnapshotHash(hash(version.getSnapshotJson()));
		version.setSourceRowVersion(plan.getRowVersion()); version.setStatus("SUBMITTED");
		version.setCreateBy(auth.getName()); version.setCreateTime(Instant.now());
		plan.setCurrentVersionNo(version.getVersionNo()); plan.setStatus("SUBMITTED");
		planVersions.save(version); plans.save(plan);
		TeachingReviewRecord record = reviews.submit("PLAN", id, plan.getOfferingId(),
				Map.of("versionId", version.getId(), "snapshotHash", version.getSnapshotHash(),
						"idempotencyKey", idempotencyKey == null ? "" : idempotencyKey), auth);
		version.setReviewRecordId(record.getId()); return record;
	}

	public TeachingPlan revisePlan(String id, Long rowVersion, Authentication auth) {
		TeachingPlan plan = getPlan(id, auth);
		if (!Objects.equals(plan.getRowVersion(), rowVersion)) throw new OptimisticLockException("教学计划已被更新");
		if (!Set.of("PUBLISHED", "REJECTED").contains(plan.getStatus()))
			throw new IllegalStateException("只有已发布或已驳回计划可以修订");
		plan.setStatus("DRAFT"); return plans.save(plan);
	}

	public LessonPlan createLesson(LessonCreateRequest request, Authentication auth) {
		CourseOffering offering = offering(request.offeringId(), auth);
		validateSchedule(request.scheduleEntryId(), offering.getId(), auth);
		validatePlanReference(request.teachingPlanId(), request.planItemId(), offering.getId(), auth);
		validateLesson(request.lessonType(), request.safetyNotes(), request.equipmentRequirements());
		validateFile(request.fileId(), auth);
		LessonPlan lesson = new LessonPlan();
		lesson.setOfferingId(offering.getId()); lesson.setOwnerTeacherId(offering.getTeacherId());
		lesson.setCampusId(offering.getCampusId()); lesson.setScheduleEntryId(request.scheduleEntryId());
		lesson.setTeachingPlanId(request.teachingPlanId()); lesson.setPlanItemId(request.planItemId());
		lesson.setTitle(request.title()); copyLesson(lesson, request.lessonNo(), request.teachingWeek(),
				request.lessonHours(), request.lessonType(), request.objectives(), request.keyPoints(),
				request.difficultPoints(), request.teachingMethod(), request.classroomActivity(),
				request.assessmentDesign(), request.afterClassReflection(), request.safetyNotes(),
				request.equipmentRequirements()); lesson.setCreateBy(auth.getName());
		LessonPlan saved = lessons.save(lesson);
		if (request.fileId() != null && !request.fileId().isBlank()) {
			LessonPlanVersion draft = new LessonPlanVersion(); draft.setId(UUID.randomUUID().toString());
			draft.setLessonPlanId(saved.getId()); draft.setVersionNo(1); draft.setFileId(request.fileId());
			draft.setStatus("DRAFT"); draft.setCreateBy(auth.getName()); draft.setCreateTime(Instant.now());
			lessonVersions.save(draft);
		}
		return saved;
	}

	public LessonPlan updateLesson(String id, LessonUpdateRequest request, Authentication auth) {
		LessonPlan lesson = lessons.findById(id).orElseThrow(() -> new NoSuchElementException("教案不存在"));
		scopes.assertOfferingAccess(scopes.resolve(auth.getName()), lesson.getOfferingId());
		if (!Objects.equals(lesson.getRowVersion(), request.rowVersion()))
			throw new OptimisticLockException("教案已被更新");
		assertDraft(lesson.getStatus());
		validateLesson(request.lessonType(), request.safetyNotes(), request.equipmentRequirements());
		validateFile(request.fileId(), auth);
		lesson.setTitle(request.title()); copyLesson(lesson, request.lessonNo(), request.teachingWeek(),
				request.lessonHours(), request.lessonType(), request.objectives(), request.keyPoints(),
				request.difficultPoints(), request.teachingMethod(), request.classroomActivity(),
				request.assessmentDesign(), request.afterClassReflection(), request.safetyNotes(),
				request.equipmentRequirements());
		LessonPlan saved = lessons.save(lesson);
		if (request.fileId() != null && !request.fileId().isBlank()) {
			int next = lessonVersions.findByLessonPlanIdOrderByVersionNoDesc(id).stream()
					.findFirst().map(v -> v.getVersionNo() + 1).orElse(1);
			LessonPlanVersion draft = new LessonPlanVersion(); draft.setId(UUID.randomUUID().toString());
			draft.setLessonPlanId(id); draft.setVersionNo(next); draft.setFileId(request.fileId());
			draft.setStatus("DRAFT"); draft.setCreateBy(auth.getName()); draft.setCreateTime(Instant.now());
			lessonVersions.save(draft);
		}
		return saved;
	}

	@Transactional(readOnly = true)
	public LessonPlan getLesson(String id, Authentication auth) {
		LessonPlan lesson = lessons.findById(id).orElseThrow(() -> new NoSuchElementException("教案不存在"));
		scopes.assertOfferingAccess(scopes.resolve(auth.getName()), lesson.getOfferingId()); return lesson;
	}

	@Transactional(readOnly = true)
	public List<LessonPlanVersion> lessonVersions(String id, Authentication auth) {
		getLesson(id, auth); return lessonVersions.findByLessonPlanIdOrderByVersionNoDesc(id);
	}

	public TeachingReviewRecord submitLesson(String id, String idempotencyKey, Authentication auth) {
		LessonPlan lesson = getLesson(id, auth); assertDraft(lesson.getStatus());
		TeachingReviewRecord existing = null;
		LessonPlanVersion version = new LessonPlanVersion();
		version.setLessonPlanId(id); version.setVersionNo(lessonVersions.findByLessonPlanIdOrderByVersionNoDesc(id)
				.stream().findFirst().map(v -> v.getVersionNo() + 1).orElse(1));
		version.setSnapshotJson(snapshot(lesson)); version.setSnapshotHash(hash(version.getSnapshotJson()));
		version.setSubmittedAt(Instant.now()); version.setStatus("SUBMITTED"); version.setCreateBy(auth.getName());
		version.setCreateTime(Instant.now()); lesson.setStatus("SUBMITTED");
		lessonVersions.save(version); lessons.save(lesson);
		TeachingReviewRecord record = reviews.submit("LESSON_PLAN", id, lesson.getOfferingId(),
				Map.of("versionId", version.getId(), "snapshotHash", version.getSnapshotHash(),
						"idempotencyKey", idempotencyKey == null ? "" : idempotencyKey), auth);
		return record;
	}

	private CourseOffering offering(String id, Authentication auth) {
		CourseOffering offering = offerings.findById(id).orElseThrow(() -> new NoSuchElementException("教学班不存在"));
		scopes.assertOfferingAccess(scopes.resolve(auth.getName()), offering); return offering;
	}
	private void validateSchedule(String id, String offeringId, Authentication auth) {
		if (id == null || id.isBlank()) return;
		ScheduleEntry entry = schedules.findById(id).orElseThrow(() -> new NoSuchElementException("课表项不存在"));
		if (!Objects.equals(offeringId, entry.getOfferingId())) throw new IllegalArgumentException("课表项不属于该教学班");
		if (!"PUBLISHED".equals(entry.getStatus()))
			throw new IllegalArgumentException("只能引用已发布课次");
		scopes.assertScheduleEntryAccess(scopes.resolve(auth.getName()), id);
	}
	private void validatePlanReference(String planId, String itemId, String offeringId, Authentication auth) {
		if (planId == null || planId.isBlank()) { if (itemId != null) throw new IllegalArgumentException("章节必须隶属教学计划"); return; }
		TeachingPlan plan = getPlan(planId, auth);
		if (!Objects.equals(offeringId, plan.getOfferingId())) throw new IllegalArgumentException("计划不属于该教学班");
		if (itemId != null && !items.findById(itemId).map(i -> planId.equals(i.getPlanId())).orElse(false))
			throw new IllegalArgumentException("章节不属于该教学计划");
	}
	private void validatePlanItems(List<PlanItemRequest> values) {
		if (values == null) return;
		for (PlanItemRequest i : values) {
			if (i.weekStart() > i.weekEnd()) throw new IllegalArgumentException("章节周次范围无效");
			if (i.trainingHours() != null && i.trainingHours() > i.lessonHours())
				throw new IllegalArgumentException("实训课时不得超过章节课时");
		}
	}
	private void saveItems(String planId, List<PlanItemRequest> values) {
		if (values == null) return;
		values.forEach(i -> { TeachingPlanItem v = new TeachingPlanItem(); v.setId(UUID.randomUUID().toString());
			v.setPlanId(planId); v.setChapterNo(i.chapterNo()); v.setChapterName(i.chapterName());
			v.setWeekStart(i.weekStart()); v.setWeekEnd(i.weekEnd()); v.setLessonHours(i.lessonHours());
			v.setTrainingHours(i.trainingHours() == null ? 0 : i.trainingHours()); v.setObjectives(i.objectives());
			v.setKeyPoints(i.keyPoints()); v.setDifficultPoints(i.difficultPoints()); v.setAssessmentMethod(i.assessmentMethod());
			v.setLinkedKnowledgePointId(i.linkedKnowledgePointId()); v.setSortOrder(i.sortOrder()); items.save(v); });
	}
	private void assertPlanEditable(TeachingPlan p, Long rowVersion, Authentication a) {
		scopes.assertOfferingAccess(scopes.resolve(a.getName()), p.getOfferingId());
		if (!Objects.equals(p.getRowVersion(), rowVersion)) throw new OptimisticLockException("教学计划已被更新");
		assertDraft(p.getStatus());
	}
	private void assertDraft(String status) { if (!"DRAFT".equals(status)) throw new IllegalStateException("仅草稿状态允许编辑或提交"); }
	private void validateLesson(LessonType type, String safety, String equipment) {
		if (type == LessonType.PRACTICAL && (safety == null || safety.isBlank() || equipment == null || equipment.isBlank()))
			throw new IllegalArgumentException("实训教案必须填写安全事项和设备要求");
	}
	private void validateFile(String fileId, Authentication auth) {
		if (fileId == null || fileId.isBlank()) return;
		if (fileId.length() > 64 || fileId.contains("/") || fileId.contains("\\") || fileId.startsWith("http"))
			throw new IllegalArgumentException("附件只能使用受控 fileId");
		var file = files.findById(fileId).orElseThrow(() -> new IllegalArgumentException("文件不存在"));
		if (!Objects.equals(file.getOwnerUsername(), auth.getName())
				&& !"ACTIVE".equals(file.getStatus()) && !"PENDING_BIND".equals(file.getStatus()))
			throw new IllegalArgumentException("文件不可绑定");
		if (!Set.of("ACTIVE", "PENDING_BIND").contains(file.getStatus()))
			throw new IllegalArgumentException("文件尚未通过扫描");
	}
	private void copyLesson(LessonPlan l, int no, int week, int hours, LessonType type, String o, String k,
			String d, String method, String activity, String assessment, String reflection, String safety, String equipment) {
		l.setLessonNo(no); l.setTeachingWeek(week); l.setLessonHours(hours); l.setLessonType(type.name());
		l.setObjectives(o); l.setKeyPoints(k); l.setDifficultPoints(d); l.setTeachingMethod(method);
		l.setClassroomActivity(activity); l.setAssessmentDesign(assessment); l.setAfterClassReflection(reflection);
		l.setSafetyNotes(safety); l.setEquipmentRequirements(equipment);
	}
	private String snapshot(Object value) { try { return json.writeValueAsString(value); } catch (JsonProcessingException e) { throw new IllegalStateException("快照生成失败", e); } }
	private String snapshot(TeachingPlan p, List<TeachingPlanItem> i) { try { return json.writeValueAsString(Map.of("plan", p, "items", i)); } catch (JsonProcessingException e) { throw new IllegalStateException("快照生成失败", e); } }
	private String hash(String s) { try { byte[] b = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)); StringBuilder x = new StringBuilder(); for (byte v : b) x.append(String.format("%02x", v)); return x.toString(); } catch (Exception e) { throw new IllegalStateException(e); } }
}
