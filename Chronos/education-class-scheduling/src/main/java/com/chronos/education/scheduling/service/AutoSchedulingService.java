package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.dao.ClassroomRepository;
import com.chronos.education.scheduling.dao.ClassroomUnavailableSlotRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.ScheduleCandidatePlanRepository;
import com.chronos.education.scheduling.dao.ScheduleEntryRepository;
import com.chronos.education.scheduling.dao.TeacherTimeConstraintRepository;
import com.chronos.education.scheduling.dao.TeacherAcademicProfileRepository;
import com.chronos.education.scheduling.dao.TeachingClassMemberRepository;
import com.chronos.education.scheduling.model.AutoScheduleCommand;
import com.chronos.education.scheduling.model.Classroom;
import com.chronos.education.scheduling.model.ClassroomUnavailableSlot;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.ScheduleCandidateMetrics;
import com.chronos.education.scheduling.model.ScheduleCandidateGovernanceCommand;
import com.chronos.education.scheduling.model.ScheduleCandidatePlan;
import com.chronos.education.scheduling.model.ScheduleCandidateView;
import com.chronos.education.scheduling.model.ScheduleDiffItem;
import com.chronos.education.scheduling.model.ScheduleDiffView;
import com.chronos.education.scheduling.model.ScheduleEntry;
import com.chronos.education.scheduling.model.SchedulePolicy;
import com.chronos.education.scheduling.model.ScheduleRunConstraints;
import com.chronos.education.scheduling.model.TeacherTimeConstraint;
import com.chronos.education.scheduling.model.TeacherAcademicProfile;
import com.chronos.education.scheduling.model.TeachingClassMember;
import com.chronos.service.iService.IAuditLogService;
import com.chronos.Idao.IAdminUserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 生成、比较和应用自动排课候选方案。
 *
 * <p>候选生成只写候选表；只有显式应用且基线哈希未变化时，才替换当前草稿课表。</p>
 */
@Service
public class AutoSchedulingService {
	private static final int DEFAULT_WEEKDAYS = 5;
	private static final int DEFAULT_PERIODS = 8;
	private static final int MAX_CANDIDATES = 5;
	private final ScheduleCandidatePlanRepository candidates;
	private final ScheduleEntryRepository entries;
	private final CourseOfferingRepository offerings;
	private final ClassroomRepository classrooms;
	private final ClassroomUnavailableSlotRepository unavailableSlots;
	private final TeacherTimeConstraintRepository constraints;
	private final TeacherAcademicProfileRepository teacherProfiles;
	private final TeachingClassMemberRepository members;
	private final AcademicTermRepository terms;
	private final AcademicCalendarService academicCalendar;
	private final SchedulePolicyService policyService;
	private final IAdminUserRepository users;
	private final IAuditLogService audit;
	private final EntityManager entityManager;
	private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

	public AutoSchedulingService(
			ScheduleCandidatePlanRepository candidates,
			ScheduleEntryRepository entries,
			CourseOfferingRepository offerings,
			ClassroomRepository classrooms,
			ClassroomUnavailableSlotRepository unavailableSlots,
			TeacherTimeConstraintRepository constraints,
			TeacherAcademicProfileRepository teacherProfiles,
			TeachingClassMemberRepository members,
			AcademicTermRepository terms,
			AcademicCalendarService academicCalendar,
			SchedulePolicyService policyService,
			IAdminUserRepository users,
			IAuditLogService audit,
			EntityManager entityManager) {
		this.candidates = candidates;
		this.entries = entries;
		this.offerings = offerings;
		this.classrooms = classrooms;
		this.unavailableSlots = unavailableSlots;
		this.constraints = constraints;
		this.teacherProfiles = teacherProfiles;
		this.members = members;
		this.terms = terms;
		this.academicCalendar = academicCalendar;
		this.policyService = policyService;
		this.users = users;
		this.audit = audit;
		this.entityManager = entityManager;
	}

	@Transactional
	public List<ScheduleCandidateView> generate(
			AutoScheduleCommand command,
			String actor) {
		return generate(command, actor, () -> false, progress -> { });
	}

	@Transactional
	public List<ScheduleCandidateView> generate(
			AutoScheduleCommand command,
			String actor,
			BooleanSupplier cancelled) {
		return generate(command, actor, cancelled, progress -> { });
	}

	@Transactional
	public List<ScheduleCandidateView> generate(
			AutoScheduleCommand command,
			String actor,
			BooleanSupplier cancelled,
			IntConsumer progress) {
		return generate(command, actor, cancelled, progress, ScheduleRunConstraints.empty());
	}

	@Transactional
	public List<ScheduleCandidateView> generate(
			AutoScheduleCommand command,
			String actor,
			BooleanSupplier cancelled,
			IntConsumer progress,
			ScheduleRunConstraints runConstraints) {
		if (runConstraints == null) {
			throw new IllegalArgumentException("动态排课规则不能为空");
		}
		GenerationRequest request = validate(command);
		SchedulePolicy policy = policyService.resolve(request.semesterCode());
		terms.findByTermCode(request.semesterCode())
				.orElseThrow(() -> new IllegalArgumentException("学期不存在"));
		List<ScheduleEntry> baseline = current(request.semesterCode());
		String baselineHash = hash(baseline);
		List<CourseOffering> semesterOfferings = offerings
				.findBySemesterCodeOrderByOfferingCode(request.semesterCode()).stream()
				.filter(offering -> "ACTIVE".equals(offering.getStatus()))
				.toList();
		Set<String> targetIds = targetOfferingIds(request, semesterOfferings);
		Set<String> teacherIds = semesterOfferings.stream()
				.filter(offering -> targetIds.contains(offering.getId()))
				.map(CourseOffering::getTeacherId)
				.collect(Collectors.toSet());
		if (runConstraints.teacherSlots().stream()
				.anyMatch(rule -> !teacherIds.contains(rule.teacherId())
						|| rule.dayOfWeek() > request.weekdays()
						|| rule.periodNo() > request.periodsPerDay())) {
			throw new IllegalArgumentException("动态规则包含当前排课范围以外的教师或时段");
		}
		if (runConstraints.offeringDurations().stream().anyMatch(rule ->
				!targetIds.contains(rule.offeringId())
				|| semesterOfferings.stream().noneMatch(offering ->
						rule.offeringId().equals(offering.getId())
								&& offering.getWeeklyLessons() != null
								&& offering.getWeeklyLessons() >= rule.periods()))) {
			throw new IllegalArgumentException("动态连堂规则包含无效教学任务");
		}
		if (runConstraints.weekRules().stream().anyMatch(rule ->
				!targetIds.contains(rule.offeringId()) || rule.startWeek() < request.startWeek()
						|| rule.endWeek() > request.endWeek())
				|| runConstraints.weekRules().stream().map(ScheduleRunConstraints.WeekRule::offeringId)
						.distinct().count() != runConstraints.weekRules().size()) {
			throw new IllegalArgumentException("动态教学周规则不在当前排课范围");
		}
		if (runConstraints.lockedEntries().stream().anyMatch(rule ->
				!targetIds.contains(rule.offeringId())
						|| baseline.stream().noneMatch(entry -> rule.entryId().equals(entry.getId())
								&& rule.offeringId().equals(entry.getOfferingId())
								&& java.util.Objects.equals(rule.dayOfWeek(), entry.getDayOfWeek())
								&& java.util.Objects.equals(rule.periodNo(), entry.getPeriodNo())
								&& !"CANCELLED".equals(entry.getStatus())))
				|| runConstraints.lockedEntries().stream().map(ScheduleRunConstraints.LockedEntry::entryId)
						.distinct().count() != runConstraints.lockedEntries().size()) {
			throw new IllegalArgumentException("临时锁课项不属于当前课表或排课范围");
		}
		if (runConstraints.softPriorities().stream().anyMatch(rule ->
				!teacherIds.contains(rule.teacherId()))
				|| runConstraints.softPriorities().stream().distinct().count()
						!= runConstraints.softPriorities().size()) {
			throw new IllegalArgumentException("动态教师软偏好不在当前排课范围");
		}
		if (runConstraints.slotExclusions().stream().anyMatch(rule ->
				!("OFFERING".equals(rule.targetType())
						? targetIds.contains(rule.targetId()) : teacherIds.contains(rule.targetId()))
						|| rule.dayOfWeek() > request.weekdays()
						|| rule.periodNo() > request.periodsPerDay())
				|| runConstraints.slotExclusions().stream().distinct().count()
						!= runConstraints.slotExclusions().size()) {
			throw new IllegalArgumentException("组合禁排规则不在当前排课范围或存在重复");
		}
		List<ScheduleCandidateView> result = new ArrayList<>();
		for (int index = 0; index < request.candidateCount(); index++) {
			checkCancelled(cancelled);
			GeneratedPlan generated = buildCandidate(
					request,
					baseline,
					semesterOfferings,
					targetIds,
					policy,
					cancelled,
					index,
					runConstraints);
			ScheduleCandidatePlan candidate = new ScheduleCandidatePlan();
			candidate.setSemesterCode(request.semesterCode());
			candidate.setPlanName(request.planName() + "-方案" + (index + 1));
			candidate.setGenerationMode(request.mode());
			candidate.setScopeJson(write(Map.of(
					"offeringIds", targetIds,
					"weekdays", request.weekdays(),
					"periodsPerDay", request.periodsPerDay(),
					"startWeek", request.startWeek(),
					"endWeek", request.endWeek())));
			candidate.setBaselineHash(baselineHash);
			candidate.setSnapshotJson(write(generated.entries()));
			candidate.setMetricsJson(write(generated.metrics()));
			candidate.setEntryCount(generated.entries().size());
			candidate.setUnscheduledLessons(generated.metrics().unscheduledLessons());
			candidate.setTotalScore(generated.metrics().totalScore());
			candidate.setGeneratedBy(actor);
			candidate.setOwnerUsername(actor);
			candidate.setReviewStatus("DRAFT");
			candidate.setGeneratedAt(LocalDateTime.now());
			result.add(view(candidates.save(candidate)));
			progress.accept((index + 1) * 100 / request.candidateCount());
		}
		audit.log(
				actor,
				"EDUCATION_AUTO_SCHEDULE_GENERATE",
				"semester=" + request.semesterCode()
						+ ", mode=" + request.mode()
						+ ", candidates=" + result.size()
						+ ", offerings=" + targetIds.size());
		Comparator<ScheduleCandidateView> ranking = Comparator
				.comparing(ScheduleCandidateView::totalScore).reversed();
		if (!runConstraints.softPriorities().isEmpty()) {
			ranking = Comparator.comparing(ScheduleCandidateView::unscheduledLessons)
					.thenComparing(ranking);
		}
		return result.stream().sorted(ranking).toList();
	}

	@Transactional(readOnly = true)
	public List<ScheduleCandidateView> list(String semesterCode) {
		return candidates.findBySemesterCodeOrderByGeneratedAtDesc(required(semesterCode, "学期编码"))
				.stream()
				.map(this::view)
				.toList();
	}

	@Transactional(readOnly = true)
	public ScheduleCandidateView get(String candidateId) {
		return view(candidate(candidateId));
	}

	@Transactional(readOnly = true)
	public List<ScheduleCandidateView> compare(List<String> candidateIds) {
		if (candidateIds == null || candidateIds.size() < 2 || candidateIds.size() > 5) {
			throw new IllegalArgumentException("请选择 2 到 5 个候选方案进行对比");
		}
		List<ScheduleCandidatePlan> values = candidates.findAllById(candidateIds);
		if (values.size() != new HashSet<>(candidateIds).size()) {
			throw new IllegalArgumentException("部分候选方案不存在");
		}
		if (values.stream().map(ScheduleCandidatePlan::getSemesterCode).distinct().count() != 1) {
			throw new IllegalArgumentException("只能比较同一学期的候选方案");
		}
		return values.stream()
				.map(this::view)
				.sorted(Comparator.comparing(ScheduleCandidateView::totalScore).reversed())
				.toList();
	}

	@Transactional(readOnly = true)
	public ScheduleDiffView preview(String candidateId) {
		ScheduleCandidatePlan candidate = candidate(candidateId);
		return diff(
				current(candidate.getSemesterCode()),
				readEntries(candidate.getSnapshotJson()),
				candidate.getSemesterCode());
	}

	@Transactional(readOnly = true)
	public ScheduleDiffView publicationPreview(
			String semesterCode,
			List<ScheduleEntry> published) {
		String semester = required(semesterCode, "学期编码");
		return diff(published, current(semester), semester);
	}

	@Transactional
	public ScheduleCandidateView apply(String candidateId, String actor) {
		ScheduleCandidatePlan candidate = candidate(candidateId);
		if (!"CANDIDATE".equals(candidate.getStatus())) {
			throw new IllegalStateException("只有候选状态的方案可以应用");
		}
		if (!"APPROVED".equals(candidate.getReviewStatus())) {
			throw new IllegalStateException("候选方案审核通过后才能应用");
		}
		if (candidate.getUnscheduledLessons() > 0) {
			throw new IllegalStateException("候选方案仍有未排课课时，不能应用");
		}
		terms.findForUpdateByTermCode(candidate.getSemesterCode())
				.orElseThrow(() -> new IllegalArgumentException("学期不存在"));
		List<ScheduleEntry> baseline = current(candidate.getSemesterCode());
		if (!candidate.getBaselineHash().equals(hash(baseline))) {
			throw new IllegalStateException("当前课表在候选方案生成后已变化，请重新生成方案");
		}
		List<ScheduleEntry> snapshot = readEntries(candidate.getSnapshotJson());
		Map<String, ScheduleEntry> existingById = baseline.stream()
				.collect(Collectors.toMap(ScheduleEntry::getId, entry -> entry));
		Set<String> snapshotIds = snapshot.stream()
				.map(ScheduleEntry::getId)
				.filter(id -> id != null && !id.isBlank())
				.collect(Collectors.toSet());
		for (ScheduleEntry old : baseline) {
			if (!snapshotIds.contains(old.getId())) {
				Number references = (Number) entityManager.createNativeQuery("""
						select (select count(*) from edu_exam_course_suspension_item
						        where source_entry_id = :entryId)
						     + (select count(*) from edu_schedule_date_exception
						        where source_entry_id = :entryId)
						""")
						.setParameter("entryId", old.getId())
						.getSingleResult();
				if (references.longValue() > 0) {
					throw new IllegalStateException("课表项已被考试停课或日期例外引用，不能删除：" + old.getId());
				}
			}
		}
		// 考试停课和日期例外会引用原课表主键；保留共存课表项的实体，只更新排课内容。
		// 先在同一事务内移到互不冲突的临时节次，避免互换时段时触发唯一约束。
		int temporaryPeriod = baseline.stream().mapToInt(ScheduleEntry::getPeriodNo)
				.max().orElse(0) + 1;
		for (ScheduleEntry old : baseline) {
			old.setPeriodNo(temporaryPeriod++);
		}
		entityManager.flush();
		for (ScheduleEntry old : baseline) {
			if (!snapshotIds.contains(old.getId())) {
				entries.delete(old);
			}
		}
		for (ScheduleEntry entry : snapshot) {
			ScheduleEntry existing = existingById.get(entry.getId());
			if (existing == null) {
				insertEntry(entry, actor);
			} else {
				existing.setOfferingId(entry.getOfferingId());
				existing.setClassroomId(entry.getClassroomId());
				existing.setDayOfWeek(entry.getDayOfWeek());
				existing.setPeriodNo(entry.getPeriodNo());
				existing.setDurationPeriods(entry.getDurationPeriods());
				existing.setWeekPattern(entry.getWeekPattern());
				existing.setStartWeek(entry.getStartWeek());
				existing.setEndWeek(entry.getEndWeek());
				existing.setStatus(entry.getStatus());
				existing.setSubstituteTeacherId(entry.getSubstituteTeacherId());
				existing.setSourceAdjustmentInstanceId(entry.getSourceAdjustmentInstanceId());
				existing.setLocked(entry.getLocked());
				existing.setLastUpdateBy(actor);
				existing.setLastUpdateTime(LocalDateTime.now());
			}
		}
		entityManager.flush();
		candidate = candidates.findById(candidateId)
				.orElseThrow(() -> new IllegalStateException("候选方案已被并发删除"));
		candidate.setStatus("APPLIED");
		candidate.setAppliedBy(actor);
		candidate.setAppliedAt(LocalDateTime.now());
		candidate = candidates.save(candidate);
		audit.log(
				actor,
				"EDUCATION_AUTO_SCHEDULE_APPLY",
				"candidate=" + candidateId + ", semester=" + candidate.getSemesterCode());
		return view(candidate);
	}

	@Transactional
	public ScheduleCandidateView discard(String candidateId, String actor) {
		ScheduleCandidatePlan candidate = candidate(candidateId);
		if ("APPLIED".equals(candidate.getStatus())) {
			throw new IllegalStateException("已应用方案不能废弃");
		}
		candidate.setStatus("DISCARDED");
		candidate = candidates.save(candidate);
		audit.log(actor, "EDUCATION_AUTO_SCHEDULE_DISCARD", "candidate=" + candidateId);
		return view(candidate);
	}

	@Transactional
	public ScheduleCandidateView updateGovernance(
			String candidateId,
			ScheduleCandidateGovernanceCommand command,
			String actor) {
		ScheduleCandidatePlan candidate = editableCandidate(candidateId);
		assertOwner(candidate, actor);
		if (!"DRAFT".equals(candidate.getReviewStatus())
				&& !"REJECTED".equals(candidate.getReviewStatus())) {
			throw new IllegalStateException("只有草稿或已驳回方案可以编辑协作信息");
		}
		String owner = required(command.ownerUsername(), "负责人");
		var ownerAccount = users.findByUsername(owner);
		if (ownerAccount == null
				|| !Integer.valueOf(1).equals(ownerAccount.getStatus())
				|| Boolean.TRUE.equals(ownerAccount.getAccountLocked())) {
			throw new IllegalArgumentException("负责人账号不存在、已停用或已锁定");
		}
		candidate.setOwnerUsername(owner);
		candidate.setCollaborationRemark(trim(command.remark(), 1000));
		audit.log(actor, "EDUCATION_SCHEDULE_CANDIDATE_UPDATE", "candidate=" + candidateId);
		return view(candidates.save(candidate));
	}

	@Transactional
	public ScheduleCandidateView submitReview(String candidateId, String actor) {
		ScheduleCandidatePlan candidate = editableCandidate(candidateId);
		assertOwner(candidate, actor);
		if (!List.of("DRAFT", "REJECTED").contains(candidate.getReviewStatus())) {
			throw new IllegalStateException("当前方案状态不能提交审核");
		}
		candidate.setReviewStatus("SUBMITTED");
		candidate.setReviewComment(null);
		candidate.setReviewedBy(null);
		candidate.setReviewedAt(null);
		audit.log(actor, "EDUCATION_SCHEDULE_CANDIDATE_SUBMIT", "candidate=" + candidateId);
		return view(candidates.save(candidate));
	}

	@Transactional
	public ScheduleCandidateView review(
			String candidateId,
			boolean approved,
			String comment,
			String actor) {
		ScheduleCandidatePlan candidate = editableCandidate(candidateId);
		if (!"SUBMITTED".equals(candidate.getReviewStatus())) {
			throw new IllegalStateException("只有待审核方案可以审批");
		}
		if (actor.equals(candidate.getOwnerUsername())) {
			throw new IllegalStateException("方案负责人不能审核自己的方案");
		}
		if (!approved && (comment == null || comment.isBlank())) {
			throw new IllegalArgumentException("驳回时必须填写原因");
		}
		candidate.setReviewStatus(approved ? "APPROVED" : "REJECTED");
		candidate.setReviewedBy(actor);
		candidate.setReviewedAt(LocalDateTime.now());
		candidate.setReviewComment(trim(comment, 1000));
		audit.log(
				actor,
				approved ? "EDUCATION_SCHEDULE_CANDIDATE_APPROVE" : "EDUCATION_SCHEDULE_CANDIDATE_REJECT",
				"candidate=" + candidateId);
		return view(candidates.save(candidate));
	}

	private ScheduleCandidatePlan editableCandidate(String candidateId) {
		ScheduleCandidatePlan candidate = candidate(candidateId);
		if (!"CANDIDATE".equals(candidate.getStatus())) {
			throw new IllegalStateException("只有未应用的候选方案可以操作");
		}
		return candidate;
	}

	private void assertOwner(ScheduleCandidatePlan candidate, String actor) {
		if (!actor.equals(candidate.getOwnerUsername())) {
			throw new IllegalStateException("只有方案负责人可以执行该操作");
		}
	}

	private String trim(String value, int maximum) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.substring(0, Math.min(trimmed.length(), maximum));
	}

	private GeneratedPlan buildCandidate(
			GenerationRequest request,
			List<ScheduleEntry> baseline,
			List<CourseOffering> semesterOfferings,
			Set<String> targetIds,
			SchedulePolicy policy,
			BooleanSupplier cancelled,
			int variation,
			ScheduleRunConstraints runConstraints) {
		Map<String, CourseOffering> offeringById = semesterOfferings.stream()
				.collect(Collectors.toMap(CourseOffering::getId, value -> value));
		Map<String, Integer> localDurations = runConstraints.offeringDurations().stream()
				.collect(Collectors.toMap(
						ScheduleRunConstraints.OfferingDuration::offeringId,
						ScheduleRunConstraints.OfferingDuration::periods));
		Map<String, ScheduleRunConstraints.WeekRule> weekRules = runConstraints.weekRules().stream()
				.collect(Collectors.toMap(ScheduleRunConstraints.WeekRule::offeringId, rule -> rule));
		Set<String> temporaryLocks = runConstraints.lockedEntries().stream()
				.map(ScheduleRunConstraints.LockedEntry::entryId).collect(Collectors.toSet());
		Set<String> scopedRules = new HashSet<>(weekRules.keySet());
		scopedRules.addAll(runConstraints.lockedEntries().stream()
				.map(ScheduleRunConstraints.LockedEntry::offeringId).toList());
		List<ScheduleEntry> result = baseline.stream()
				.filter(entry -> "CANCELLED".equals(entry.getStatus())
						|| !targetIds.contains(entry.getOfferingId())
						|| Boolean.TRUE.equals(entry.getLocked()) || temporaryLocks.contains(entry.getId()))
				.map(this::copy)
				.collect(Collectors.toCollection(ArrayList::new));
		Map<String, Deque<String>> reusableIds = baseline.stream()
				.filter(entry -> targetIds.contains(entry.getOfferingId()))
				.filter(entry -> !Boolean.TRUE.equals(entry.getLocked())
						&& !temporaryLocks.contains(entry.getId()))
				// 已取消记录作为历史保留，不能把它的主键复用于新排课项。
				.filter(entry -> !"CANCELLED".equals(entry.getStatus()))
				.collect(Collectors.groupingBy(
						ScheduleEntry::getOfferingId,
						Collectors.mapping(
								ScheduleEntry::getId,
								Collectors.toCollection(ArrayDeque::new))));
		Map<String, Set<String>> studentIds = studentIdsByOffering(
				semesterOfferings.stream()
						.map(CourseOffering::getId)
						.toList());
		Map<String, List<TeacherTimeConstraint>> teacherConstraints = constraints
				.findBySemesterCodeOrderByTeacherIdAscDayOfWeekAscPeriodNoAsc(request.semesterCode())
				.stream()
				.collect(Collectors.groupingBy(TeacherTimeConstraint::getTeacherId));
		for (ScheduleRunConstraints.TeacherSlot rule : runConstraints.teacherSlots()) {
			TeacherTimeConstraint scoped = new TeacherTimeConstraint();
			scoped.setTeacherId(rule.teacherId());
			scoped.setDayOfWeek(rule.dayOfWeek());
			scoped.setPeriodNo(rule.periodNo());
			scoped.setConstraintType(rule.type());
			teacherConstraints.computeIfAbsent(rule.teacherId(), ignored -> new ArrayList<>())
					.add(scoped);
		}
		Map<String, TeacherAcademicProfile> teacherById = teacherProfiles.findAll().stream()
				.collect(Collectors.toMap(TeacherAcademicProfile::getId, item -> item));
		List<Classroom> availableRooms = classrooms.findByEnabledTrueOrderByRoomCode();
		// 公共课可跨校区，但只能使用本学期实际开课的校区，避免选到其他演示学校的教室。
		Set<String> termCampusIds = semesterOfferings.stream()
				.map(CourseOffering::getCampusId)
				.filter(id -> id != null && !id.isBlank())
				.collect(Collectors.toSet());
		Map<String, Classroom> roomById = classrooms.findAll().stream()
				.collect(Collectors.toMap(Classroom::getId, room -> room));
		List<ClassroomUnavailableSlot> roomUnavailableSlots = unavailableSlots
				.findBySemesterCodeOrderByClassroomIdAscDayOfWeekAscStartPeriodAsc(request.semesterCode())
				.stream()
				.filter(item -> "ACTIVE".equals(item.getStatus()))
				.toList();
		List<CourseOffering> targets = semesterOfferings.stream()
				.filter(offering -> targetIds.contains(offering.getId()))
				.sorted(Comparator.comparing(CourseOffering::getStudentCount).reversed()
						.thenComparing(
								Comparator.comparing(CourseOffering::getWeeklyLessons).reversed())
						.thenComparing(CourseOffering::getOfferingCode))
				.toList();
		List<Slot> slots = slots(request.weekdays(), request.periodsPerDay(), variation);
		// 热点循环只查询内存索引，避免每个“时段 × 教室”组合重复扫描完整课表。
		SchedulingIndex schedulingIndex = new SchedulingIndex(
				result,
				offeringById,
				roomById,
				studentIds,
				true,
				!runConstraints.softPriorities().isEmpty());
		int scheduled = 0;
		int unscheduled = 0;
		int preferredHits = 0;
		int sameCourseDayPenalty = 0;
		int teacherLoadPenalty = 0;
		int consecutivePenalty = 0;
		int campusSwitchPenalty = 0;
		int teacherGapPenalty = 0;
		int consecutiveBlockHits = 0;
		int teacherDayConcentrationHits = 0;
		int scopedPreferenceAdjustment = 0;
		List<ScheduleRunConstraints.SlotExclusion> slotExclusions = runConstraints.slotExclusions();
		Map<String, Set<Integer>> periodsByCampus = new HashMap<>();
		for (CourseOffering offering : targets) {
			checkCancelled(cancelled);
			ScheduleRunConstraints.WeekRule weekRule = weekRules.get(offering.getId());
			String pattern = weekRule == null
					? offering.getWeekPattern() == null ? "ALL" : offering.getWeekPattern()
					: weekRule.weekPattern();
			int startWeek = weekRule == null ? request.startWeek() : weekRule.startWeek();
			int endWeek = weekRule == null ? request.endWeek() : weekRule.endWeek();
			long actualWeeks = weekMask(pattern, startWeek, endWeek);
			if (actualWeeks == 0) {
				throw new IllegalArgumentException("教学周规则没有实际授课周");
			}
			long activeWeeks = schedulingIndex.weekAware ? actualWeeks : weekMask("ALL", 1, 1);
			if ((scopedRules.contains(offering.getId()) || schedulingIndex.weekAware)
					&& result.stream()
					.filter(entry -> offering.getId().equals(entry.getOfferingId()))
					.filter(entry -> !"CANCELLED".equals(entry.getStatus()))
					.anyMatch(entry -> weekMask(entry.getWeekPattern() == null ? "ALL" : entry.getWeekPattern(),
							entry.getStartWeek(), entry.getEndWeek()) != actualWeeks)) {
				throw new IllegalArgumentException("临时锁课与本轮授课周规则不一致");
			}
			int lockedLessons = result.stream()
					.filter(entry -> offering.getId().equals(entry.getOfferingId()))
					.filter(entry -> !"CANCELLED".equals(entry.getStatus()))
					.mapToInt(entry -> entry.getDurationPeriods() == null ? 1 : entry.getDurationPeriods())
					.sum();
			int requiredLessons = Math.max(0, offering.getWeeklyLessons() - lockedLessons);
			int preferredDuration = Math.max(1, localDurations.getOrDefault(offering.getId(),
					offering.getPreferredDurationPeriods() == null ? 1
							: offering.getPreferredDurationPeriods()));
			for (int lesson = 0; lesson < requiredLessons;) {
				checkCancelled(cancelled);
				int duration = Math.min(preferredDuration, requiredLessons - lesson);
				Placement placement = bestPlacement(
						offering,
						slots,
						availableRooms,
						schedulingIndex,
						teacherConstraints.getOrDefault(offering.getTeacherId(), List.of()),
						periodsByCampus, termCampusIds, request,
						duration,
						roomUnavailableSlots,
						teacherById.get(offering.getTeacherId()),
						policy, activeWeeks, runConstraints.softPriorities(), slotExclusions);
				if (placement == null && duration == 2
						&& localDurations.containsKey(offering.getId())) {
					duration = 1;
					placement = bestPlacement(
							offering, slots, availableRooms, schedulingIndex,
							teacherConstraints.getOrDefault(offering.getTeacherId(), List.of()),
							periodsByCampus, termCampusIds, request, duration, roomUnavailableSlots,
							teacherById.get(offering.getTeacherId()), policy,
							activeWeeks, runConstraints.softPriorities(), slotExclusions);
				}
				if (placement == null) {
					unscheduled += duration;
					lesson += duration;
					continue;
				}
				ScheduleEntry generated = new ScheduleEntry();
				Deque<String> ids = reusableIds.get(offering.getId());
				if (ids != null && !ids.isEmpty()) {
					generated.setId(ids.removeFirst());
				}
				generated.setSemesterCode(request.semesterCode());
				generated.setOfferingId(offering.getId());
				generated.setClassroomId(placement.classroom().getId());
				generated.setDayOfWeek(placement.slot().day());
				generated.setPeriodNo(placement.slot().period());
				generated.setDurationPeriods(duration);
				generated.setWeekPattern(pattern);
				generated.setStartWeek(startWeek);
				generated.setEndWeek(endWeek);
				generated.setStatus("SCHEDULED");
				generated.setLocked(false);
				result.add(generated);
				schedulingIndex.add(generated);
				scheduled += duration;
				if (duration == 2 && localDurations.containsKey(offering.getId())) {
					consecutiveBlockHits++;
				}
				lesson += duration;
				if (placement.preferred()) {
					preferredHits++;
				}
				sameCourseDayPenalty += placement.sameCourseDayCount();
				teacherLoadPenalty += placement.teacherDayLoad();
				consecutivePenalty += placement.consecutiveLoad();
				campusSwitchPenalty += placement.campusSwitches();
				teacherGapPenalty += placement.teacherGaps();
				scopedPreferenceAdjustment += placement.preferenceAdjustment();
				if (placement.teacherDayLoad() > 0
						&& runConstraints.softPriorities().contains(
								new ScheduleRunConstraints.SoftPriority("SAME_DAY", offering.getTeacherId()))) {
					teacherDayConcentrationHits++;
				}
			}
		}
		result.sort(entryComparator());
		List<ScheduleCandidateMetrics.SlotRuleCheck> slotRuleChecks = slotExclusions.stream()
				.map(rule -> new ScheduleCandidateMetrics.SlotRuleCheck(
						rule.targetType(), rule.targetId(), rule.dayOfWeek(), rule.periodNo(),
						(int) result.stream()
								.filter(entry -> !"CANCELLED".equals(entry.getStatus()))
								.filter(entry -> entry.getDayOfWeek() == rule.dayOfWeek()
										&& entry.getPeriodNo() <= rule.periodNo()
										&& entry.getPeriodNo() + entry.getDurationPeriods() > rule.periodNo())
								.filter(entry -> matchesSlotRule(rule, offeringById.get(entry.getOfferingId()),
										entry.getOfferingId())).count()))
				.toList();
		if (slotRuleChecks.stream().anyMatch(check -> check.violations() != 0)) {
			throw new IllegalStateException("现有课表保留项与本轮组合禁排规则冲突");
		}
		int score = scheduled * policy.getScheduledLessonReward()
				+ preferredHits * policy.getPreferredSlotReward()
				- sameCourseDayPenalty * policy.getSameCourseDayPenalty()
				- teacherLoadPenalty * policy.getTeacherLoadPenalty()
				- consecutivePenalty * policy.getConsecutivePenalty()
				- campusSwitchPenalty * policy.getCampusSwitchPenalty()
				- teacherGapPenalty * policy.getTeacherGapPenalty()
				- unscheduled * policy.getUnscheduledLessonPenalty()
				+ scopedPreferenceAdjustment;
		return new GeneratedPlan(
				result,
				new ScheduleCandidateMetrics(
						scheduled,
						unscheduled,
						preferredHits,
						sameCourseDayPenalty,
						teacherLoadPenalty,
						consecutivePenalty,
						campusSwitchPenalty,
						teacherGapPenalty,
						score,
						consecutiveBlockHits,
						teacherDayConcentrationHits,
						slotRuleChecks));
	}

	private boolean matchesSlotRule(ScheduleRunConstraints.SlotExclusion rule,
			CourseOffering offering, String offeringId) {
		return "OFFERING".equals(rule.targetType())
				? rule.targetId().equals(offeringId)
				: offering != null && rule.targetId().equals(offering.getTeacherId());
	}

	private void checkCancelled(BooleanSupplier cancelled) {
		if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()) {
			throw new java.util.concurrent.CancellationException("自动排课任务已取消");
		}
	}

	private Placement bestPlacement(
			CourseOffering offering,
			List<Slot> slots,
			List<Classroom> availableRooms,
			SchedulingIndex schedulingIndex,
			List<TeacherTimeConstraint> teacherConstraints,
			Map<String, Set<Integer>> periodsByCampus,
			Set<String> termCampusIds,
			GenerationRequest request,
			int duration,
			List<ClassroomUnavailableSlot> roomUnavailableSlots,
			TeacherAcademicProfile teacher,
			SchedulePolicy policy,
			long weeks,
			List<ScheduleRunConstraints.SoftPriority> softPriorities,
			List<ScheduleRunConstraints.SlotExclusion> slotExclusions) {
		Placement best = null;
		Set<String> priorities = softPriorities.stream()
				.filter(rule -> offering.getTeacherId().equals(rule.teacherId()))
				.map(ScheduleRunConstraints.SoftPriority::kind)
				.collect(Collectors.toSet());
		for (Slot slot : slots) {
			if (slotExclusions.stream().anyMatch(rule ->
					rule.dayOfWeek() == slot.day()
							&& rule.periodNo() >= slot.period()
							&& rule.periodNo() < slot.period() + duration
							&& ("OFFERING".equals(rule.targetType())
									? rule.targetId().equals(offering.getId())
									: rule.targetId().equals(offering.getTeacherId())))) {
				continue;
			}
			int maxDaily = teacher == null || teacher.getMaxDailyLessons() == null
					? policy.getDefaultMaxDailyLessons() : teacher.getMaxDailyLessons();
			int maxConsecutive = teacher == null || teacher.getMaxConsecutiveLessons() == null
					? policy.getDefaultMaxConsecutiveLessons() : teacher.getMaxConsecutiveLessons();
			if (schedulingIndex.teacherDayLoad(offering, slot, weeks) + duration > maxDaily
					|| schedulingIndex.teacherWeeklyLoad(offering, weeks) + duration
							> (teacher == null || teacher.getMaxWeeklyLessons() == null
									? policy.getDefaultMaxWeeklyLessons() : teacher.getMaxWeeklyLessons())
					|| schedulingIndex.consecutiveLoad(offering, slot, duration, weeks) > maxConsecutive) {
				continue;
			}
			if (forbidden(teacherConstraints, slot, duration)) {
				continue;
			}
			for (Classroom room : availableRooms) {
				if (!roomSuitable(offering, room)
						|| !termCampusIds.contains(room.getCampusId())
						|| roomUnavailable(roomUnavailableSlots, room, slot, duration)
						|| schedulingIndex.conflicts(offering, room, slot, duration, weeks)) {
					continue;
				}
				Set<Integer> allowedPeriods = periodsByCampus.computeIfAbsent(
						room.getCampusId(), campus -> academicCalendar.schedulablePeriodNumbers(
								request.semesterCode(), campus, request.periodsPerDay()));
				if (!consecutivePeriodsAllowed(allowedPeriods, slot.period(), duration)
						|| schedulingIndex.violatesCampusTravelGap(offering, room, slot, duration,
								policy.getMinimumCampusTravelPeriods(), weeks)) {
					continue;
				}
				int sameDay = schedulingIndex.sameCourseDayCount(offering, slot, weeks);
				int teacherDayLoad = schedulingIndex.teacherDayLoad(offering, slot, weeks);
				int consecutiveLoad = schedulingIndex.consecutiveLoad(offering, slot, duration, weeks);
				int campusSwitches = schedulingIndex.campusSwitches(offering, room, slot, weeks);
				int teacherGaps = schedulingIndex.teacherGapIncrease(offering, slot, duration, weeks);
				boolean preferred = preferred(teacherConstraints, slot);
				int preferenceAdjustment = -(priorities.contains("CAMPUS_SWITCH")
						? campusSwitches * Math.min(policy.getCampusSwitchPenalty(), 25) : 0)
						- (priorities.contains("TEACHER_GAP")
								? teacherGaps * Math.min(policy.getTeacherGapPenalty(), 10) : 0)
						+ (priorities.contains("SAME_DAY") ? teacherDayLoad
								* Math.max(12, Math.min(policy.getTeacherLoadPenalty() * 4, 20)) : 0);
				int penalty = sameDay * policy.getSameCourseDayPenalty()
						+ teacherDayLoad * policy.getTeacherLoadPenalty()
						+ consecutiveLoad * policy.getConsecutivePenalty()
						+ campusSwitches * policy.getCampusSwitchPenalty()
						+ teacherGaps * policy.getTeacherGapPenalty()
						- preferenceAdjustment
						- (preferred ? policy.getPreferredSlotReward() : 0);
				Placement candidate = new Placement(
						slot, room, penalty, preferred, sameDay,
						teacherDayLoad, consecutiveLoad, campusSwitches, teacherGaps,
						preferenceAdjustment);
				if (best == null || candidate.penalty() < best.penalty()) {
					best = candidate;
				}
			}
		}
		return best;
	}

	private boolean roomSuitable(CourseOffering offering, Classroom room) {
		return room.getCapacity() >= offering.getStudentCount()
				&& containsAllCodes(room.getEquipmentCodes(), offering.getRequiredEquipmentCodes())
				&& (offering.getRequiredRoomType() == null
						|| offering.getRequiredRoomType().isBlank()
						|| offering.getRequiredRoomType().equals(room.getRoomType()))
				&& (offering.getCampusId() == null
						|| offering.getCampusId().isBlank()
						|| offering.getCampusId().equals(room.getCampusId()));
	}

	private boolean roomUnavailable(
			List<ClassroomUnavailableSlot> values,
			Classroom room,
			Slot slot,
			int duration) {
		return values.stream().anyMatch(value -> room.getId().equals(value.getClassroomId())
				&& value.getDayOfWeek() == slot.day()
				&& slot.period() <= value.getEndPeriod()
				&& slot.period() + duration - 1 >= value.getStartPeriod());
	}

	private boolean containsAllCodes(String actual, String requiredCodes) {
		if (requiredCodes == null || requiredCodes.isBlank()) {
			return true;
		}
		Set<String> actualValues = java.util.Arrays.stream(
				actual == null ? new String[0] : actual.split(","))
				.map(String::trim)
				.filter(value -> !value.isBlank())
				.collect(Collectors.toSet());
		return java.util.Arrays.stream(requiredCodes.split(","))
				.map(String::trim)
				.filter(value -> !value.isBlank())
				.allMatch(actualValues::contains);
	}

	private boolean forbidden(List<TeacherTimeConstraint> values, Slot slot, int duration) {
		return values.stream().anyMatch(value -> "FORBIDDEN".equals(value.getConstraintType())
				&& value.getDayOfWeek() == slot.day()
				&& value.getPeriodNo() >= slot.period()
				&& value.getPeriodNo() < slot.period() + duration);
	}

	private boolean consecutivePeriodsAllowed(Set<Integer> values, int start, int duration) {
		for (int period = start; period < start + duration; period++) {
			if (!values.contains(period)) {
				return false;
			}
		}
		return true;
	}

	private boolean preferred(List<TeacherTimeConstraint> values, Slot slot) {
		return values.stream().anyMatch(value -> "PREFERRED".equals(value.getConstraintType())
				&& value.getDayOfWeek() == slot.day()
				&& value.getPeriodNo() == slot.period());
	}

	private Map<String, Set<String>> studentIdsByOffering(List<String> offeringIds) {
		if (offeringIds.isEmpty()) {
			return Map.of();
		}
		return members.findByOfferingIdInAndEnrollmentStatus(
				offeringIds,
				"ENROLLED").stream()
				.collect(Collectors.groupingBy(
						TeachingClassMember::getOfferingId,
						Collectors.mapping(
								TeachingClassMember::getStudentId,
								Collectors.toSet())));
	}

	private List<Slot> slots(int weekdays, int periodsPerDay, int variation) {
		List<Slot> slots = new ArrayList<>();
		for (int day = 1; day <= weekdays; day++) {
			for (int period = 1; period <= periodsPerDay; period++) {
				slots.add(new Slot(day, period));
			}
		}
		if (!slots.isEmpty()) {
			java.util.Collections.rotate(slots, -(variation % slots.size()));
		}
		return slots;
	}

	private ScheduleDiffView diff(
			List<ScheduleEntry> before,
			List<ScheduleEntry> after,
			String semesterCode) {
		Map<String, CourseOffering> offeringById = offerings
				.findBySemesterCodeOrderByOfferingCode(semesterCode)
				.stream()
				.collect(Collectors.toMap(CourseOffering::getId, value -> value));
		Map<String, Classroom> roomById = classrooms.findAll().stream()
				.collect(Collectors.toMap(Classroom::getId, value -> value));
		Set<String> offeringIds = new HashSet<>();
		before.forEach(entry -> offeringIds.add(entry.getOfferingId()));
		after.forEach(entry -> offeringIds.add(entry.getOfferingId()));
		List<ScheduleDiffItem> items = new ArrayList<>();
		int added = 0;
		int removed = 0;
		int moved = 0;
		int unchanged = 0;
		for (String offeringId : offeringIds) {
			List<ScheduleEntry> oldEntries = before.stream()
					.filter(entry -> offeringId.equals(entry.getOfferingId()))
					.sorted(entryComparator())
					.collect(Collectors.toCollection(ArrayList::new));
			List<ScheduleEntry> newEntries = after.stream()
					.filter(entry -> offeringId.equals(entry.getOfferingId()))
					.sorted(entryComparator())
					.collect(Collectors.toCollection(ArrayList::new));
			for (int oldIndex = oldEntries.size() - 1; oldIndex >= 0; oldIndex--) {
				ScheduleEntry oldEntry = oldEntries.get(oldIndex);
				int exact = indexOfSlot(newEntries, oldEntry);
				if (exact >= 0) {
					unchanged++;
					oldEntries.remove(oldIndex);
					newEntries.remove(exact);
				}
			}
			CourseOffering offering = offeringById.get(offeringId);
			while (!oldEntries.isEmpty() && !newEntries.isEmpty()) {
				ScheduleEntry oldEntry = oldEntries.remove(0);
				ScheduleEntry newEntry = newEntries.remove(0);
				items.add(diffItem("MOVED", offering, oldEntry, newEntry, roomById));
				moved++;
			}
			for (ScheduleEntry oldEntry : oldEntries) {
				items.add(diffItem("REMOVED", offering, oldEntry, null, roomById));
				removed++;
			}
			for (ScheduleEntry newEntry : newEntries) {
				items.add(diffItem("ADDED", offering, null, newEntry, roomById));
				added++;
			}
		}
		return new ScheduleDiffView(added, removed, moved, unchanged, items);
	}

	private int indexOfSlot(List<ScheduleEntry> values, ScheduleEntry expected) {
		for (int index = 0; index < values.size(); index++) {
			if (slotKey(values.get(index)).equals(slotKey(expected))) {
				return index;
			}
		}
		return -1;
	}

	private ScheduleDiffItem diffItem(
			String type,
			CourseOffering offering,
			ScheduleEntry before,
			ScheduleEntry after,
			Map<String, Classroom> roomById) {
		return new ScheduleDiffItem(
				type,
				offering == null ? "" : offering.getId(),
				offering == null ? "未知课程" : offering.getCourseName(),
				offering == null ? "" : offering.getTeachingClassName(),
				offering == null ? "" : offering.getTeacherName(),
				slotDescription(before, roomById),
				slotDescription(after, roomById));
	}

	private String slotDescription(
			ScheduleEntry entry,
			Map<String, Classroom> roomById) {
		if (entry == null) {
			return "";
		}
		Classroom room = roomById.get(entry.getClassroomId());
		return "周" + entry.getDayOfWeek()
				+ " 第" + entry.getPeriodNo() + "节"
				+ " / " + (room == null ? "未知教室" : room.getRoomName());
	}

	private String slotKey(ScheduleEntry entry) {
		return entry.getDayOfWeek() + "|"
				+ entry.getPeriodNo() + "|"
				+ entry.getDurationPeriods() + "|"
				+ entry.getClassroomId() + "|"
				+ entry.getWeekPattern() + "|"
				+ entry.getStartWeek() + "|"
				+ entry.getEndWeek();
	}

	private void insertEntry(ScheduleEntry entry, String actor) {
		String id = entry.getId() == null || entry.getId().isBlank()
				? java.util.UUID.randomUUID().toString()
				: entry.getId();
		LocalDateTime now = LocalDateTime.now();
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
				.setParameter("id", id)
				.setParameter("createBy", entry.getCreateBy() == null ? actor : entry.getCreateBy())
				.setParameter("createTime", entry.getCreateTime() == null ? now : entry.getCreateTime())
				.setParameter("lastUpdateBy", actor)
				.setParameter("lastUpdateTime", now)
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
				.setParameter("locked", Boolean.TRUE.equals(entry.getLocked()))
				.executeUpdate();
	}

	private GenerationRequest validate(AutoScheduleCommand command) {
		if (command == null) {
			throw new IllegalArgumentException("自动排课参数不能为空");
		}
		String mode = command.mode() == null ? "FULL" : command.mode().trim().toUpperCase();
		if (!Set.of("FULL", "LOCAL").contains(mode)) {
			throw new IllegalArgumentException("排课模式必须为 FULL 或 LOCAL");
		}
		Set<String> selected = command.selectedOfferingIds() == null
				? Set.of()
				: command.selectedOfferingIds().stream()
						.filter(value -> value != null && !value.isBlank())
						.collect(Collectors.toSet());
		if ("LOCAL".equals(mode) && selected.isEmpty()) {
			throw new IllegalArgumentException("局部重排至少选择一个教学任务");
		}
		int startWeek = range(command.startWeek(), 1, 52, 1, "开始周");
		int endWeek = range(command.endWeek(), 1, 52, 20, "结束周");
		if (endWeek < startWeek) {
			throw new IllegalArgumentException("结束周不能早于开始周");
		}
		return new GenerationRequest(
				required(command.semesterCode(), "学期编码"),
				required(command.planName(), "方案名称"),
				mode,
				selected,
				range(command.candidateCount(), 1, MAX_CANDIDATES, 3, "候选方案数"),
				range(command.weekdays(), 1, 7, DEFAULT_WEEKDAYS, "上课天数"),
				range(command.periodsPerDay(), 1, 20, DEFAULT_PERIODS, "每日节数"),
				startWeek,
				endWeek);
	}

	private Set<String> targetOfferingIds(
			GenerationRequest request,
			List<CourseOffering> semesterOfferings) {
		Set<String> available = semesterOfferings.stream()
				.map(CourseOffering::getId)
				.collect(Collectors.toSet());
		if ("FULL".equals(request.mode())) {
			return available;
		}
		if (!available.containsAll(request.selectedOfferingIds())) {
			throw new IllegalArgumentException("局部重排包含不属于当前学期的教学任务");
		}
		return request.selectedOfferingIds();
	}

	private int range(
			Integer value,
			int minimum,
			int maximum,
			int defaultValue,
			String label) {
		int resolved = value == null ? defaultValue : value;
		if (resolved < minimum || resolved > maximum) {
			throw new IllegalArgumentException(label + "必须在 " + minimum + " 到 " + maximum + " 之间");
		}
		return resolved;
	}

	private List<ScheduleEntry> current(String semesterCode) {
		return entries.findBySemesterCodeOrderByDayOfWeekAscPeriodNoAsc(semesterCode).stream()
				.toList();
	}

	private ScheduleCandidatePlan candidate(String id) {
		return candidates.findById(required(id, "候选方案"))
				.orElseThrow(() -> new IllegalArgumentException("候选方案不存在"));
	}

	private ScheduleCandidateView view(ScheduleCandidatePlan candidate) {
		return new ScheduleCandidateView(
				candidate.getId(),
				candidate.getSemesterCode(),
				candidate.getPlanName(),
				candidate.getGenerationMode(),
				candidate.getEntryCount(),
				candidate.getUnscheduledLessons(),
				candidate.getTotalScore(),
				candidate.getStatus(),
				candidate.getReviewStatus(),
				candidate.getOwnerUsername(),
				candidate.getCollaborationRemark(),
				candidate.getReviewedBy(),
				candidate.getReviewedAt(),
				candidate.getReviewComment(),
				candidate.getGeneratedBy(),
				candidate.getGeneratedAt(),
				candidate.getAppliedBy(),
				candidate.getAppliedAt(),
				read(candidate.getMetricsJson(), ScheduleCandidateMetrics.class));
	}

	private ScheduleEntry copy(ScheduleEntry source) {
		return json.convertValue(source, ScheduleEntry.class);
	}

	private List<ScheduleEntry> readEntries(String value) {
		try {
			return json.readValue(value, new TypeReference<>() { });
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("候选方案快照损坏", exception);
		}
	}

	private <T> T read(String value, Class<T> type) {
		try {
			/*
			 * Candidate rows can survive application upgrades. Older showcase and
			 * pre-governance rows contain additional metrics that are no longer part
			 * of the typed contract; unknown fields are safe to ignore here while
			 * malformed JSON still fails explicitly.
			 */
			return json.readerFor(type)
					.without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
					.readValue(value);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("候选方案指标损坏", exception);
		}
	}

	private String write(Object value) {
		try {
			return json.writeValueAsString(value);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("候选方案序列化失败", exception);
		}
	}

	private String hash(List<ScheduleEntry> values) {
		String canonical = values.stream()
				.sorted(entryComparator())
				.map(entry -> (entry.getId() == null ? "" : entry.getId())
						+ "|" + entry.getOfferingId()
						+ "|" + slotKey(entry)
						+ "|" + entry.getStatus()
						+ "|" + entry.getLocked())
				.collect(Collectors.joining("\n"));
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(canonical.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("运行环境不支持 SHA-256", exception);
		}
	}

	private Comparator<ScheduleEntry> entryComparator() {
		return Comparator.comparing(ScheduleEntry::getOfferingId)
				.thenComparing(ScheduleEntry::getDayOfWeek)
				.thenComparing(ScheduleEntry::getPeriodNo)
				.thenComparing(entry -> entry.getId() == null ? "" : entry.getId());
	}

	private String required(String value, String label) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(label + "不能为空");
		}
		return value.trim();
	}

	private record GenerationRequest(
			String semesterCode,
			String planName,
			String mode,
			Set<String> selectedOfferingIds,
			int candidateCount,
			int weekdays,
			int periodsPerDay,
			int startWeek,
			int endWeek) {
	}

	private record Slot(int day, int period) {
	}

	private record Placement(
			Slot slot,
			Classroom classroom,
			int penalty,
			boolean preferred,
			int sameCourseDayCount,
			int teacherDayLoad,
			int consecutiveLoad,
			int campusSwitches,
			int teacherGaps,
			int preferenceAdjustment) {
	}

	private record GeneratedPlan(
			List<ScheduleEntry> entries,
			ScheduleCandidateMetrics metrics) {
	}

	private static long weekMask(String pattern, int start, int end) {
		if (!Set.of("ALL", "ODD", "EVEN").contains(pattern)
				|| start < 1 || end > 52 || start > end) {
			throw new IllegalArgumentException("课表授课周规则无效");
		}
		long mask = 0;
		for (int week = start; week <= end; week++) {
			if ("ALL".equals(pattern) || ("ODD".equals(pattern) == (week % 2 == 1))) {
				mask |= 1L << week;
			}
		}
		return mask;
	}

	/** 当前候选方案的冲突和评分索引；添加新课时后增量更新。 */
	private static final class SchedulingIndex {
		private final Map<String, CourseOffering> offeringById;
		private final Map<String, Classroom> roomById;
		private final Map<String, Set<String>> studentIds;
		private final boolean weekAware;
		private final boolean scopedPreferences;
		private final Map<String, Long> roomSlots = new HashMap<>();
		private final Map<String, Long> offeringSlots = new HashMap<>();
		private final Map<String, Long> teacherSlots = new HashMap<>();
		private final Map<String, Long> studentSlots = new HashMap<>();
		private final Map<String, Integer> offeringDayCounts = new HashMap<>();
		private final Map<String, Integer> teacherDayCounts = new HashMap<>();
		private final Map<String, Integer> teacherWeeklyCounts = new HashMap<>();
		private final Map<String, Set<Integer>> teacherDayPeriods = new HashMap<>();
		private final Map<String, Set<String>> teacherDayCampuses = new HashMap<>();
		private final Map<String, Map<Integer, String>> teacherDayPeriodCampuses = new HashMap<>();

		private SchedulingIndex(
				List<ScheduleEntry> scheduled,
				Map<String, CourseOffering> offeringById,
				Map<String, Classroom> roomById,
				Map<String, Set<String>> studentIds,
				boolean weekAware,
				boolean scopedPreferences) {
			this.offeringById = offeringById;
			this.roomById = roomById;
			this.studentIds = studentIds;
			this.weekAware = weekAware;
			this.scopedPreferences = scopedPreferences;
			// 已取消记录仍保留在表中，唯一约束也覆盖它们；新课不能复用其相同教学任务与时段。
			scheduled.stream()
					.filter(entry -> "CANCELLED".equals(entry.getStatus()))
					.forEach(entry -> offeringSlots.put(
							slot(entry.getDayOfWeek(), entry.getPeriodNo()) + "|" + entry.getOfferingId(),
							-1L));
			scheduled.stream()
					.filter(entry -> !"CANCELLED".equals(entry.getStatus()))
					.forEach(this::add);
		}

		private void add(ScheduleEntry entry) {
			CourseOffering offering = offeringById.get(entry.getOfferingId());
			if (offering == null) {
				return;
			}
			Classroom classroom = roomById.get(entry.getClassroomId());
			String campusId = classroom == null ? offering.getCampusId() : classroom.getCampusId();
			if (campusId == null) {
				campusId = "";
			}
			long weeks = weekAware
					? weekMask(entry.getWeekPattern() == null ? "ALL" : entry.getWeekPattern(),
							entry.getStartWeek(), entry.getEndWeek())
					: weekMask("ALL", 1, 1);
			int duration = entry.getDurationPeriods() == null
					? 1
					: Math.max(1, entry.getDurationPeriods());
			for (int offset = 0; offset < duration; offset++) {
				String slot = slot(entry.getDayOfWeek(), entry.getPeriodNo() + offset);
				roomSlots.merge(slot + "|" + entry.getClassroomId(), weeks, (a, b) -> a | b);
				offeringSlots.merge(slot + "|" + entry.getOfferingId(), weeks, (a, b) -> a | b);
				teacherSlots.merge(slot + "|" + offering.getTeacherId(), weeks, (a, b) -> a | b);
				studentIds.getOrDefault(entry.getOfferingId(), Set.of())
						.forEach(studentId -> studentSlots.merge(slot + "|" + studentId,
								weeks, (a, b) -> a | b));
			}
			for (long remaining = weeks; remaining != 0; remaining &= remaining - 1) {
				int week = Long.numberOfTrailingZeros(remaining);
				String suffix = "|" + week;
				offeringDayCounts.merge(entry.getOfferingId() + "|" + entry.getDayOfWeek() + suffix,
						1, Integer::sum);
				teacherDayCounts.merge(offering.getTeacherId() + "|" + entry.getDayOfWeek() + suffix,
						duration, Integer::sum);
				teacherWeeklyCounts.merge(offering.getTeacherId() + suffix, duration, Integer::sum);
				String teacherDay = offering.getTeacherId() + "|" + entry.getDayOfWeek() + suffix;
				Set<Integer> periods = teacherDayPeriods.computeIfAbsent(teacherDay,
						key -> new HashSet<>());
				for (int offset = 0; offset < duration; offset++) {
					periods.add(entry.getPeriodNo() + offset);
					teacherDayPeriodCampuses.computeIfAbsent(teacherDay, key -> new HashMap<>())
							.put(entry.getPeriodNo() + offset,
									campusId);
				}
				teacherDayCampuses.computeIfAbsent(teacherDay, key -> new HashSet<>())
						.add(campusId);
			}
		}

		private boolean conflicts(CourseOffering offering, Classroom room, Slot slot,
				int duration, long weeks) {
			for (int offset = 0; offset < duration; offset++) {
				String slotKey = slot(slot.day(), slot.period() + offset);
				if (occupied(roomSlots, slotKey + "|" + room.getId(), weeks)
						|| occupied(offeringSlots, slotKey + "|" + offering.getId(), weeks)
						|| occupied(teacherSlots, slotKey + "|" + offering.getTeacherId(), weeks)
						|| studentIds.getOrDefault(offering.getId(), Set.of()).stream()
								.anyMatch(studentId -> occupied(studentSlots,
										slotKey + "|" + studentId, weeks))) {
					return true;
				}
			}
			return false;
		}

		private boolean occupied(Map<String, Long> slots, String key, long weeks) {
			return (slots.getOrDefault(key, 0L) & weeks) != 0;
		}

		private int sameCourseDayCount(CourseOffering offering, Slot slot, long weeks) {
			return maxCount(offeringDayCounts, offering.getId() + "|" + slot.day(), weeks);
		}

		private int teacherDayLoad(CourseOffering offering, Slot slot, long weeks) {
			return maxCount(teacherDayCounts, offering.getTeacherId() + "|" + slot.day(), weeks);
		}

		private int teacherWeeklyLoad(CourseOffering offering, long weeks) {
			return maxCount(teacherWeeklyCounts, offering.getTeacherId(), weeks);
		}

		private int maxCount(Map<String, Integer> counts, String prefix, long weeks) {
			int maximum = 0;
			for (long remaining = weeks; remaining != 0; remaining &= remaining - 1) {
				maximum = Math.max(maximum, counts.getOrDefault(
						prefix + "|" + Long.numberOfTrailingZeros(remaining), 0));
			}
			return maximum;
		}

		private int consecutiveLoad(CourseOffering offering, Slot slot, int duration, long weeks) {
			int largest = 0;
			for (long remaining = weeks; remaining != 0; remaining &= remaining - 1) {
				Set<Integer> periods = new HashSet<>(teacherDayPeriods.getOrDefault(
						offering.getTeacherId() + "|" + slot.day() + "|"
								+ Long.numberOfTrailingZeros(remaining), Set.of()));
				for (int offset = 0; offset < duration; offset++) {
					periods.add(slot.period() + offset);
				}
				int run = 0;
				int maximum = 0;
				for (int period = 1; period <= 20; period++) {
					run = periods.contains(period) ? run + 1 : 0;
					maximum = Math.max(maximum, run);
				}
				largest = Math.max(largest, maximum);
			}
			return largest;
		}

		private int campusSwitches(CourseOffering offering, Classroom room, Slot slot, long weeks) {
			String campus = room.getCampusId();
			for (long remaining = weeks; remaining != 0; remaining &= remaining - 1) {
				Set<String> campuses = teacherDayCampuses.getOrDefault(
						offering.getTeacherId() + "|" + slot.day() + "|"
								+ Long.numberOfTrailingZeros(remaining), Set.of());
				if (scopedPreferences
						? campuses.stream().anyMatch(other -> !campus.equals(other))
						: !campuses.isEmpty() && !campuses.contains(campus)) {
					return 1;
				}
			}
			return 0;
		}

		private boolean violatesCampusTravelGap(
				CourseOffering offering,
				Classroom room,
				Slot slot,
				int duration,
				int minimumGap, long weeks) {
			if (minimumGap <= 0) {
				return false;
			}
			String targetCampus = room.getCampusId();
			int start = slot.period();
			int end = slot.period() + duration - 1;
			for (long remaining = weeks; remaining != 0; remaining &= remaining - 1) {
				String teacherDay = offering.getTeacherId() + "|" + slot.day() + "|"
						+ Long.numberOfTrailingZeros(remaining);
				Map<Integer, String> occupied = teacherDayPeriodCampuses.getOrDefault(
						teacherDay, Map.of());
				if (occupied.entrySet().stream().anyMatch(item -> {
					if (targetCampus.equals(item.getValue())) {
						return false;
					}
					int distance = item.getKey() < start
							? start - item.getKey() - 1
							: item.getKey() - end - 1;
					return distance < minimumGap;
				})) {
					return true;
				}
			}
			return false;
		}

		private int teacherGapIncrease(CourseOffering offering, Slot slot, int duration, long weeks) {
			int maximum = 0;
			for (long remaining = weeks; remaining != 0; remaining &= remaining - 1) {
				Set<Integer> before = teacherDayPeriods.getOrDefault(
						offering.getTeacherId() + "|" + slot.day() + "|"
								+ Long.numberOfTrailingZeros(remaining), Set.of());
				int previous = gapCount(before);
				Set<Integer> after = new HashSet<>(before);
				for (int offset = 0; offset < duration; offset++) {
					after.add(slot.period() + offset);
				}
				maximum = Math.max(maximum, Math.max(0, gapCount(after) - previous));
			}
			return maximum;
		}

		private int gapCount(Set<Integer> periods) {
			if (periods.size() < 2) {
				return 0;
			}
			int minimum = periods.stream().mapToInt(Integer::intValue).min().orElse(0);
			int maximum = periods.stream().mapToInt(Integer::intValue).max().orElse(0);
			int gaps = 0;
			for (int period = minimum; period <= maximum; period++) {
				if (!periods.contains(period)) {
					gaps++;
				}
			}
			return gaps;
		}

		private String slot(int day, int period) {
			return day + "|" + period;
		}
	}
}
