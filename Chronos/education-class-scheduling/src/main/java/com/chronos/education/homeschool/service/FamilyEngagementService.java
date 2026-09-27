package com.chronos.education.homeschool.service;

import com.chronos.education.homeschool.dao.*;
import com.chronos.education.homeschool.dto.FamilyEngagementDtos.*;
import com.chronos.education.homeschool.model.*;
import com.chronos.education.scheduling.dao.*;
import com.chronos.education.scheduling.model.*;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.file.service.ManagedFileService;
import com.chronos.service.iService.IAuditLogService;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FamilyEngagementService {
	private static final String FEEDBACK_TYPE = "EDU_PARENT_FEEDBACK";
	private final ParentFeedbackRepository feedbacks;
	private final CommunicationRecordRepository communications;
	private final ParentAccountBindingRepository bindings;
	private final ParentProfileRepository parents;
	private final StudentProfileRepository students;
	private final StudentGuardianRepository guardians;
	private final AdministrativeClassRepository classes;
	private final EducationDataScopeService scopes;
	private final ManagedFileService files;
	private final IAuditLogService audit;

	public FamilyEngagementService(ParentFeedbackRepository feedbacks,
			CommunicationRecordRepository communications, ParentAccountBindingRepository bindings,
			ParentProfileRepository parents, StudentProfileRepository students,
			StudentGuardianRepository guardians, AdministrativeClassRepository classes,
			EducationDataScopeService scopes, ManagedFileService files, IAuditLogService audit) {
		this.feedbacks = feedbacks;
		this.communications = communications;
		this.bindings = bindings;
		this.parents = parents;
		this.students = students;
		this.guardians = guardians;
		this.classes = classes;
		this.scopes = scopes;
		this.files = files;
		this.audit = audit;
	}

	@Transactional
	public FeedbackResponse submit(FeedbackCommand command, String username) {
		ParentAccountBinding binding = activeParent(username);
		if (command == null || blank(command.studentId()) || blank(command.title()) || blank(command.content()))
			throw new IllegalArgumentException("反馈学生、标题和内容不能为空");
		StudentProfile student = studentForParent(binding.getParentId(), command.studentId());
		ParentFeedback value = new ParentFeedback();
		value.setParentId(binding.getParentId());
		value.setStudentId(student.getId());
		value.setClassId(student.getAdministrativeClassId());
		value.setTitle(command.title().trim());
		value.setContent(command.content().trim());
		value.setDueAt(command.dueAt());
		value = feedbacks.save(value);
		if (command.fileIds() != null && !command.fileIds().isEmpty())
			files.bind(command.fileIds(), FEEDBACK_TYPE, value.getId(), username);
		audit.log(username, "EDU_PARENT_FEEDBACK_CREATE",
				"feedbackId=" + value.getId() + ",studentId=" + student.getId());
		return response(value);
	}

	@Transactional(readOnly = true)
	public List<FeedbackResponse> parentFeedbacks(String username) {
		ParentAccountBinding binding = activeParent(username);
		return feedbacks.findByParentIdOrderByCreateTimeDesc(binding.getParentId()).stream()
				.map(this::response).toList();
	}

	@Transactional(readOnly = true)
	public List<FeedbackResponse> staffFeedbacks(String username) {
		EducationDataScope scope = scopes.resolve(username);
		return feedbacks.findAllByOrderByCreateTimeDesc().stream()
				.filter(item -> scope.fullAccess() || scopes.canAccessStudent(scope, item.getStudentId()))
				.map(this::response).toList();
	}

	@Transactional
	public FeedbackResponse transition(String id, FeedbackActionCommand command, String username) {
		ParentFeedback item = feedbacks.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("反馈不存在"));
		String next = command == null ? null : command.status();
		if (next == null || !Set.of("ACCEPTED", "ASSIGNED", "IN_PROGRESS", "WAITING_SUPPLEMENT",
				"RESOLVED", "CLOSED").contains(next))
			throw new IllegalArgumentException("不支持的反馈状态");
		assertStaffStudentAccess(username, item.getStudentId());
		String previous = item.getStatus();
		if (!allowed(previous, next))
			throw new IllegalStateException("不允许从 " + item.getStatus() + " 流转到 " + next);
		if ("ASSIGNED".equals(next) && blank(command.assignee()))
			throw new IllegalArgumentException("分派必须指定处理人");
		item.setStatus(next);
		if (command.assignee() != null && !command.assignee().isBlank()) item.setAssignedTo(command.assignee().trim());
		if ("ACCEPTED".equals(next)) item.setAcceptedAt(LocalDateTime.now());
		if ("RESOLVED".equals(next)) item.setResolvedAt(LocalDateTime.now());
		if ("CLOSED".equals(next)) item.setClosedAt(LocalDateTime.now());
		item = feedbacks.save(item);
		audit.log(username, "EDU_PARENT_FEEDBACK_STATUS",
				"feedbackId=" + id + ",from=" + previous + ",to=" + next);
		return response(item);
	}

	@Transactional
	public FeedbackResponse reply(String id, String reply, String username) {
		ParentFeedback item = feedbacks.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("反馈不存在"));
		String next = switch (item.getStatus()) {
			case "SUBMITTED" -> "ACCEPTED";
			case "ACCEPTED", "ASSIGNED", "WAITING_SUPPLEMENT" -> "IN_PROGRESS";
			case "IN_PROGRESS" -> "RESOLVED";
			default -> throw new IllegalStateException("当前状态不能回复");
		};
		return transition(id, new FeedbackActionCommand(next, null, reply), username);
	}

	@Transactional
	public FeedbackResponse parentConfirm(String id, boolean reopen, String username) {
		ParentAccountBinding binding = activeParent(username);
		ParentFeedback item = feedbacks.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("反馈不存在"));
		if (!binding.getParentId().equals(item.getParentId()))
			throw new AccessDeniedException("无权操作该反馈");
		if (reopen) {
			if (!Set.of("RESOLVED", "CLOSED").contains(item.getStatus()))
				throw new IllegalStateException("当前状态不能重新打开");
			item.setStatus("REOPENED");
			item.setReopenedAt(LocalDateTime.now());
			item.setClosedAt(null);
		} else {
			if (!"RESOLVED".equals(item.getStatus()))
				throw new IllegalStateException("只有已解决反馈可以确认");
			item.setStatus("CLOSED");
			item.setParentConfirmedAt(LocalDateTime.now());
			item.setClosedAt(LocalDateTime.now());
		}
		item = feedbacks.save(item);
		audit.log(username, reopen ? "EDU_PARENT_FEEDBACK_REOPEN" : "EDU_PARENT_FEEDBACK_CONFIRM",
				"feedbackId=" + id);
		return response(item);
	}

	@Transactional
	public CommunicationResponse record(CommunicationCommand command, String username) {
		if (command == null || blank(command.studentId()) || blank(command.channel())
				|| blank(command.content()))
			throw new IllegalArgumentException("沟通学生、渠道和内容不能为空");
		StudentProfile student = students.findById(command.studentId())
				.orElseThrow(() -> new IllegalArgumentException("学生不存在"));
		EducationDataScope scope = scopes.resolve(username);
		scopes.assertClassAccess(scope, student.getAdministrativeClassId());
		CommunicationRecord value = new CommunicationRecord();
		value.setTeacherUsername(username);
		value.setStudentId(student.getId());
		value.setClassId(student.getAdministrativeClassId());
		value.setChannel(command.channel().trim());
		value.setSubject(command.subject());
		value.setContent(command.content().trim());
		value.setSensitiveContent(command.sensitiveContent());
		value.setOccurredAt(command.occurredAt() == null ? LocalDateTime.now() : command.occurredAt());
		value = communications.save(value);
		audit.log(username, "EDU_HOME_COMMUNICATION_CREATE", "recordId=" + value.getId()
				+ ",studentId=" + value.getStudentId());
		return response(value, canReadSensitive(username));
	}

	@Transactional(readOnly = true)
	public List<CommunicationResponse> listCommunications(String username) {
		boolean parent = bindings.findByUsernameAndStatus(username, "ACTIVE").isPresent();
		List<CommunicationRecord> values;
		if (parent) {
			String parentId = activeParent(username).getParentId();
			Set<String> studentIds = guardians.findByParentIdOrderByCreateTime(parentId).stream()
					.map(StudentGuardianRelation::getStudentId).collect(Collectors.toSet());
			values = communications.findByStudentIdInOrderByOccurredAtDesc(studentIds);
		} else {
			EducationDataScope scope = scopes.resolve(username);
			values = communications.findAllByOrderByOccurredAtDesc().stream()
					.filter(item -> scope.fullAccess() || scopes.canAccessStudent(scope, item.getStudentId()))
					.toList();
		}
		boolean sensitive = canReadSensitive(username);
		return values.stream().map(value -> response(value, sensitive)).toList();
	}

	@Transactional(readOnly = true)
	public byte[] exportCommunications(String username) {
		if (!has(username, "education:home-school:communication:export"))
			throw new AccessDeniedException("无权导出沟通记录");
		List<CommunicationResponse> values = listCommunications(username);
		StringBuilder csv = new StringBuilder("id,studentId,classId,channel,subject,content,occurredAt\n");
		for (CommunicationResponse value : values) {
			csv.append(csv(value.id())).append(',').append(csv(value.studentId())).append(',')
					.append(csv(value.classId())).append(',').append(csv(value.channel())).append(',')
					.append(csv(value.subject())).append(',').append(csv(value.content())).append(',')
					.append(csv(String.valueOf(value.occurredAt()))).append('\n');
		}
		audit.log(username, "EDU_HOME_COMMUNICATION_EXPORT", "count=" + values.size());
		return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
	}

	private boolean allowed(String from, String to) {
		return switch (from) {
			case "SUBMITTED" -> Set.of("ACCEPTED", "ASSIGNED").contains(to);
			case "ACCEPTED" -> Set.of("ASSIGNED", "IN_PROGRESS").contains(to);
			case "ASSIGNED" -> "IN_PROGRESS".equals(to);
			case "IN_PROGRESS" -> Set.of("WAITING_SUPPLEMENT", "RESOLVED").contains(to);
			case "WAITING_SUPPLEMENT" -> "IN_PROGRESS".equals(to);
			case "REOPENED" -> Set.of("ASSIGNED", "IN_PROGRESS").contains(to);
			case "RESOLVED" -> "CLOSED".equals(to);
			default -> false;
		};
	}

	private ParentAccountBinding activeParent(String username) {
		ParentAccountBinding binding = bindings.findByUsernameAndStatus(username, "ACTIVE")
				.orElseThrow(() -> new AccessDeniedException("当前账号不是有效家长账号"));
		parents.findById(binding.getParentId())
				.filter(parent -> "ACTIVE".equals(parent.getStatus()))
				.orElseThrow(() -> new AccessDeniedException("家长档案已失效"));
		return binding;
	}

	private StudentProfile studentForParent(String parentId, String studentId) {
		if (!guardians.findByParentIdOrderByCreateTime(parentId).stream()
				.anyMatch(relation -> studentId.equals(relation.getStudentId())))
			throw new AccessDeniedException("该学生不是当前家长的被监护人");
		return students.findById(studentId).orElseThrow(() -> new IllegalArgumentException("学生不存在"));
	}

	private void assertStaffStudentAccess(String username, String studentId) {
		if (bindings.findByUsernameAndStatus(username, "ACTIVE").isPresent())
			throw new AccessDeniedException("家长账号不能处理反馈");
		EducationDataScope scope = scopes.resolve(username);
		scopes.assertStudentAccess(scope, studentId);
	}

	private boolean canReadSensitive(String username) {
		return has(username, "education:home-school:communication:sensitive");
	}

	private boolean has(String username, String authority) {
		return org.springframework.security.core.context.SecurityContextHolder.getContext()
				.getAuthentication() != null
				&& org.springframework.security.core.context.SecurityContextHolder.getContext()
						.getAuthentication().getAuthorities().stream()
						.anyMatch(item -> authority.equals(item.getAuthority()));
	}

	private FeedbackResponse response(ParentFeedback value) {
		return new FeedbackResponse(value.getId(), value.getParentId(), value.getStudentId(), value.getClassId(),
				value.getTitle(), value.getContent(), value.getStatus(), value.getAssignedTo(), value.getDueAt(),
				value.getDueAt() != null && value.getDueAt().isBefore(LocalDateTime.now())
						&& !Set.of("RESOLVED", "CLOSED").contains(value.getStatus()),
				value.getAcceptedAt(), value.getResolvedAt(), value.getClosedAt(),
				value.getParentConfirmedAt(), value.getReopenedAt());
	}

	private CommunicationResponse response(CommunicationRecord value, boolean sensitive) {
		return new CommunicationResponse(value.getId(), value.getTeacherUsername(), value.getStudentId(),
				value.getClassId(), value.getChannel(), value.getSubject(), value.getContent(),
				sensitive ? value.getSensitiveContent() : null, value.getOccurredAt());
	}

	private String csv(String value) {
		if (value == null) return "";
		return "\"" + value.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + "\"";
	}
	private boolean blank(String value) { return value == null || value.isBlank(); }
}
