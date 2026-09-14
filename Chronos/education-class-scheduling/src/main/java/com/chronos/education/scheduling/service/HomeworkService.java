package com.chronos.education.scheduling.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.commons.model.PageView;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.HomeworkAssignmentRepository;
import com.chronos.education.scheduling.dao.HomeworkSubmissionRepository;
import com.chronos.education.scheduling.dao.TeachingClassMemberRepository;
import com.chronos.education.scheduling.dao.TeachingPlanItemRepository;
import com.chronos.education.scheduling.dao.TeachingPlanRepository;
import com.chronos.education.scheduling.dao.PreparationRepository;
import com.chronos.education.scheduling.dao.LessonPlanRepository;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.education.scheduling.model.HomeworkAssignment;
import com.chronos.education.scheduling.model.HomeworkSubmission;
import com.chronos.education.scheduling.model.TeachingClassMember;
import com.chronos.education.scheduling.model.TeachingPlanItem;
import com.chronos.education.scheduling.model.dto.HomeworkDtos.AssignmentRequest;
import com.chronos.education.scheduling.model.dto.HomeworkDtos.GradeRequest;
import com.chronos.education.scheduling.model.dto.HomeworkDtos.SubmissionRequest;

@Service
@Transactional
public class HomeworkService {
	private final HomeworkAssignmentRepository assignments;
	private final HomeworkSubmissionRepository submissions;
	private final TeachingClassMemberRepository members;
	private final CourseOfferingRepository offerings;
	private final EducationDataScopeService scopes;
	private final TeachingPlanItemRepository planItems;
	private final TeachingPlanRepository plans;
	private final PreparationRepository preparations;
	private final LessonPlanRepository lessonPlans;

	public HomeworkService(HomeworkAssignmentRepository assignments,
			HomeworkSubmissionRepository submissions, TeachingClassMemberRepository members,
			CourseOfferingRepository offerings, EducationDataScopeService scopes,
			TeachingPlanItemRepository planItems, TeachingPlanRepository plans,
			PreparationRepository preparations, LessonPlanRepository lessonPlans) {
		this.assignments = assignments;
		this.submissions = submissions;
		this.members = members;
		this.offerings = offerings;
		this.scopes = scopes;
		this.planItems = planItems; this.plans = plans;
		this.preparations = preparations; this.lessonPlans = lessonPlans;
	}

	private EducationDataScope scope(Authentication auth) {
		return scopes.resolve(auth.getName());
	}

	private HomeworkAssignment assignment(String id) {
		return assignments.findById(id).orElseThrow(() -> new IllegalArgumentException("作业不存在"));
	}

	private CourseOffering offering(String id) {
		return offerings.findById(id).orElseThrow(() -> new IllegalArgumentException("教学任务不存在"));
	}

	private void teacherCan(EducationDataScope scope, HomeworkAssignment assignment) {
		scopes.assertOfferingAccess(scope, assignment.getOfferingId());
	}

	private String studentId(Authentication auth) {
		EducationDataScope value = scope(auth);
		if (value.studentIds().size() != 1) {
			throw new AccessDeniedException("当前账号未绑定唯一学生");
		}
		return value.studentIds().iterator().next();
	}

	private void studentCan(EducationDataScope scope, HomeworkAssignment assignment, String studentId) {
		if (!"PUBLISHED".equals(assignment.getStatus())) {
			throw new AccessDeniedException("作业尚未发布");
		}
		boolean enrolled = members.findByOfferingIdAndStudentId(assignment.getOfferingId(), studentId)
				.map(member -> "ACTIVE".equals(member.getEnrollmentStatus())
						|| "ENROLLED".equals(member.getEnrollmentStatus()))
				.orElse(false);
		if (!enrolled || (!scope.fullAccess() && !scope.studentIds().contains(studentId))) {
			throw new AccessDeniedException("无权访问该作业");
		}
	}

	@Transactional(readOnly = true)
	public PageView<HomeworkAssignment> pageAssignments(int page, int size, String offeringId,
			Authentication auth) {
		EducationDataScope value = scope(auth);
		List<HomeworkAssignment> visible;
		if (offeringId != null && !offeringId.isBlank()) {
			HomeworkAssignment dummy = new HomeworkAssignment();
			dummy.setOfferingId(offeringId);
			if (value.studentIds().isEmpty()) {
				teacherCan(value, dummy);
				visible = assignments.findByOfferingIdOrderByDueAtDescCreateTimeDesc(offeringId);
			} else if (value.fullAccess() || value.teacherIds().isEmpty()) {
				List<TeachingClassMember> enrolled = members.findByStudentIdAndEnrollmentStatusIn(
						studentId(auth), List.of("ACTIVE", "ENROLLED"));
				if (enrolled.stream().noneMatch(m -> offeringId.equals(m.getOfferingId()))) {
					throw new AccessDeniedException("无权访问该教学班");
				}
				visible = assignments.findByOfferingIdInAndStatusOrderByDueAtDescCreateTimeDesc(
						List.of(offeringId), "PUBLISHED");
			} else {
				teacherCan(value, dummy);
				visible = assignments.findByOfferingIdOrderByDueAtDescCreateTimeDesc(offeringId);
			}
		} else if (!value.studentIds().isEmpty() && value.teacherIds().isEmpty()) {
			List<String> ids = members.findByStudentIdAndEnrollmentStatusIn(
					studentId(auth), List.of("ACTIVE", "ENROLLED"))
					.stream().map(TeachingClassMember::getOfferingId).toList();
			visible = ids.isEmpty() ? List.of()
					: assignments.findByOfferingIdInAndStatusOrderByDueAtDescCreateTimeDesc(ids, "PUBLISHED");
		} else {
			visible = assignments.findAll().stream()
					.filter(a -> value.fullAccess() || canTeacher(value, a)).toList();
		}
		return PageView.from(visible, page, size);
	}

	private boolean canTeacher(EducationDataScope scope, HomeworkAssignment value) {
		return scope.fullAccess() || offerings.findById(value.getOfferingId())
				.map(o -> scopes.canAccessOffering(scope, o)).orElse(false);
	}

	public HomeworkAssignment create(AssignmentRequest request, Authentication auth) {
		EducationDataScope value = scope(auth);
		scopes.assertOfferingAccess(value, request.offeringId());
		offering(request.offeringId());
		validateReferences(request, request.offeringId());
		HomeworkAssignment result = new HomeworkAssignment();
		apply(result, request);
		return assignments.save(result);
	}

	public HomeworkAssignment update(String id, AssignmentRequest request, Authentication auth) {
		HomeworkAssignment result = assignment(id);
		teacherCan(scope(auth), result);
		if (!"DRAFT".equals(result.getStatus())) throw new IllegalStateException("只有草稿可以修改");
		if (!Objects.equals(result.getOfferingId(), request.offeringId())) {
			scopes.assertOfferingAccess(scope(auth), request.offeringId());
		}
		validateReferences(request, request.offeringId());
		apply(result, request);
		return result;
	}

	private void apply(HomeworkAssignment result, AssignmentRequest request) {
		result.setOfferingId(request.offeringId());
		result.setTeachingPlanItemId(request.teachingPlanItemId());
		result.setPreparationId(request.preparationId());
		result.setLessonPlanId(request.lessonPlanId());
		result.setTitle(request.title());
		result.setQuestionSnapshotJson(request.questionSnapshotJson());
		result.setInstructionsJson(request.instructionsJson());
		result.setDueAt(request.dueAt());
		result.setMaxScore(request.maxScore() == null ? 100 : request.maxScore());
		result.setAllowLate(request.allowLate());
	}

	private void validateReferences(AssignmentRequest request, String offeringId) {
		if (request.teachingPlanItemId() != null && !request.teachingPlanItemId().isBlank()) {
			TeachingPlanItem item = planItems.findById(request.teachingPlanItemId())
					.orElseThrow(() -> new IllegalArgumentException("教学计划项不存在"));
			if (!plans.findById(item.getPlanId()).map(p -> offeringId.equals(p.getOfferingId())).orElse(false))
				throw new IllegalArgumentException("教学计划项不属于该教学班");
		}
		if (request.preparationId() != null && !request.preparationId().isBlank()
				&& !preparations.findById(request.preparationId())
						.map(p -> offeringId.equals(p.getOfferingId())).orElse(false))
			throw new IllegalArgumentException("备课记录不属于该教学班");
		if (request.lessonPlanId() != null && !request.lessonPlanId().isBlank()
				&& !lessonPlans.findById(request.lessonPlanId())
						.map(p -> offeringId.equals(p.getOfferingId())).orElse(false))
			throw new IllegalArgumentException("教案不属于该教学班");
	}

	public HomeworkAssignment publish(String id, Authentication auth) {
		HomeworkAssignment result = assignment(id);
		teacherCan(scope(auth), result);
		if (!"DRAFT".equals(result.getStatus())) throw new IllegalStateException("只有草稿可以发布");
		result.setStatus("PUBLISHED");
		return result;
	}

	public HomeworkAssignment close(String id, Authentication auth) {
		HomeworkAssignment result = assignment(id);
		teacherCan(scope(auth), result);
		if (!"PUBLISHED".equals(result.getStatus())) throw new IllegalStateException("只有已发布作业可以关闭");
		result.setStatus("CLOSED");
		return result;
	}

	@Transactional(readOnly = true)
	public HomeworkAssignment get(String id, Authentication auth) {
		HomeworkAssignment result = assignment(id);
		EducationDataScope value = scope(auth);
		if (value.studentIds().isEmpty() || !value.teacherIds().isEmpty() || value.fullAccess()) {
			teacherCan(value, result);
		} else {
			studentCan(value, result, studentId(auth));
		}
		return result;
	}

	public HomeworkSubmission saveDraft(String assignmentId, SubmissionRequest request,
			Authentication auth) {
		HomeworkAssignment assignment = assignment(assignmentId);
		String student = studentId(auth);
		studentCan(scope(auth), assignment, student);
		HomeworkSubmission result = submissions.findByAssignmentIdAndStudentId(assignmentId, student)
				.orElseGet(HomeworkSubmission::new);
		if (result.getId() != null && !"DRAFT".equals(result.getStatus())
				&& !"RETURNED_FOR_REVISION".equals(result.getStatus())) {
			throw new IllegalStateException("当前提交不能修改");
		}
		result.setAssignmentId(assignmentId);
		result.setStudentId(student);
		result.setAnswerSnapshotJson(request.answerSnapshotJson());
		result.setStatus("DRAFT");
		return submissions.save(result);
	}

	public HomeworkSubmission updateDraft(String id, SubmissionRequest request, Authentication auth) {
		HomeworkSubmission current = submissions.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("提交不存在"));
		return saveDraft(current.getAssignmentId(), request, auth);
	}

	public HomeworkSubmission submit(String id, Authentication auth) {
		HomeworkSubmission result = submissions.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("提交不存在"));
		HomeworkAssignment assignment = assignment(result.getAssignmentId());
		String student = studentId(auth);
		if (!student.equals(result.getStudentId())) throw new AccessDeniedException("无权提交该答案");
		studentCan(scope(auth), assignment, student);
		if (!assignment.isAllowLate() && assignment.getDueAt() != null
				&& LocalDateTime.now().isAfter(assignment.getDueAt())) {
			throw new IllegalStateException("作业已超过截止时间");
		}
		if (!"DRAFT".equals(result.getStatus()) && !"RETURNED_FOR_REVISION".equals(result.getStatus()))
			throw new IllegalStateException("当前提交不能提交");
		result.setStatus("SUBMITTED");
		result.setSubmittedAt(LocalDateTime.now());
		return result;
	}

	@Transactional(readOnly = true)
	public PageView<HomeworkSubmission> submissions(String assignmentId, int page, int size,
			Authentication auth) {
		HomeworkAssignment assignment = assignment(assignmentId);
		teacherCan(scope(auth), assignment);
		return PageView.from(submissions.findByAssignmentIdOrderByCreateTimeAsc(assignmentId), page, size);
	}

	@Transactional(readOnly = true)
	public HomeworkSubmission mySubmission(String assignmentId, Authentication auth) {
		HomeworkAssignment homework = assignment(assignmentId);
		String student = studentId(auth);
		studentCan(scope(auth), homework, student);
		return submissions.findByAssignmentIdAndStudentId(assignmentId, student).orElse(null);
	}

	public HomeworkSubmission grade(String id, GradeRequest request, Authentication auth) {
		HomeworkSubmission result = submissions.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("提交不存在"));
		HomeworkAssignment assignment = assignment(result.getAssignmentId());
		teacherCan(scope(auth), assignment);
		if (!"SUBMITTED".equals(result.getStatus()) && !"RETURNED_FOR_REVISION".equals(result.getStatus()))
			throw new IllegalStateException("当前提交不能评分");
		if (request.score() != null
				&& (request.score() < 0 || request.score() > assignment.getMaxScore()))
			throw new IllegalArgumentException("分数超出作业满分");
		result.setScore(request.score());
		result.setTeacherFeedback(request.teacherFeedback());
		result.setStatus(request.returnForRevision() ? "RETURNED_FOR_REVISION" : "GRADED");
		result.setGradedAt(LocalDateTime.now());
		return result;
	}
}
