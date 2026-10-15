package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.HomeworkAssignmentRepository;
import com.chronos.education.scheduling.dao.HomeworkSubmissionRepository;
import com.chronos.education.scheduling.dao.TeachingClassMemberRepository;
import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.education.scheduling.model.HomeworkAssignment;
import com.chronos.education.scheduling.model.HomeworkSubmission;
import com.chronos.file.service.ManagedFileAccessPolicy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 按实时教学班成员和教师数据范围控制作业附件，而非只信任文件上传者。 */
@Component
@Transactional(readOnly = true)
public class HomeworkManagedFileAccessPolicy implements ManagedFileAccessPolicy {
	private static final String ASSIGNMENT = "EDUCATION_HOMEWORK_ASSIGNMENT";
	private static final String SUBMISSION = "EDUCATION_HOMEWORK_SUBMISSION";

	private final HomeworkAssignmentRepository assignments;
	private final HomeworkSubmissionRepository submissions;
	private final TeachingClassMemberRepository members;
	private final CourseOfferingRepository offerings;
	private final EducationDataScopeService scopes;

	public HomeworkManagedFileAccessPolicy(HomeworkAssignmentRepository assignments,
			HomeworkSubmissionRepository submissions, TeachingClassMemberRepository members,
			CourseOfferingRepository offerings, EducationDataScopeService scopes) {
		this.assignments = assignments;
		this.submissions = submissions;
		this.members = members;
		this.offerings = offerings;
		this.scopes = scopes;
	}

	@Override
	public boolean canRead(String username, String businessType, String businessId) {
		if (ASSIGNMENT.equals(businessType)) {
			return assignments.findById(businessId)
					.map(assignment -> teacherCan(username, assignment)
							|| (published(assignment) && studentCan(username, assignment)))
					.orElse(false);
		}
		if (SUBMISSION.equals(businessType)) {
			return submissions.findById(businessId)
					.map(submission -> ownerCan(username, submission)
							|| assignments.findById(submission.getAssignmentId())
									.map(assignment -> teacherCan(username, assignment)).orElse(false))
					.orElse(false);
		}
		return false;
	}

	@Override
	public boolean canWrite(String username, String businessType, String businessId) {
		if (ASSIGNMENT.equals(businessType)) {
			return assignments.findById(businessId)
					.map(assignment -> "DRAFT".equals(assignment.getStatus())
							&& teacherCan(username, assignment))
					.orElse(false);
		}
		if (SUBMISSION.equals(businessType)) {
			return submissions.findById(businessId)
					.map(submission -> "DRAFT".equals(submission.getStatus())
							&& ownerCan(username, submission)
							&& assignments.findById(submission.getAssignmentId())
									.map(this::published).orElse(false))
					.orElse(false);
		}
		return false;
	}

	private boolean published(HomeworkAssignment assignment) {
		return "PUBLISHED".equals(assignment.getStatus()) || "CLOSED".equals(assignment.getStatus());
	}

	private boolean teacherCan(String username, HomeworkAssignment assignment) {
		EducationDataScope scope = scopes.resolve(username);
		if (!scope.fullAccess() && scope.teacherIds().isEmpty()) return false;
		return offerings.findById(assignment.getOfferingId())
				.map(offering -> scopes.canAccessOffering(scope, offering)).orElse(false);
	}

	private boolean studentCan(String username, HomeworkAssignment assignment) {
		EducationDataScope scope = scopes.resolve(username);
		return scope.studentIds().stream().anyMatch(studentId ->
				members.findByOfferingIdAndStudentId(assignment.getOfferingId(), studentId)
						.map(member -> "ENROLLED".equals(member.getEnrollmentStatus())
								|| "ACTIVE".equals(member.getEnrollmentStatus()))
						.orElse(false));
	}

	private boolean ownerCan(String username, HomeworkSubmission submission) {
		return scopes.resolve(username).studentIds().contains(submission.getStudentId());
	}
}
