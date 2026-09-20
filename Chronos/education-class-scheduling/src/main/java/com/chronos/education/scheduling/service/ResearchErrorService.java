package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.*;
import com.chronos.education.scheduling.model.*;
import com.chronos.education.scheduling.model.dto.ResearchErrorDtos.*;
import com.chronos.file.dao.ManagedFileRepository;
import com.chronos.file.service.ManagedFileService;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

/** 第四切片应用服务。所有关联均指向既有 IAM、教务和 platform-file 主数据。 */
@Service
@Transactional
public class ResearchErrorService {
	private final ResearchGroupRepository groups;
	private final ResearchGroupMemberRepository groupMembers;
	private final ResearchActivityRepository activities;
	private final ResearchActivityMemberRepository activityMembers;
	private final ResearchMaterialRepository materials;
	private final ResearchResultRepository results;
	private final ErrorBookRepository books;
	private final ErrorItemRepository items;
	private final EducationDataScopeService scopes;
	private final ManagedFileRepository files;
	private ManagedFileService managedFiles;
	private final TeachingReviewService reviews;
	private final QuestionRepository questions;
	private final KnowledgePointRepository points;
	private final QuestionKnowledgePointRepository questionPoints;
	private final QuestionBankRepository questionBanks;
	private QuestionVersionRepository questionVersions;
	private ErrorReviewRepository errorReviews;
	private EducationIdentityService identities;
	private TeachingCollaborationNotificationService notifications;

	public ResearchErrorService(ResearchGroupRepository groups, ResearchGroupMemberRepository groupMembers,
			ResearchActivityRepository activities, ResearchActivityMemberRepository activityMembers,
			ResearchMaterialRepository materials, ResearchResultRepository results, ErrorBookRepository books,
			ErrorItemRepository items, EducationDataScopeService scopes, ManagedFileRepository files,
			TeachingReviewService reviews, QuestionRepository questions, KnowledgePointRepository points,
			QuestionKnowledgePointRepository questionPoints, QuestionBankRepository questionBanks) {
		this.groups=groups; this.groupMembers=groupMembers; this.activities=activities;
		this.activityMembers=activityMembers; this.materials=materials; this.results=results;
		this.books=books; this.items=items; this.scopes=scopes; 		this.files=files; this.reviews=reviews; this.questions=questions; this.points=points;
		this.questionPoints=questionPoints;
		this.questionBanks=questionBanks;
		this.errorReviews = null;
		this.questionVersions = null;
	}

	@Autowired
	public ResearchErrorService(ResearchGroupRepository groups, ResearchGroupMemberRepository groupMembers,
			ResearchActivityRepository activities, ResearchActivityMemberRepository activityMembers,
			ResearchMaterialRepository materials, ResearchResultRepository results, ErrorBookRepository books,
			ErrorItemRepository items, EducationDataScopeService scopes, ManagedFileRepository files,
			TeachingReviewService reviews, QuestionRepository questions, KnowledgePointRepository points,
			QuestionKnowledgePointRepository questionPoints, QuestionBankRepository questionBanks,
			ErrorReviewRepository errorReviews, QuestionVersionRepository questionVersions,
			ManagedFileService managedFiles, EducationIdentityService identities,
			TeachingCollaborationNotificationService notifications) {
		this(groups, groupMembers, activities, activityMembers, materials, results, books, items, scopes, files,
				reviews, questions, points, questionPoints, questionBanks);
		this.errorReviews = errorReviews;
		this.questionVersions = questionVersions;
		this.managedFiles = managedFiles;
		this.identities = identities;
		this.notifications = notifications;
	}

	private void validateQuestionVersion(String questionId, String questionVersionId) {
		if (questionVersionId == null || questionVersionId.isBlank()) return;
		if (questionVersions == null)
			throw new IllegalStateException("题目版本校验服务未配置");
		QuestionVersion version = questionVersions.findById(questionVersionId)
				.orElseThrow(() -> new IllegalArgumentException("题目版本不存在"));
		if (questionId == null || !questionId.equals(version.getQuestionId())
				|| !"PUBLISHED".equals(version.getStatus()))
			throw new IllegalArgumentException("只能引用该题目的已发布版本");
	}

	private EducationDataScope scope(Authentication a) { return scopes.resolve(a.getName()); }
	private void teacher(Authentication a, String id) { scopes.assertTeacherAccess(scope(a), id); }
	private ResearchGroup group(String id, Authentication a) {
		ResearchGroup g=groups.findById(id).orElseThrow(()->new NoSuchElementException("教研组不存在"));
		EducationDataScope s=scope(a);
		if (!s.fullAccess() && !s.teacherIds().contains(g.getLeaderTeacherId())
				&& groupMembers.findByGroupId(id).stream().noneMatch(m->s.teacherIds().contains(m.getTeacherId())))
			throw new AccessDeniedException("无权访问该教研组");
		return g;
	}
	@Transactional(readOnly = true)
	public List<ResearchGroup> groups(Authentication a) {
		EducationDataScope s = scope(a);
		return groups.findAll().stream().filter(g -> s.fullAccess()
				|| s.teacherIds().contains(g.getLeaderTeacherId())
				|| groupMembers.findByGroupId(g.getId()).stream()
						.anyMatch(m -> s.teacherIds().contains(m.getTeacherId()))).toList();
	}
	@Transactional(readOnly = true)
	public List<ResearchActivity> activities(String groupId, Authentication a) {
		group(groupId, a);
		return activities.findAll().stream().filter(x -> groupId.equals(x.getGroupId()) && !x.isArchived()).toList();
	}
	@Transactional(readOnly = true)
	public List<ResearchResult> results(String activityId, Authentication a) {
		ResearchActivity x = activities.findById(activityId).orElseThrow(() -> new NoSuchElementException("活动不存在"));
		group(x.getGroupId(), a);
		return results.findAll().stream().filter(r -> activityId.equals(r.getActivityId())).toList();
	}
	@Transactional(readOnly = true)
	public List<ResearchGroupMember> groupMembers(String groupId, Authentication a) {
		group(groupId, a);
		return groupMembers.findByGroupId(groupId);
	}
	@Transactional(readOnly = true)
	public List<ResearchActivityMember> activityMembers(String activityId, Authentication a) {
		ResearchActivity x = activities.findById(activityId).orElseThrow(() -> new NoSuchElementException("活动不存在"));
		group(x.getGroupId(), a);
		return activityMembers.findByActivityId(activityId);
	}
	@Transactional(readOnly = true)
	public List<ResearchMaterial> materials(String activityId, Authentication a) {
		ResearchActivity x = activities.findById(activityId).orElseThrow(() -> new NoSuchElementException("活动不存在"));
		group(x.getGroupId(), a);
		return materials.findByActivityId(activityId);
	}
	public ResearchGroup updateGroup(String id, GroupRequest r, Authentication a) {
		ResearchGroup g = group(id, a);
		assertGroupLeader(g, a);
		teacher(a, r.leaderTeacherId());
		g.setName(r.name()); g.setSubjectId(r.subjectId()); g.setCampusId(r.campusId());
		g.setLeaderTeacherId(r.leaderTeacherId()); g.setCourseScopeJson(r.courseScopeJson());
		g.setDescription(r.description()); return groups.save(g);
	}
	public ResearchActivity updateActivity(String id, ActivityRequest r, Authentication a) {
		ResearchActivity x = activities.findById(id).orElseThrow(() -> new NoSuchElementException("活动不存在"));
		group(x.getGroupId(), a);
		assertActivityOrganizer(x, a);
		if (r.endTime() != null && r.activityTime() != null && r.endTime().isBefore(r.activityTime()))
			throw new IllegalArgumentException("结束时间不能早于开始时间");
		x.setTitle(r.title()); x.setActivityTime(r.activityTime()); x.setEndTime(r.endTime());
		x.setLocation(r.location()); x.setAgenda(r.agenda()); x.setCourseId(r.courseId()); x.setTopicId(r.topicId()); return activities.save(x);
	}
	public ResearchActivity cancelActivity(String id, ActivityCancelRequest request, Authentication a) {
		ResearchActivity x = activities.findById(id)
				.orElseThrow(() -> new NoSuchElementException("活动不存在"));
		group(x.getGroupId(), a);
		assertActivityOrganizer(x, a);
		if (!Set.of("SCHEDULED", "IN_PROGRESS").contains(x.getStatus()))
			throw new IllegalStateException("当前活动状态不能取消");
		if (request == null || request.reason() == null || request.reason().isBlank())
			throw new IllegalArgumentException("取消活动必须填写原因");
		x.setStatus("CANCELLED");
		x.setCancelReason(request.reason().trim());
		ResearchActivity saved = activities.save(x);
		if (notifications != null) {
			notifications.researchCancelled(
					saved,
					activityMembers.findByActivityId(id).stream()
							.map(ResearchActivityMember::getTeacherId)
							.toList());
		}
		return saved;
	}
	public ResearchResult updateResult(String id, ResultRequest r, Authentication a) {
		ResearchResult z = results.findById(id).orElseThrow(() -> new NoSuchElementException("成果不存在"));
		ResearchActivity x = activities.findById(z.getActivityId()).orElseThrow(() -> new NoSuchElementException("活动不存在"));
		group(x.getGroupId(), a);
		if (!"DRAFT".equals(z.getStatus())) throw new IllegalStateException("只有草稿成果可以编辑");
		if (r.fileId() != null) file(r.fileId(), a);
		z.setTitle(r.title()); z.setResultType(r.resultType()); z.setContent(r.content()); z.setFileId(r.fileId());
		return results.save(z);
	}
	private void file(String id, Authentication a) {
		if (id == null || id.isBlank() || id.contains("/") || id.contains("\\")) {
			throw new IllegalArgumentException("fileId无效");
		}
		var managedFile = files.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("文件不存在"));
		if (!a.getName().equals(managedFile.getOwnerUsername())) {
			throw new AccessDeniedException("只能绑定本人上传的文件");
		}
		if (!"ACTIVE".equals(managedFile.getStatus())
				|| !"PENDING_BIND".equals(managedFile.getBindState())) {
			throw new IllegalArgumentException("文件不可绑定");
		}
	}
	public ResearchGroup createGroup(GroupRequest r, Authentication a) {
		teacher(a,r.leaderTeacherId()); ResearchGroup g=new ResearchGroup();
		g.setName(r.name()); g.setSubjectId(r.subjectId()); g.setCampusId(r.campusId());
		g.setLeaderTeacherId(r.leaderTeacherId()); g.setCourseScopeJson(r.courseScopeJson()); g.setDescription(r.description());
		g.setSchoolId("CURRENT"); g.setCreateBy(a.getName()); return groups.save(g);
	}
	public ResearchGroupMember addMember(String id, MemberRequest r, Authentication a) {
		ResearchGroup current = group(id,a);assertGroupLeader(current,a);teacher(a,r.teacherId());
		if (groupMembers.existsByGroupIdAndTeacherId(id,r.teacherId())) throw new IllegalStateException("教师已在教研组");
		ResearchGroupMember m=new ResearchGroupMember(); m.setId(UUID.randomUUID().toString()); m.setGroupId(id);
		m.setTeacherId(r.teacherId()); m.setRole(r.role()); return groupMembers.save(m);
	}
	public void removeMember(String id, String teacherId, Authentication a) {
		ResearchGroup current = group(id, a);assertGroupLeader(current,a);
		if (!groupMembers.existsByGroupIdAndTeacherId(id, teacherId))
			throw new NoSuchElementException("教师不是教研组成员");
		groupMembers.deleteByGroupIdAndTeacherId(id, teacherId);
	}
	public ResearchActivity createActivity(String groupId, ActivityRequest r, Authentication a) {
		ResearchGroup current=group(groupId,a);assertGroupLeader(current,a);if (r.endTime()!=null && r.activityTime()!=null && r.endTime().isBefore(r.activityTime()))
			throw new IllegalArgumentException("结束时间不能早于开始时间");
		ResearchActivity x=new ResearchActivity(); x.setGroupId(groupId); x.setStatus("SCHEDULED");
		x.setTitle(r.title()); x.setActivityTime(r.activityTime()); x.setEndTime(r.endTime()); x.setLocation(r.location());
		x.setAgenda(r.agenda()); x.setCourseId(r.courseId()); x.setTopicId(r.topicId());
		x.setOrganizerId(currentTeacherId(a));x.setCreateBy(a.getName());return activities.save(x);
	}
	public ResearchActivityMember inviteActivityMember(String activityId, MemberRequest r, Authentication a) {
		ResearchActivity x=activities.findById(activityId).orElseThrow(()->new NoSuchElementException("活动不存在"));
		group(x.getGroupId(),a);assertActivityOrganizer(x,a);teacher(a,r.teacherId());
		if (activityMembers.findByActivityIdAndTeacherId(activityId,r.teacherId()).isPresent())
			throw new IllegalStateException("教师已在活动成员中");
		ResearchActivityMember m=new ResearchActivityMember(); m.setId(UUID.randomUUID().toString());
		m.setActivityId(activityId);m.setTeacherId(r.teacherId());m.setRole(r.role());
		ResearchActivityMember saved=activityMembers.save(m);
		if(notifications!=null)notifications.researchInvited(x,r.teacherId());
		return saved;
	}
	public void removeActivityMember(String activityId, String teacherId, Authentication a) {
		ResearchActivity x = activities.findById(activityId).orElseThrow(() -> new NoSuchElementException("活动不存在"));
		group(x.getGroupId(), a);assertActivityOrganizer(x,a);
		if (activityMembers.findByActivityIdAndTeacherId(activityId, teacherId).isEmpty())
			throw new NoSuchElementException("教师不是活动成员");
		activityMembers.deleteByActivityIdAndTeacherId(activityId, teacherId);
	}
	public ResearchActivityMember attendance(String activityId, AttendanceRequest r, Authentication a) {
		ResearchActivity x=activities.findById(activityId).orElseThrow(()->new NoSuchElementException("活动不存在"));
		group(x.getGroupId(),a); ResearchActivityMember m=activityMembers.findByActivityIdAndTeacherId(activityId,r.teacherId()).orElse(null);
		if (m==null) throw new IllegalArgumentException("活动成员不存在");
		if (!currentTeacherIds(a).contains(r.teacherId()) && !scope(a).fullAccess()) throw new AccessDeniedException("只能为本人签到或请假");
		if (!Set.of("SIGNED_IN","LEAVE","ABSENT").contains(r.status())) throw new IllegalArgumentException("签到状态无效");
		if ("LEAVE".equals(r.status()) && (r.leaveReason()==null || r.leaveReason().isBlank())) throw new IllegalArgumentException("请假必须填写原因");
		m.setAttendanceStatus(r.status()); m.setLeaveReason(r.leaveReason()); m.setRespondedAt(LocalDateTime.now());
		if ("SIGNED_IN".equals(r.status())) m.setAttendanceAt(LocalDateTime.now()); return activityMembers.save(m);
	}
	public ResearchActivity updateMinutes(String activityId, MinutesRequest r, Authentication a) {
		ResearchActivity x=activities.findById(activityId).orElseThrow();group(x.getGroupId(),a);assertActivityOrganizer(x,a);
		if (!Set.of("IN_PROGRESS","COMPLETED").contains(x.getStatus())) throw new IllegalStateException("活动开始后才能记录纪要");
		x.setMinutes(r.minutes()); x.setStatus("COMPLETED"); return activities.save(x);
	}
	public ResearchActivity transitionActivity(String activityId,String action,Authentication a){
		ResearchActivity x=activities.findById(activityId).orElseThrow(()->new NoSuchElementException("活动不存在"));
		group(x.getGroupId(),a);assertActivityOrganizer(x,a);
		if("start".equals(action)){
			if(!"SCHEDULED".equals(x.getStatus()))throw new IllegalStateException("只有已安排活动可以开始");
			x.setStatus("IN_PROGRESS");
		}else if("archive".equals(action)){
			if(!Set.of("COMPLETED","CANCELLED").contains(x.getStatus()))throw new IllegalStateException("只有已完成或已取消活动可以归档");
			x.setStatus("ARCHIVED");x.setArchived(true);
		}else throw new IllegalArgumentException("不支持的活动操作");
		return activities.save(x);
	}
	public ResearchMaterial addMaterial(String activityId, MaterialRequest r, Authentication a) {
		ResearchActivity x=activities.findById(activityId).orElseThrow(); group(x.getGroupId(),a); file(r.fileId(),a);
		ResearchMaterial m=new ResearchMaterial(); m.setId(UUID.randomUUID().toString()); m.setActivityId(activityId);
		m.setTitle(r.title()); m.setFileId(r.fileId());
		ResearchMaterial saved = materials.save(m);
		bindFile(r.fileId(), "EDUCATION_RESEARCH_MATERIAL", saved.getId(), a);
		return saved;
	}
	public ResearchResult addResult(String activityId, ResultRequest r, Authentication a) {
		ResearchActivity x=activities.findById(activityId).orElseThrow(); group(x.getGroupId(),a);
		if (r.fileId()!=null) file(r.fileId(),a); ResearchResult z=new ResearchResult(); z.setId(UUID.randomUUID().toString());
		z.setActivityId(activityId); z.setTitle(r.title()); z.setResultType(r.resultType()); z.setContent(r.content()); z.setFileId(r.fileId());
		ResearchResult saved = results.save(z);
		if (r.fileId() != null && !r.fileId().isBlank()) {
			bindFile(r.fileId(), "EDUCATION_RESEARCH_RESULT", saved.getId(), a);
		}
		return saved;
	}

	/**
	 * 业务数据先落库，再由文件中心将本人草稿绑定到受权访问的业务记录。
	 * 整个服务位于同一事务，绑定失败时研讨资料/成果也会回滚。
	 */
	private void bindFile(
			String fileId,
			String businessType,
			String businessId,
			Authentication authentication) {
		if (managedFiles == null) {
			throw new IllegalStateException("文件绑定服务未配置");
		}
		managedFiles.bind(
				List.of(fileId),
				businessType,
				businessId,
				authentication.getName());
	}

	private Set<String> currentTeacherIds(Authentication authentication) {
		return identities == null
				? Set.of(authentication.getName())
				: identities.teacherIds(authentication.getName());
	}

	private String currentTeacherId(Authentication authentication) {
		if (identities == null) {
			throw new IllegalStateException("教师身份绑定服务未配置");
		}
		Set<String> teacherIds = identities.teacherIds(authentication.getName());
		if (teacherIds == null || teacherIds.isEmpty()) {
			throw new AccessDeniedException("当前账号未绑定教师档案");
		}
		return teacherIds.stream()
				.findFirst()
				.orElseThrow(() -> new AccessDeniedException("当前账号未绑定教师档案"));
	}

	private void assertGroupLeader(ResearchGroup group, Authentication authentication) {
		if (!scope(authentication).fullAccess()
				&& !currentTeacherIds(authentication).contains(group.getLeaderTeacherId())) {
			throw new AccessDeniedException("只有教研组长可以执行该操作");
		}
	}

	private void assertActivityOrganizer(
			ResearchActivity activity,
			Authentication authentication) {
		if (!scope(authentication).fullAccess()
				&& !authentication.getName().equals(activity.getCreateBy())
				&& !authentication.getName().equals(activity.getOrganizerId())
				&& !currentTeacherIds(authentication).contains(activity.getOrganizerId())) {
			throw new AccessDeniedException("只有活动主持人可以执行该操作");
		}
	}
	public ResearchResult submitResult(
			String id,
			String idempotencyKey,
			Authentication authentication) {
		ResearchResult result = results.findById(id)
				.orElseThrow(() -> new NoSuchElementException("成果不存在"));
		ResearchActivity activity = activities.findById(result.getActivityId())
				.orElseThrow(() -> new NoSuchElementException("活动不存在"));
		group(activity.getGroupId(), authentication);
		assertActivityOrganizer(activity, authentication);

		// 网络重试必须返回第一次提交的审核记录，不能重复启动工作流实例。
		var existing = reviews.findIdempotent(
				"RESEARCH_RESULT",
				id,
				idempotencyKey,
				authentication);
		if (existing != null) {
			result.setReviewRecordId(existing.getId());
			result.setStatus("SUBMITTED");
			return results.save(result);
		}
		if (!Set.of("DRAFT", "REJECTED").contains(result.getStatus())) {
			throw new IllegalStateException("只有草稿或驳回成果可以提交审核");
		}
		Map<String, Object> form = new java.util.LinkedHashMap<>();
		form.put("resultType", Objects.toString(result.getResultType(), ""));
		if (idempotencyKey != null && !idempotencyKey.isBlank()) {
			form.put("idempotencyKey", idempotencyKey);
		}
		var review = reviews.submit(
				"RESEARCH_RESULT",
				id,
				null,
				form,
				authentication);
		result.setReviewRecordId(review.getId());
		result.setStatus("SUBMITTED");
		return results.save(result);
	}
	public ResearchResult transitionResult(String id, String status, Authentication a) {
		ResearchResult z = results.findById(id).orElseThrow(() -> new NoSuchElementException("成果不存在"));
		ResearchActivity x = activities.findById(z.getActivityId()).orElseThrow(() -> new NoSuchElementException("活动不存在"));
		group(x.getGroupId(), a);
		if ("ARCHIVED".equals(status)) {
			if (!"PUBLISHED".equals(z.getStatus())) throw new IllegalStateException("只有已发布成果可以归档");
			z.setStatus("ARCHIVED");
		} else if ("PUBLISHED".equals(status)) {
			if (!"PUBLISHED".equals(z.getStatus())) throw new IllegalStateException("成果须审核通过后才能发布");
		} else {
			throw new IllegalArgumentException("不支持的成果状态");
		}
		return results.save(z);
	}
	private ErrorBook book(String studentId,String courseId,String semesterId,Authentication a) {
		scopes.assertStudentAccess(scope(a),studentId);
		return books.findAll().stream().filter(b->studentId.equals(b.getStudentId()) && Objects.equals(courseId,b.getCourseId())
				&& Objects.equals(semesterId,b.getSemesterId()) && !b.isArchived()).findFirst().orElseGet(()->{
					ErrorBook b=new ErrorBook(); b.setStudentId(studentId); b.setName("错题沉淀");
					b.setCourseId(courseId); b.setSemesterId(semesterId); b.setCreateBy(a.getName()); return books.save(b);});
	}
	public ErrorItem recordManual(ErrorManualRequest r, Authentication a) {
		if (!Set.of("MANUAL", "STUDENT_SELF").contains(r.sourceType()))
			throw new IllegalArgumentException("手工错题来源无效");
		validateQuestionVersion(r.questionId(), r.questionVersionId());
		if (r.questionId() != null) {
			Question q = questions.findById(r.questionId()).orElseThrow(() -> new IllegalArgumentException("题目不存在"));
			QuestionBank bank = questionBanks.findById(q.getBankId()).orElseThrow(() -> new IllegalArgumentException("题库不存在"));
			if (r.courseId() != null && !r.courseId().equals(bank.getCourseId()))
				throw new IllegalArgumentException("题目不属于所选课程");
			if (r.knowledgePointId() != null && questionPoints.findByQuestionId(r.questionId()).stream()
					.noneMatch(link -> r.knowledgePointId().equals(link.getKnowledgePointId())))
				throw new IllegalArgumentException("知识点未关联该题目");
		}
		if (r.knowledgePointId() != null)
			points.findById(r.knowledgePointId()).orElseThrow(() -> new IllegalArgumentException("知识点不存在"));
		ErrorBook b=book(r.studentId(),r.courseId(),r.semesterId(),a); ErrorItem i=new ErrorItem(); i.setId(UUID.randomUUID().toString());
		i.setBookId(b.getId()); i.setQuestionId(r.questionId()); i.setQuestionVersionId(r.questionVersionId()); i.setKnowledgePointId(r.knowledgePointId());
		i.setErrorReason(r.errorReason()); i.setSourceRef(r.sourceRef()); i.setSourceType(r.sourceType());
		i.setSourceItemId(r.sourceItemId());
		i.setAnalysis(r.analysis()); i.setStudentNote(r.studentNote()); i.setCreateTime(LocalDateTime.now());
		i.setOccurredAt(r.occurredAt() == null ? i.getCreateTime() : r.occurredAt());
		i.setLastWrongAt(i.getOccurredAt()); return items.save(i);
	}
	public ErrorItem onWrongAnswerConfirmed(WrongAnswerConfirmed r, Authentication a) {
		return recordConfirmedWrongAnswer(r, a, true);
	}

	/**
	 * 仅供已完成自身数据范围校验的作业、考试领域服务调用。
	 * 生产者已校验教学班或考试管理权限，这里不再要求操作者直接拥有学生行政班范围，
	 * 否则任课教师可能能批改本教学班作业，却无法为同一学生沉淀错题。
	 */
	ErrorItem onTrustedWrongAnswerConfirmed(WrongAnswerConfirmed r, Authentication a) {
		return recordConfirmedWrongAnswer(r, a, false);
	}

	private ErrorItem recordConfirmedWrongAnswer(
			WrongAnswerConfirmed r,
			Authentication a,
			boolean enforceStudentScope) {
		// 此入口只接收作业、考试模块产生的可信事件；人工录入必须走 manual 接口。
		if (!Set.of("HOMEWORK", "EXAM").contains(r.sourceType())) {
			throw new IllegalArgumentException("错题确认事件来源只能是作业或考试");
		}
		validateQuestionVersion(r.questionId(), r.questionVersionId());
		var replay=items.findByEventId(r.eventId()); if (replay.isPresent()) return replay.get();
		ErrorBook b = enforceStudentScope
				? book(r.studentId(), r.courseId(), r.semesterId(), a)
				: trustedBook(r.studentId(), r.courseId(), r.semesterId(), a);
		ErrorItem i=items.findByBookIdAndSourceTypeAndSourceItemId(b.getId(),r.sourceType(),r.sourceItemId()).orElse(null);
		// sourceItemId is the producer's idempotency key. Replayed delivery must
		// not inflate the student's error count.
		if (i!=null) return i;
		i=new ErrorItem(); i.setId(UUID.randomUUID().toString()); i.setBookId(b.getId()); i.setQuestionId(r.questionId());
		i.setQuestionVersionId(r.questionVersionId());
		i.setSourceRef(r.sourceRef()); i.setSourceType(r.sourceType()); i.setSourceItemId(r.sourceItemId()); i.setEventId(r.eventId());
		i.setAnalysis(r.analysis()); i.setCreateTime(LocalDateTime.now());
		i.setOccurredAt(r.occurredAt() == null ? i.getCreateTime() : r.occurredAt());
		i.setLastWrongAt(i.getOccurredAt()); return items.save(i);
	}

	private ErrorBook trustedBook(
			String studentId,
			String courseId,
			String semesterId,
			Authentication authentication) {
		return books.findAll().stream()
				.filter(book -> studentId.equals(book.getStudentId())
						&& Objects.equals(courseId, book.getCourseId())
						&& Objects.equals(semesterId, book.getSemesterId())
						&& !book.isArchived())
				.findFirst()
				.orElseGet(() -> {
					ErrorBook book = new ErrorBook();
					book.setStudentId(studentId);
					book.setName("错题沉淀");
					book.setCourseId(courseId);
					book.setSemesterId(semesterId);
					book.setCreateBy(authentication.getName());
					return books.save(book);
				});
	}
	public ErrorItem mastery(String id, MasteryRequest r, Authentication a) {
		ErrorItem i=items.findById(id).orElseThrow(); ErrorBook b=books.findById(i.getBookId()).orElseThrow();
		scopes.assertStudentAccess(scope(a),b.getStudentId()); if (!Set.of("MASTERED","NEEDS_PRACTICE").contains(r.status()))
			throw new IllegalArgumentException("掌握状态无效"); i.setMasteryStatus(r.status()); i.setStudentNote(r.note()); return items.save(i);
	}

	public ErrorItem review(String id, ErrorReviewRequest r, Authentication a) {
			ErrorItem item = items.findById(id).orElseThrow(() -> new NoSuchElementException("错题不存在"));
			ErrorBook book = books.findById(item.getBookId()).orElseThrow(() -> new NoSuchElementException("错题本不存在"));
			scopes.assertStudentAccess(scope(a), book.getStudentId());
			if (!Set.of("OPEN", "RESOLVED", "MASTERED", "NEEDS_PRACTICE").contains(r.status()))
				throw new IllegalArgumentException("复习状态无效");
			item.setStatus(r.status());
			item.setTeacherNote(r.note());
			ErrorItem saved = items.save(item);
			if (errorReviews != null) {
				ErrorReview review = new ErrorReview();
				review.setId(UUID.randomUUID().toString()); review.setErrorItemId(id);
				review.setReviewerId(a.getName()); review.setStatus(r.status());
				review.setNote(r.note()); review.setReviewedAt(LocalDateTime.now());
				errorReviews.save(review);
			}
			return saved;
	}

	@Transactional(readOnly = true)
	public List<ErrorItem> items(String courseId, Authentication a) {
			EducationDataScope current = scope(a);
			if (!current.fullAccess() && !current.teacherIds().isEmpty() && current.studentIds().isEmpty()) {
				throw new AccessDeniedException("教师只能查看错题聚合统计");
			}
			Set<String> visibleBooks = books.findAll().stream()
					.filter(book -> !book.isArchived() && (courseId == null || courseId.equals(book.getCourseId())))
					.filter(book -> scopes.canAccessStudent(current, book.getStudentId()))
					.map(ErrorBook::getId).collect(java.util.stream.Collectors.toSet());
			return items.findAll().stream().filter(item -> visibleBooks.contains(item.getBookId())).toList();
	}
	public List<TeacherErrorAggregate> teacherAggregation(String courseId, Authentication a) {
		List<ErrorItem> visible = items(courseId, a);
		Map<String, List<ErrorItem>> grouped = visible.stream().collect(java.util.stream.Collectors.groupingBy(
				x -> String.valueOf(x.getQuestionId()) + "\u0000" + String.valueOf(x.getKnowledgePointId())));
		return grouped.values().stream().map(values -> new TeacherErrorAggregate(values.get(0).getQuestionId(),
				values.get(0).getKnowledgePointId(),
				values.stream().map(x -> books.findById(x.getBookId()).map(ErrorBook::getStudentId).orElse(""))
						.filter(s -> !s.isBlank()).distinct().count(),
				values.stream().mapToLong(ErrorItem::getWrongCount).sum(),
				values.stream().map(ErrorItem::getLastWrongAt).filter(Objects::nonNull)
						.max(LocalDateTime::compareTo).orElse(null))).toList();
	}
}
