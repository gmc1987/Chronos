package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.AdministrativeClassRepository;
import com.chronos.education.scheduling.dao.StudentProfileRepository;
import com.chronos.education.scheduling.dao.StudentStatusChangeRepository;
import com.chronos.education.scheduling.dao.TeachingClassMemberRepository;
import com.chronos.education.scheduling.model.AdministrativeClass;
import com.chronos.education.scheduling.model.StudentProfile;
import com.chronos.education.scheduling.model.StudentStatusChange;
import com.chronos.education.scheduling.model.TeachingClassMember;
import com.chronos.education.scheduling.model.dto.StudentStatusChangeCommand;
import com.chronos.service.iService.IAuditLogService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 管理学籍异动申请、审批和学生主档生效，禁止直接覆盖历史状态。 */
@Service
public class StudentStatusChangeService {
	private static final Map<String, String> TARGET_STATUS = Map.of(
			"SUSPEND", "SUSPENDED",
			"RESUME", "ACTIVE",
			"TRANSFER_OUT", "TRANSFERRED",
			"WITHDRAW", "WITHDRAWN",
			"GRADUATE", "GRADUATED",
			"TRANSFER_CLASS", "ACTIVE",
			"RETAIN_GRADE", "ACTIVE");

	private final StudentStatusChangeRepository changes;
	private final StudentProfileRepository students;
	private final AdministrativeClassRepository classes;
	private final TeachingClassMemberRepository members;
	private final IAuditLogService audit;

	public StudentStatusChangeService(
			StudentStatusChangeRepository changes,
			StudentProfileRepository students,
			AdministrativeClassRepository classes,
			TeachingClassMemberRepository members,
			IAuditLogService audit) {
		this.changes = changes;
		this.students = students;
		this.classes = classes;
		this.members = members;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public Page<StudentStatusChange> page(String studentId, int page, int size) {
		return changes.findByStudentIdOrderByRequestedAtDesc(
				studentId,
				PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100)));
	}

	@Transactional
	public StudentStatusChange request(
			String studentId,
			StudentStatusChangeCommand command,
			String username) {
		StudentProfile student = requireStudent(studentId);
		String type = normalizeType(command.changeType());
		validateTransition(student.getEnrollmentStatus(), type);
		if (changes.existsByStudentIdAndStatus(studentId, "PENDING")) {
			throw new IllegalStateException("学生已有待审批的学籍异动");
		}

		AdministrativeClass targetClass = targetClass(type, command.targetClassId());
		StudentStatusChange change = new StudentStatusChange();
		change.setStudentId(studentId);
		change.setChangeType(type);
		change.setFromStatus(student.getEnrollmentStatus());
		change.setToStatus(TARGET_STATUS.get(type));
		change.setFromGradeId(student.getGradeId());
		change.setToGradeId(targetClass == null ? student.getGradeId() : targetClass.getGradeId());
		change.setFromMajorId(student.getMajorId());
		change.setToMajorId(targetClass == null ? student.getMajorId() : targetClass.getMajorId());
		change.setFromClassId(student.getAdministrativeClassId());
		change.setToClassId(targetClass == null ? student.getAdministrativeClassId() : targetClass.getId());
		change.setEffectiveDate(command.effectiveDate() == null ? LocalDate.now() : command.effectiveDate());
		change.setReason(requireText(command.reason(), "异动原因不能为空"));
		change.setRequestedBy(username);
		change.setRequestedAt(LocalDateTime.now());
		StudentStatusChange saved = changes.save(change);
		audit.log(username, "STUDENT_STATUS_CHANGE_REQUEST", "changeId=" + saved.getId() + ",studentId=" + studentId);
		return saved;
	}

	@Transactional
	public StudentStatusChange approve(String id, String comment, String username) {
		StudentStatusChange change = requirePending(id);
		change.setStatus("APPROVED_PENDING");
		change.setDecisionComment(comment);
		change.setDecidedBy(username);
		change.setDecidedAt(LocalDateTime.now());
		changes.save(change);
		if (change.getEffectiveDate().isAfter(LocalDate.now())) {
			audit.log(username, "STUDENT_STATUS_CHANGE_APPROVE", "changeId=" + id + ",waitingEffectiveDate=true");
			return change;
		}
		return applyApproved(change, username);
	}

	/** 调度器只处理已经批准且到达生效日期的记录，重复执行保持幂等。 */
	@Transactional
	public StudentStatusChange applyDue(String id) {
		StudentStatusChange change = changes.findLockedById(id)
				.orElseThrow(() -> new IllegalArgumentException("学籍异动不存在"));
		if ("APPROVED".equals(change.getStatus())) {
			return change;
		}
		if (!"APPROVED_PENDING".equals(change.getStatus())
				|| change.getEffectiveDate().isAfter(LocalDate.now())) {
			throw new IllegalStateException("学籍异动尚未到生效时间");
		}
		return applyApproved(change, "SYSTEM");
	}

	@Transactional(readOnly = true)
	public java.util.List<String> dueChangeIds() {
		return changes.findTop100ByStatusAndEffectiveDateLessThanEqualOrderByEffectiveDateAsc(
				"APPROVED_PENDING",
				LocalDate.now()).stream()
				.map(StudentStatusChange::getId)
				.toList();
	}

	private StudentStatusChange applyApproved(StudentStatusChange change, String operator) {
		StudentProfile student = requireStudentForUpdate(change.getStudentId());
		// 审批期间如果学生主档已经变化，拒绝使用过期快照覆盖最新数据。
		if (!change.getFromStatus().equals(student.getEnrollmentStatus())
				|| !change.getFromClassId().equals(student.getAdministrativeClassId())) {
			throw new IllegalStateException("学生档案已发生变化，请驳回后重新申请");
		}

		student.setEnrollmentStatus(change.getToStatus());
		student.setGradeId(change.getToGradeId());
		student.setMajorId(change.getToMajorId());
		student.setAdministrativeClassId(change.getToClassId());
		students.save(student);
		synchronizeTeachingClassMembers(change);

		change.setStatus("APPROVED");
		change.setAppliedAt(LocalDateTime.now());
		audit.log(operator, "STUDENT_STATUS_CHANGE_APPLY", "changeId=" + change.getId() + ",studentId=" + student.getId());
		return changes.save(change);
	}

	@Transactional
	public StudentStatusChange reject(String id, String comment, String username) {
		StudentStatusChange change = requirePending(id);
		change.setStatus("REJECTED");
		change.setDecisionComment(requireText(comment, "驳回原因不能为空"));
		change.setDecidedBy(username);
		change.setDecidedAt(LocalDateTime.now());
		audit.log(username, "STUDENT_STATUS_CHANGE_REJECT", "changeId=" + id + ",studentId=" + change.getStudentId());
		return changes.save(change);
	}

	private void synchronizeTeachingClassMembers(StudentStatusChange change) {
		LocalDateTime now = LocalDateTime.now();
		members.findByStudentIdAndEnrollmentStatus(change.getStudentId(), "ENROLLED").stream()
				.filter(member -> shouldWithdraw(change, member))
				.forEach(member -> {
					member.setEnrollmentStatus("WITHDRAWN");
					member.setWithdrawnAt(now);
					members.save(member);
				});
	}

	private boolean shouldWithdraw(StudentStatusChange change, TeachingClassMember member) {
		if (!"ACTIVE".equals(change.getToStatus())) {
			return true;
		}
		return ("TRANSFER_CLASS".equals(change.getChangeType())
				|| "RETAIN_GRADE".equals(change.getChangeType()))
				&& "SOURCE_CLASS".equals(member.getEnrollmentSource());
	}

	private StudentStatusChange requirePending(String id) {
		StudentStatusChange change = changes.findLockedById(id)
				.orElseThrow(() -> new IllegalArgumentException("学籍异动不存在"));
		if (!"PENDING".equals(change.getStatus())) {
			throw new IllegalStateException("学籍异动已处理，不能重复操作");
		}
		return change;
	}

	private StudentProfile requireStudent(String id) {
		return students.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("学生不存在"));
	}

	private StudentProfile requireStudentForUpdate(String id) {
		return students.findLockedById(id)
				.orElseThrow(() -> new IllegalArgumentException("学生不存在"));
	}

	private AdministrativeClass targetClass(String type, String id) {
		boolean required = "TRANSFER_CLASS".equals(type) || "RETAIN_GRADE".equals(type);
		if (!required) {
			return null;
		}
		if (!StringUtils.hasText(id)) {
			throw new IllegalArgumentException("转班或留级必须选择目标行政班");
		}
		AdministrativeClass target = classes.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("目标行政班不存在"));
		if (!"ACTIVE".equals(target.getStatus())) {
			throw new IllegalStateException("目标行政班不可用");
		}
		return target;
	}

	private void validateTransition(String currentStatus, String type) {
		boolean allowed = switch (type) {
			case "RESUME" -> "SUSPENDED".equals(currentStatus);
			case "SUSPEND", "TRANSFER_OUT", "WITHDRAW", "GRADUATE", "TRANSFER_CLASS", "RETAIN_GRADE" ->
					"ACTIVE".equals(currentStatus);
			default -> false;
		};
		if (!allowed) {
			throw new IllegalStateException("当前学籍状态不允许执行该异动");
		}
	}

	private String normalizeType(String type) {
		String normalized = StringUtils.hasText(type) ? type.trim().toUpperCase() : "";
		if (!TARGET_STATUS.containsKey(normalized)) {
			throw new IllegalArgumentException("不支持的学籍异动类型");
		}
		return normalized;
	}

	private String requireText(String value, String message) {
		if (!StringUtils.hasText(value)) {
			throw new IllegalArgumentException(message);
		}
		return value.trim();
	}
}
