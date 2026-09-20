package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.EducationUserBindingRepository;
import com.chronos.education.scheduling.dao.LeaveRequestRecordRepository;
import com.chronos.education.scheduling.model.EducationUserBinding;
import com.chronos.education.scheduling.model.LeaveRequestRecord;
import com.chronos.service.iService.IAuditLogService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 请假业务台账查询、销假状态机和基础统计。 */
@Service
public class LeaveRecordService {
	private final LeaveRequestRecordRepository records;
	private final EducationUserBindingRepository bindings;
	private final IAuditLogService audit;

	public LeaveRecordService(
			LeaveRequestRecordRepository records,
			EducationUserBindingRepository bindings,
			IAuditLogService audit) {
		this.records = records;
		this.bindings = bindings;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public List<LeaveRequestRecord> mine(String username) {
		EducationUserBinding identity = leaveIdentity(username);
		return records.findByApplicantTypeAndApplicantIdOrderByStartDateDesc(
				identity.getProfileType(),
				identity.getProfileId());
	}

	@Transactional
	public LeaveRequestRecord requestCancellation(
			String username,
			String id,
			String reason) {
		LeaveRequestRecord record = records.findLockedById(id)
				.orElseThrow(() -> new IllegalArgumentException("请假记录不存在"));
		EducationUserBinding identity = leaveIdentity(username);
		if (!identity.getProfileType().equals(record.getApplicantType())
				|| !identity.getProfileId().equals(record.getApplicantId())) {
			throw new AccessDeniedException("只能为本人请假记录申请销假");
		}
		if (!"APPROVED".equals(record.getStatus())
				|| !"NONE".equals(record.getCancellationStatus())) {
			throw new IllegalStateException("当前请假记录不能申请销假");
		}
		record.setCancellationStatus("PENDING");
		record.setCancellationReason(required(reason, "销假原因不能为空"));
		record.setCancellationRequestedBy(username);
		record.setCancellationRequestedAt(LocalDateTime.now());
		audit.log(username, "EDUCATION_LEAVE_CANCELLATION_REQUEST", "leaveId=" + id);
		return records.save(record);
	}

	@Transactional(readOnly = true)
	public List<LeaveRequestRecord> pendingCancellations() {
		return records.findByCancellationStatusOrderByCancellationRequestedAtAsc("PENDING");
	}

	@Transactional
	public LeaveRequestRecord decideCancellation(
			String username,
			String id,
			boolean approved,
			String comment) {
		LeaveRequestRecord record = records.findLockedById(id)
				.orElseThrow(() -> new IllegalArgumentException("请假记录不存在"));
		if (!"PENDING".equals(record.getCancellationStatus())) {
			throw new IllegalStateException("销假申请已处理");
		}
		record.setCancellationStatus(approved ? "APPROVED" : "REJECTED");
		record.setCancellationDecidedBy(username);
		record.setCancellationDecidedAt(LocalDateTime.now());
		record.setCancellationComment(comment == null ? null : comment.trim());
		if (approved) {
			record.setStatus("CANCELLED");
		}
		audit.log(username, "EDUCATION_LEAVE_CANCELLATION_DECIDE",
				"leaveId=" + id + ",approved=" + approved);
		return records.save(record);
	}

	@Transactional(readOnly = true)
	public Map<String, Object> statistics(LocalDate start, LocalDate end) {
		if (start == null || end == null || end.isBefore(start)) {
			throw new IllegalArgumentException("统计日期范围不正确");
		}
		List<LeaveRequestRecord> values = records
				.findByStartDateLessThanEqualAndEndDateGreaterThanEqual(end, start);
		Map<String, Long> byType = values.stream().collect(java.util.stream.Collectors.groupingBy(
				LeaveRequestRecord::getLeaveType,
				java.util.stream.Collectors.counting()));
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("requestCount", values.size());
		result.put("teacherCount", values.stream()
				.filter(item -> "TEACHER".equals(item.getApplicantType())).count());
		result.put("studentCount", values.stream()
				.filter(item -> "STUDENT".equals(item.getApplicantType())).count());
		result.put("cancelledCount", values.stream()
				.filter(item -> "CANCELLED".equals(item.getStatus())).count());
		result.put("leaveDays", values.stream().mapToLong(item ->
				ChronoUnit.DAYS.between(
						item.getStartDate().isBefore(start) ? start : item.getStartDate(),
						(item.getEndDate().isAfter(end) ? end : item.getEndDate()).plusDays(1))).sum());
		result.put("byType", byType);
		return result;
	}

	private EducationUserBinding leaveIdentity(String username) {
		return bindings.findByUsernameAndStatusOrderByProfileType(username, "ACTIVE").stream()
				.filter(item -> "TEACHER".equals(item.getProfileType())
						|| "STUDENT".equals(item.getProfileType()))
				.findFirst()
				.orElseThrow(() -> new AccessDeniedException("当前账号未绑定教师或学生档案"));
	}

	private String required(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(message);
		}
		return value.trim();
	}
}
