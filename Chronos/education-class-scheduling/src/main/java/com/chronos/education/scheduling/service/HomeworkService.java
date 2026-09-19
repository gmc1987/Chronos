package com.chronos.education.scheduling.service;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.chronos.commons.model.PageView;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.HomeworkAssignmentRepository;
import com.chronos.education.scheduling.dao.HomeworkSubmissionRepository;
import com.chronos.education.scheduling.dao.TeachingClassMemberRepository;
import com.chronos.education.scheduling.dao.TeachingPlanItemRepository;
import com.chronos.education.scheduling.dao.TeachingPlanRepository;
import com.chronos.education.scheduling.dao.PreparationRepository;
import com.chronos.education.scheduling.dao.LessonPlanRepository;
import com.chronos.education.scheduling.dao.QuestionRepository;
import com.chronos.education.scheduling.dao.QuestionVersionRepository;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.education.scheduling.model.HomeworkAssignment;
import com.chronos.education.scheduling.model.HomeworkSubmission;
import com.chronos.education.scheduling.model.Question;
import com.chronos.education.scheduling.model.QuestionVersion;
import com.chronos.education.scheduling.model.TeachingClassMember;
import com.chronos.education.scheduling.model.TeachingPlanItem;
import com.chronos.education.scheduling.model.dto.HomeworkDtos.AssignmentRequest;
import com.chronos.education.scheduling.model.dto.HomeworkDtos.GradeRequest;
import com.chronos.education.scheduling.model.dto.HomeworkDtos.BatchGradeRequest;
import com.chronos.education.scheduling.model.dto.HomeworkDtos.SubmissionRequest;
import com.chronos.education.scheduling.model.dto.ResearchErrorDtos.WrongAnswerConfirmed;

@Service
@Transactional
public class HomeworkService {
	private static final ObjectMapper JSON = new ObjectMapper();
	private final HomeworkAssignmentRepository assignments;
	private final HomeworkSubmissionRepository submissions;
	private final TeachingClassMemberRepository members;
	private final CourseOfferingRepository offerings;
	private final EducationDataScopeService scopes;
	private final TeachingPlanItemRepository planItems;
	private final TeachingPlanRepository plans;
	private final PreparationRepository preparations;
	private final LessonPlanRepository lessonPlans;
	private final QuestionRepository questions;
	private final QuestionVersionRepository questionVersions;
	private final EducationDomainEventService domainEvents;

	public HomeworkService(HomeworkAssignmentRepository assignments,
			HomeworkSubmissionRepository submissions, TeachingClassMemberRepository members,
			CourseOfferingRepository offerings, EducationDataScopeService scopes,
			TeachingPlanItemRepository planItems, TeachingPlanRepository plans,
			PreparationRepository preparations, LessonPlanRepository lessonPlans,
			QuestionRepository questions, QuestionVersionRepository questionVersions,
			EducationDomainEventService domainEvents) {
		this.assignments = assignments;
		this.submissions = submissions;
		this.members = members;
		this.offerings = offerings;
		this.scopes = scopes;
		this.planItems = planItems; this.plans = plans;
		this.preparations = preparations; this.lessonPlans = lessonPlans;
		this.questions = questions; this.questionVersions = questionVersions;
		this.domainEvents = domainEvents;
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
			} else if (!value.studentIds().isEmpty() && value.teacherIds().isEmpty()) {
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

	private void validatePublishedQuestionRefs(String raw) {
		try {
			JsonNode refs = JSON.readTree(raw == null ? "[]" : raw);
			if (!refs.isArray() || refs.isEmpty()) {
				throw new IllegalArgumentException("发布作业必须引用至少一个题目的已发布版本");
			}
			for (JsonNode ref : refs) {
				String questionId = ref.path("questionId").asText(null);
				String versionId = ref.path("versionId").asText(null);
				if (questionId == null || versionId == null) {
					throw new IllegalArgumentException("题目版本引用必须包含 questionId 和 versionId");
				}
				Question question = questions.findById(questionId)
						.orElseThrow(() -> new IllegalArgumentException("引用题目不存在"));
				if (!versionId.equals(question.getPublishedVersionId())) {
					throw new IllegalArgumentException("作业只能引用题目的当前发布版本");
				}
				QuestionVersion version = questionVersions.findById(versionId)
						.orElseThrow(() -> new IllegalArgumentException("引用题目版本不存在"));
				if (!questionId.equals(version.getQuestionId()) || !"PUBLISHED".equals(version.getStatus())) {
					throw new IllegalArgumentException("引用题目版本未发布或不属于该题目");
				}
			}
		} catch (IllegalArgumentException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new IllegalArgumentException("题目版本引用格式无效", ex);
		}
	}

	private void apply(HomeworkAssignment result, AssignmentRequest request) {
		result.setOfferingId(request.offeringId());
		result.setType(request.type());
		result.setTeachingPlanItemId(request.teachingPlanItemId());
		result.setPreparationId(request.preparationId());
		result.setLessonPlanId(request.lessonPlanId());
		result.setTitle(request.title());
		result.setQuestionSnapshotJson(request.questionSnapshotJson());
		result.setQuestionVersionRefsJson(request.questionVersionRefsJson() == null ? "[]" : request.questionVersionRefsJson());
		result.setInstructionsJson(request.instructionsJson());
		result.setDueAt(request.dueAt());
		result.setStartAt(request.startAt());
		result.setMaxScore(request.maxScore() == null ? 100 : request.maxScore());
		result.setAttemptLimit(request.attemptLimit() == null ? 1 : request.attemptLimit());
		result.setAllowLate(request.allowLate());
		result.setLateRule(request.lateRule() == null ? (request.allowLate() ? "ALLOW" : "REJECT") : request.lateRule());
		result.setPublishAudience(request.publishAudience() == null ? "ENROLLED_STUDENTS" : request.publishAudience());
		result.setAttachmentSnapshotJson(request.attachmentSnapshotJson() == null ? "[]" : request.attachmentSnapshotJson());
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
		validatePublishedQuestionRefs(result.getQuestionVersionRefsJson());
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

	public HomeworkAssignment archive(String id, Authentication auth) {
		HomeworkAssignment result = assignment(id);
		teacherCan(scope(auth), result);
		if (!"CLOSED".equals(result.getStatus())) {
			throw new IllegalStateException("只有已关闭作业可以归档");
		}
		result.setStatus("ARCHIVED");
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
		if (result.getId() != null && !"NOT_STARTED".equals(result.getStatus())
				&& !"DRAFT".equals(result.getStatus())
				&& !"RETURNED_FOR_REVISION".equals(result.getStatus())) {
			throw new IllegalStateException("当前提交不能修改");
		}
		if (result.getId() == null) {
			result.setAttemptNo(1);
		} else if ("RETURNED_FOR_REVISION".equals(result.getStatus())) {
			int nextAttempt = result.getAttemptNo() + 1;
			if (nextAttempt > assignment.getAttemptLimit()) {
				throw new IllegalStateException("已达到提交次数限制");
			}
			result.setAttemptNo(nextAttempt);
		}
		result.setAssignmentId(assignmentId);
		result.setStudentId(student);
		result.setAnswerSnapshotJson(request.answerSnapshotJson());
		result.setAttachmentSnapshotJson(request.attachmentSnapshotJson() == null ? "[]" : request.attachmentSnapshotJson());
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
		if (!assignment.isAllowLate() && !"ALLOW".equals(assignment.getLateRule())
				&& assignment.getDueAt() != null
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
		String questionScores = request.questionScoresJson() == null ? "{}" : request.questionScoresJson();
		validateQuestionScores(questionScores, assignment.getMaxScore());
		result.setScore(request.score());
		result.setTeacherFeedback(request.teacherFeedback());
		result.setQuestionScoresJson(questionScores);
		result.setAnnotationSnapshotJson(request.annotationSnapshotJson() == null ? "[]" : request.annotationSnapshotJson());
		result.setStatus(request.returnForRevision() ? "RETURNED_FOR_REVISION" : "GRADED");
		result.setGradedAt(LocalDateTime.now());
		return result;
	}

	private void validateQuestionScores(String raw, int maxScore) {
		try {
			JsonNode node = JSON.readTree(raw);
			if (node == null || !node.isObject()) {
				throw new IllegalArgumentException("题目得分必须是 JSON 对象");
			}
			int total = 0;
			var fields = node.fields();
			while (fields.hasNext()) {
				JsonNode score = fields.next().getValue();
				if (!score.isNumber() || score.asInt() < 0) {
					throw new IllegalArgumentException("题目得分必须是非负数字");
				}
				total += score.asInt();
			}
			if (total > maxScore) {
				throw new IllegalArgumentException("题目得分合计超出作业满分");
			}
		} catch (IllegalArgumentException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new IllegalArgumentException("题目得分格式无效", ex);
		}
	}

	public List<HomeworkSubmission> batchGrade(String assignmentId, BatchGradeRequest request,
			Authentication auth) {
		HomeworkAssignment homework = assignment(assignmentId);
		teacherCan(scope(auth), homework);
		if (request.submissionIds() == null || request.submissionIds().isEmpty()) {
			throw new IllegalArgumentException("至少选择一条提交");
		}
		return request.submissionIds().stream().map(id -> {
			HomeworkSubmission submission = submissions.findById(id)
					.orElseThrow(() -> new IllegalArgumentException("提交不存在"));
			if (!assignmentId.equals(submission.getAssignmentId())) {
				throw new AccessDeniedException("提交不属于该作业");
			}
			return grade(id, new GradeRequest(request.score(), request.teacherFeedback(),
					request.questionScoresJson(), request.annotationSnapshotJson(),
					request.returnForRevision()), auth);
		}).toList();
	}

	public int publishGrades(String assignmentId, Authentication auth) {
		HomeworkAssignment homework = assignment(assignmentId);
		teacherCan(scope(auth), homework);
		if (!"CLOSED".equals(homework.getStatus()) && !"PUBLISHED".equals(homework.getStatus())) {
			throw new IllegalStateException("只有已发布或已关闭作业可以发布成绩");
		}
		List<HomeworkSubmission> values = submissions.findByAssignmentIdOrderByCreateTimeAsc(assignmentId);
		CourseOffering offering = offering(homework.getOfferingId());
		List<HomeworkSubmission> pending = values.stream()
				.filter(submission -> "GRADED".equals(submission.getStatus()))
				.filter(submission -> !submission.isGradesPublished())
				.toList();
		pending.forEach(submission -> {
					// 成绩发布才是作业评分的业务确认点；草稿评分不能提前污染错题本。
					publishWrongAnswers(homework, offering, submission, auth);
					submission.setGradesPublished(true);
				});
		// 返回本次新发布数量，重复点击时返回 0，便于前端准确反馈幂等结果。
		return pending.size();
	}

	private void publishWrongAnswers(
			HomeworkAssignment homework,
			CourseOffering offering,
			HomeworkSubmission submission,
			Authentication authentication) {
		try {
			JsonNode refs = JSON.readTree(homework.getQuestionVersionRefsJson());
			JsonNode scoreMap = JSON.readTree(submission.getQuestionScoresJson());
			if (refs == null || !refs.isArray() || scoreMap == null || !scoreMap.isObject()) {
				return;
			}
			for (JsonNode ref : refs) {
				String questionId = ref.path("questionId").asText(null);
				String versionId = ref.path("versionId").asText(null);
				JsonNode score = questionId == null ? null : scoreMap.get(questionId);
				if (score == null && versionId != null) {
					score = scoreMap.get(versionId);
				}
				if (!isConfirmedWrongAnswer(ref, score)) {
					continue;
				}
				String sourceItemId = UUID.nameUUIDFromBytes((submission.getId() + ":" + questionId)
						.getBytes(StandardCharsets.UTF_8)).toString();
				domainEvents.enqueueWrongAnswer(
						new WrongAnswerConfirmed(
								"HOMEWORK_GRADE_PUBLISHED:" + sourceItemId,
								submission.getStudentId(),
								offering.getCourseCode(),
								offering.getSemesterCode(),
								questionId,
								sourceItemId,
								homework.getId(),
								submission.getTeacherFeedback(),
								"HOMEWORK",
								versionId,
								submission.getGradedAt()),
						authentication.getName());
			}
		} catch (IllegalArgumentException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new IllegalStateException("发布作业错题事件失败", exception);
		}
	}

	private boolean isConfirmedWrongAnswer(JsonNode reference, JsonNode score) {
		if (score == null || !score.isNumber()) {
			return false;
		}
		JsonNode maxScore = reference.get("maxScore");
		if (maxScore != null && maxScore.isNumber()) {
			return score.decimalValue().compareTo(maxScore.decimalValue()) < 0;
		}
		// 旧数据没有题目满分时只能确认零分题，避免把部分得分误判为错题。
		return score.decimalValue().signum() == 0;
	}
}
