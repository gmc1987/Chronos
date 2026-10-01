package com.chronos.education.scheduling.service;

import com.chronos.service.iService.IAuditLogService;
import com.chronos.education.scheduling.dao.AdministrativeClassRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.EducationUserBindingRepository;
import com.chronos.education.scheduling.dao.TeacherAcademicProfileRepository;
import com.chronos.education.scheduling.dao.TeacherEmploymentChangeRepository;
import com.chronos.education.scheduling.dao.TeacherTeachingAssignmentRepository;
import com.chronos.education.scheduling.model.TeacherAcademicProfile;
import com.chronos.education.scheduling.model.TeacherEmploymentChange;
import com.chronos.education.scheduling.model.dto.TeacherEmploymentChangeCommand;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理教师有效日期任职异动，并在停职、离职前强制完成教学职责交接。 */
@Service
public class TeacherEmploymentChangeService {
	private static final Map<String, String> TARGET_STATUS = Map.of(
			"TRANSFER", "ACTIVE",
			"SUSPEND", "SUSPENDED",
			"RESUME", "ACTIVE",
			"TERMINATE", "TERMINATED");
	private final TeacherAcademicProfileRepository teachers;
	private final TeacherEmploymentChangeRepository changes;
	private final TeacherTeachingAssignmentRepository assignments;
	private final CourseOfferingRepository offerings;
	private final AdministrativeClassRepository classes;
	private final EducationUserBindingRepository bindings;
	private final IAuditLogService audit;

	public TeacherEmploymentChangeService(
			TeacherAcademicProfileRepository teachers,
			TeacherEmploymentChangeRepository changes,
			TeacherTeachingAssignmentRepository assignments,
			CourseOfferingRepository offerings,
			AdministrativeClassRepository classes,
			EducationUserBindingRepository bindings,
			IAuditLogService audit) {
		this.teachers = teachers;
		this.changes = changes;
		this.assignments = assignments;
		this.offerings = offerings;
		this.classes = classes;
		this.bindings = bindings;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public Page<TeacherEmploymentChange> history(String teacherId, int page, int size) {
		return changes.findByTeacherIdOrderByEffectiveDateDesc(
				teacherId,
				PageRequest.of(Math.max(0, page), Math.min(Math.max(size, 1), 100)));
	}

	@Transactional
	public TeacherEmploymentChange register(
			String teacherId,
			TeacherEmploymentChangeCommand command,
			String username) {
		TeacherAcademicProfile teacher = teachers.findLockedById(teacherId)
				.orElseThrow(() -> new IllegalArgumentException("教师档案不存在"));
		String type = required(command.changeType(), "任职异动类型不能为空");
		String targetStatus = TARGET_STATUS.get(type);
		if (targetStatus == null) {
			throw new IllegalArgumentException("不支持的教师任职异动类型");
		}
		validateTransition(teacher, type, command.targetDepartmentId());
		if (changes.existsByTeacherIdAndStatus(teacherId, "SCHEDULED")) {
			throw new IllegalStateException("该教师已有待生效任职异动");
		}
		TeacherEmploymentChange value = new TeacherEmploymentChange();
		value.setTeacherId(teacherId);
		value.setChangeType(type);
		value.setFromStatus(teacher.getEmploymentStatus());
		value.setToStatus(targetStatus);
		value.setFromDepartmentId(teacher.getDepartmentId());
		value.setToDepartmentId("TRANSFER".equals(type)
				? required(command.targetDepartmentId(), "目标单位不能为空")
				: teacher.getDepartmentId());
		value.setEffectiveDate(command.effectiveDate() == null
				? LocalDate.now()
				: command.effectiveDate());
		value.setReason(required(command.reason(), "异动原因不能为空"));
		value.setStatus("SCHEDULED");
		value.setCreatedBy(username);
		TeacherEmploymentChange saved = changes.save(value);
		if (!saved.getEffectiveDate().isAfter(LocalDate.now())) {
			return apply(saved.getId(), username);
		}
		audit.log(username, "TEACHER_EMPLOYMENT_CHANGE_REGISTER", "changeId=" + saved.getId());
		return saved;
	}

	@Transactional
	public TeacherEmploymentChange apply(String id, String operator) {
		TeacherEmploymentChange change = changes.findLockedById(id)
				.orElseThrow(() -> new IllegalArgumentException("教师任职异动不存在"));
		if ("APPLIED".equals(change.getStatus())) {
			return change;
		}
		if (!"SCHEDULED".equals(change.getStatus())
				|| change.getEffectiveDate().isAfter(LocalDate.now())) {
			throw new IllegalStateException("教师任职异动尚未到生效时间");
		}
		TeacherAcademicProfile teacher = teachers.findLockedById(change.getTeacherId())
				.orElseThrow(() -> new IllegalArgumentException("教师档案不存在"));
		if (!change.getFromStatus().equals(teacher.getEmploymentStatus())
				|| !java.util.Objects.equals(
						change.getFromDepartmentId(),
						teacher.getDepartmentId())) {
			throw new IllegalStateException("教师档案已变化，请取消后重新登记异动");
		}
		if ("SUSPEND".equals(change.getChangeType())
				|| "TERMINATE".equals(change.getChangeType())) {
			assertDutiesHandedOver(teacher.getId());
		}
		teacher.setDepartmentId(change.getToDepartmentId());
		teacher.setEmploymentStatus(change.getToStatus());
		teacher.setEnabled("ACTIVE".equals(change.getToStatus()));
		teachers.save(teacher);
		synchronizeBinding(teacher.getId(), teacher.getEnabled());
		change.setStatus("APPLIED");
		change.setAppliedAt(LocalDateTime.now());
		audit.log(operator, "TEACHER_EMPLOYMENT_CHANGE_APPLY", "changeId=" + id);
		return changes.save(change);
	}

	@Transactional
	public TeacherEmploymentChange cancel(String id, String username) {
		TeacherEmploymentChange change = changes.findLockedById(id)
				.orElseThrow(() -> new IllegalArgumentException("教师任职异动不存在"));
		if (!"SCHEDULED".equals(change.getStatus())) {
			throw new IllegalStateException("只有待生效任职异动可以取消");
		}
		change.setStatus("CANCELLED");
		audit.log(username, "TEACHER_EMPLOYMENT_CHANGE_CANCEL", "changeId=" + id);
		return changes.save(change);
	}

	@Transactional(readOnly = true)
	public List<String> dueIds() {
		return changes.findTop100ByStatusAndEffectiveDateLessThanEqualOrderByEffectiveDateAsc(
				"SCHEDULED",
				LocalDate.now()).stream().map(TeacherEmploymentChange::getId).toList();
	}

	private void validateTransition(
			TeacherAcademicProfile teacher,
			String type,
			String targetDepartmentId) {
		String status = teacher.getEmploymentStatus();
		if ("SUSPEND".equals(type) && !"ACTIVE".equals(status)
				|| "RESUME".equals(type) && !"SUSPENDED".equals(status)
				|| "TERMINATE".equals(type) && "TERMINATED".equals(status)
				|| "TRANSFER".equals(type) && !"ACTIVE".equals(status)) {
			throw new IllegalStateException("当前教师任职状态不允许该异动");
		}
		if ("TRANSFER".equals(type)
				&& java.util.Objects.equals(teacher.getDepartmentId(), targetDepartmentId)) {
			throw new IllegalArgumentException("目标单位不能与当前单位相同");
		}
	}

	private void assertDutiesHandedOver(String teacherId) {
		if (!assignments.findByTeacherIdAndEnabledTrue(teacherId).isEmpty()
				|| offerings.findByTeacherIdOrderByOfferingCode(teacherId).stream()
						.anyMatch(item -> "ACTIVE".equals(item.getStatus()))
				|| !classes.findByHeadTeacherId(teacherId).isEmpty()) {
			throw new IllegalStateException("教师仍承担任教、开课或班主任职责，请完成交接后再生效");
		}
	}

	private void synchronizeBinding(String teacherId, boolean active) {
		bindings.findByProfileTypeAndProfileId("TEACHER", teacherId).ifPresent(binding -> {
			binding.setStatus(active ? "ACTIVE" : "INACTIVE");
			bindings.save(binding);
		});
	}

	private String required(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(message);
		}
		return value.trim();
	}
}
