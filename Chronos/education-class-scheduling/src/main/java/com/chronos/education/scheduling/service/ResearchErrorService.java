package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.*;
import com.chronos.education.scheduling.model.*;
import com.chronos.education.scheduling.model.dto.ResearchErrorDtos.*;
import com.chronos.file.dao.ManagedFileRepository;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
	private final TeachingReviewService reviews;

	public ResearchErrorService(ResearchGroupRepository groups, ResearchGroupMemberRepository groupMembers,
			ResearchActivityRepository activities, ResearchActivityMemberRepository activityMembers,
			ResearchMaterialRepository materials, ResearchResultRepository results, ErrorBookRepository books,
			ErrorItemRepository items, EducationDataScopeService scopes, ManagedFileRepository files,
			TeachingReviewService reviews) {
		this.groups=groups; this.groupMembers=groupMembers; this.activities=activities;
		this.activityMembers=activityMembers; this.materials=materials; this.results=results;
		this.books=books; this.items=items; this.scopes=scopes; this.files=files; this.reviews=reviews;
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
	private void file(String id, Authentication a) {
		if (id==null || id.isBlank() || id.contains("/") || id.contains("\\")) throw new IllegalArgumentException("fileId无效");
		var f=files.findById(id).orElseThrow(()->new IllegalArgumentException("文件不存在"));
		if (!a.getName().equals(f.getOwnerUsername()) && !Set.of("ACTIVE","PENDING_BIND").contains(f.getStatus()))
			throw new IllegalArgumentException("文件不可绑定");
	}
	public ResearchGroup createGroup(GroupRequest r, Authentication a) {
		teacher(a,r.leaderTeacherId()); ResearchGroup g=new ResearchGroup(); g.setId(UUID.randomUUID().toString());
		g.setName(r.name()); g.setSubjectId(r.subjectId()); g.setCampusId(r.campusId());
		g.setLeaderTeacherId(r.leaderTeacherId()); g.setCourseScopeJson(r.courseScopeJson()); g.setDescription(r.description());
		g.setSchoolId("CURRENT"); g.setCreateBy(a.getName()); return groups.save(g);
	}
	public ResearchGroupMember addMember(String id, MemberRequest r, Authentication a) {
		group(id,a); teacher(a,r.teacherId());
		if (groupMembers.existsByGroupIdAndTeacherId(id,r.teacherId())) throw new IllegalStateException("教师已在教研组");
		ResearchGroupMember m=new ResearchGroupMember(); m.setId(UUID.randomUUID().toString()); m.setGroupId(id);
		m.setTeacherId(r.teacherId()); m.setRole(r.role()); return groupMembers.save(m);
	}
	public ResearchActivity createActivity(String groupId, ActivityRequest r, Authentication a) {
		group(groupId,a); if (r.endTime()!=null && r.activityTime()!=null && r.endTime().isBefore(r.activityTime()))
			throw new IllegalArgumentException("结束时间不能早于开始时间");
		ResearchActivity x=new ResearchActivity(); x.setId(UUID.randomUUID().toString()); x.setGroupId(groupId);
		x.setTitle(r.title()); x.setActivityTime(r.activityTime()); x.setEndTime(r.endTime()); x.setLocation(r.location());
		x.setAgenda(r.agenda()); x.setOrganizerId(a.getName()); x.setCreateBy(a.getName()); return activities.save(x);
	}
	public ResearchActivityMember inviteActivityMember(String activityId, MemberRequest r, Authentication a) {
		ResearchActivity x=activities.findById(activityId).orElseThrow(()->new NoSuchElementException("活动不存在"));
		group(x.getGroupId(),a); teacher(a,r.teacherId());
		if (activityMembers.findByActivityIdAndTeacherId(activityId,r.teacherId()).isPresent())
			throw new IllegalStateException("教师已在活动成员中");
		ResearchActivityMember m=new ResearchActivityMember(); m.setId(UUID.randomUUID().toString());
		m.setActivityId(activityId); m.setTeacherId(r.teacherId()); m.setRole(r.role()); return activityMembers.save(m);
	}
	public ResearchActivityMember attendance(String activityId, AttendanceRequest r, Authentication a) {
		ResearchActivity x=activities.findById(activityId).orElseThrow(()->new NoSuchElementException("活动不存在"));
		group(x.getGroupId(),a); ResearchActivityMember m=activityMembers.findByActivityIdAndTeacherId(activityId,r.teacherId()).orElse(null);
		if (m==null) throw new IllegalArgumentException("活动成员不存在");
		if (!Set.of("SIGNED_IN","LEAVE","ABSENT").contains(r.status())) throw new IllegalArgumentException("签到状态无效");
		m.setAttendanceStatus(r.status()); m.setLeaveReason(r.leaveReason()); m.setRespondedAt(LocalDateTime.now());
		if ("SIGNED_IN".equals(r.status())) m.setAttendanceAt(LocalDateTime.now()); return activityMembers.save(m);
	}
	public ResearchMaterial addMaterial(String activityId, MaterialRequest r, Authentication a) {
		ResearchActivity x=activities.findById(activityId).orElseThrow(); group(x.getGroupId(),a); file(r.fileId(),a);
		ResearchMaterial m=new ResearchMaterial(); m.setId(UUID.randomUUID().toString()); m.setActivityId(activityId);
		m.setTitle(r.title()); m.setFileId(r.fileId()); return materials.save(m);
	}
	public ResearchResult addResult(String activityId, ResultRequest r, Authentication a) {
		ResearchActivity x=activities.findById(activityId).orElseThrow(); group(x.getGroupId(),a);
		if (r.fileId()!=null) file(r.fileId(),a); ResearchResult z=new ResearchResult(); z.setId(UUID.randomUUID().toString());
		z.setActivityId(activityId); z.setTitle(r.title()); z.setResultType(r.resultType()); z.setContent(r.content()); z.setFileId(r.fileId());
		return results.save(z);
	}
	public ResearchResult submitResult(String id, Authentication a) {
		ResearchResult z=results.findById(id).orElseThrow(); ResearchActivity x=activities.findById(z.getActivityId()).orElseThrow();
		group(x.getGroupId(),a); var review=reviews.submit("RESEARCH_RESULT",id,null,Map.of("resultType",z.getResultType()),a);
		z.setReviewRecordId(review.getId()); z.setStatus("SUBMITTED"); return results.save(z);
	}
	private ErrorBook book(String studentId,String courseId,String semesterId,Authentication a) {
		scopes.assertStudentAccess(scope(a),studentId);
		return books.findAll().stream().filter(b->studentId.equals(b.getStudentId()) && Objects.equals(courseId,b.getCourseId())
				&& Objects.equals(semesterId,b.getSemesterId()) && !b.isArchived()).findFirst().orElseGet(()->{
					ErrorBook b=new ErrorBook(); b.setId(UUID.randomUUID().toString()); b.setStudentId(studentId); b.setName("错题沉淀");
					b.setCourseId(courseId); b.setSemesterId(semesterId); b.setCreateBy(a.getName()); return books.save(b);});
	}
	public ErrorItem recordManual(ErrorManualRequest r, Authentication a) {
		ErrorBook b=book(r.studentId(),r.courseId(),r.semesterId(),a); ErrorItem i=new ErrorItem(); i.setId(UUID.randomUUID().toString());
		i.setBookId(b.getId()); i.setQuestionId(r.questionId()); i.setSourceRef(r.sourceRef()); i.setSourceType("MANUAL");
		i.setAnalysis(r.analysis()); i.setStudentNote(r.studentNote()); i.setCreateTime(LocalDateTime.now()); return items.save(i);
	}
	public ErrorItem onWrongAnswerConfirmed(WrongAnswerConfirmed r, Authentication a) {
		ErrorBook b=book(r.studentId(),r.courseId(),r.semesterId(),a);
		ErrorItem i=items.findByBookIdAndSourceTypeAndSourceItemId(b.getId(),"WRONG_ANSWER_CONFIRMED",r.sourceItemId()).orElse(null);
		if (i!=null) { i.setWrongCount(i.getWrongCount()+1); i.setLastWrongAt(LocalDateTime.now()); return items.save(i); }
		i=new ErrorItem(); i.setId(UUID.randomUUID().toString()); i.setBookId(b.getId()); i.setQuestionId(r.questionId());
		i.setSourceRef(r.sourceRef()); i.setSourceType("WRONG_ANSWER_CONFIRMED"); i.setSourceItemId(r.sourceItemId());
		i.setAnalysis(r.analysis()); i.setLastWrongAt(LocalDateTime.now()); i.setCreateTime(LocalDateTime.now()); return items.save(i);
	}
	public ErrorItem mastery(String id, MasteryRequest r, Authentication a) {
		ErrorItem i=items.findById(id).orElseThrow(); ErrorBook b=books.findById(i.getBookId()).orElseThrow();
		scopes.assertStudentAccess(scope(a),b.getStudentId()); if (!Set.of("MASTERED","NEEDS_PRACTICE").contains(r.status()))
			throw new IllegalArgumentException("掌握状态无效"); i.setMasteryStatus(r.status()); i.setStudentNote(r.note()); return items.save(i);
	}
}
