package com.chronos.education.scheduling.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.ScheduleEntryRepository;
import com.chronos.education.scheduling.dao.SchedulingAgentProposalRepository;
import com.chronos.education.scheduling.dao.TeacherAcademicProfileRepository;
import com.chronos.education.scheduling.dao.TeacherTimeConstraintRepository;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.ScheduleEntry;
import com.chronos.education.scheduling.model.SchedulingAgentProposal;
import com.chronos.education.scheduling.model.TeacherAcademicProfile;
import com.chronos.education.scheduling.model.TeacherTimeConstraint;
import com.chronos.service.iService.IAuditLogService;

/** 受控教务 Agent：只生成可审查草稿，只有确认接口能够写入正式约束。 */
@Service
public class EducationAgentService {
	private static final Pattern PERIOD_PATTERN = Pattern.compile("第?([一二三四五六七八九十\\d]+)节");
	private static final Map<String, Integer> DAYS = Map.ofEntries(
			Map.entry("星期一", 1), Map.entry("周一", 1),
			Map.entry("星期二", 2), Map.entry("周二", 2),
			Map.entry("星期三", 3), Map.entry("周三", 3),
			Map.entry("星期四", 4), Map.entry("周四", 4),
			Map.entry("星期五", 5), Map.entry("周五", 5),
			Map.entry("星期六", 6), Map.entry("周六", 6),
			Map.entry("星期日", 7), Map.entry("星期天", 7), Map.entry("周日", 7));

	private final SchedulingAgentProposalRepository proposals;
	private final TeacherAcademicProfileRepository teachers;
	private final TeacherTimeConstraintRepository constraints;
	private final CourseOfferingRepository offerings;
	private final ScheduleEntryRepository entries;
	private final EducationDataScopeService dataScopes;
	private final IAuditLogService auditLogService;

	public EducationAgentService(
			SchedulingAgentProposalRepository proposals,
			TeacherAcademicProfileRepository teachers,
			TeacherTimeConstraintRepository constraints,
			CourseOfferingRepository offerings,
			ScheduleEntryRepository entries,
			EducationDataScopeService dataScopes,
			IAuditLogService auditLogService) {
		this.proposals = proposals;
		this.teachers = teachers;
		this.constraints = constraints;
		this.offerings = offerings;
		this.entries = entries;
		this.dataScopes = dataScopes;
		this.auditLogService = auditLogService;
	}

	public List<SchedulingAgentProposal> proposals(
			String semesterCode,
			Authentication authentication) {
		var scope = dataScopes.resolve(authentication.getName());
		return proposals.findBySemesterCodeOrderByCreateTimeDesc(
				required(semesterCode, "学期编码")).stream()
				.filter(proposal -> dataScopes.canAccessTeacher(scope, proposal.getTeacherId()))
				.toList();
	}

	@Transactional
	public SchedulingAgentProposal propose(
			ProposalCommand command,
			Authentication authentication) {
		String semesterCode = required(command.semesterCode(), "学期编码");
		String requestText = required(command.requestText(), "自然语言约束");
		var scope = dataScopes.resolve(authentication.getName());
		TeacherAcademicProfile teacher = resolveTeacher(requestText, scope);
		Integer dayOfWeek = resolveDay(requestText);
		Integer periodNo = resolvePeriod(requestText);
		String constraintType = requestText.contains("优先") || requestText.contains("尽量")
				? "PREFERRED"
				: "FORBIDDEN";
		SchedulingAgentProposal proposal = new SchedulingAgentProposal();
		proposal.setSemesterCode(semesterCode);
		proposal.setRequestText(requestText);
		proposal.setTeacherId(teacher.getId());
		proposal.setTeacherName(teacher.getTeacherName());
		proposal.setDayOfWeek(dayOfWeek);
		proposal.setPeriodNo(periodNo);
		proposal.setConstraintType(constraintType);
		proposal.setReason(requestText);
		proposal.setStatus("DRAFT");
		proposal = proposals.save(proposal);
		audit(authentication, "SCHEDULING_AGENT_PROPOSE", "proposalId=" + proposal.getId());
		return proposal;
	}

	@Transactional
	public SchedulingAgentProposal confirm(String id, Authentication authentication) {
		// 对草稿加行锁，避免两个管理员并发确认时重复生成正式约束。
		SchedulingAgentProposal proposal = proposals.findLockedById(id)
				.orElseThrow(() -> new IllegalArgumentException("排课建议不存在"));
		dataScopes.assertTeacherAccess(
				dataScopes.resolve(authentication.getName()),
				proposal.getTeacherId());
		if (!"DRAFT".equals(proposal.getStatus())) {
			throw new IllegalStateException("只有草稿状态的建议可以确认");
		}
		TeacherTimeConstraint constraint = new TeacherTimeConstraint();
		constraint.setSemesterCode(proposal.getSemesterCode());
		constraint.setTeacherId(proposal.getTeacherId());
		constraint.setDayOfWeek(proposal.getDayOfWeek());
		constraint.setPeriodNo(proposal.getPeriodNo());
		constraint.setConstraintType(proposal.getConstraintType());
		constraint.setWeight("FORBIDDEN".equals(proposal.getConstraintType()) ? 100 : 10);
		constraint.setReason("排课 Agent 建议，经人工确认：" + proposal.getReason());
		constraint = constraints.save(constraint);
		proposal.setStatus("CONFIRMED");
		proposal.setConfirmedBy(authentication.getName());
		proposal.setConfirmedAt(LocalDateTime.now());
		proposal.setAppliedConstraintId(constraint.getId());
		proposal = proposals.save(proposal);
		audit(authentication, "SCHEDULING_AGENT_CONFIRM", "proposalId=" + proposal.getId());
		return proposal;
	}

	@Transactional
	public SchedulingAgentProposal reject(String id, Authentication authentication) {
		// 确认与驳回竞争同一把行锁，最终只能有一个状态迁移成功。
		SchedulingAgentProposal proposal = proposals.findLockedById(id)
				.orElseThrow(() -> new IllegalArgumentException("排课建议不存在"));
		dataScopes.assertTeacherAccess(
				dataScopes.resolve(authentication.getName()),
				proposal.getTeacherId());
		if (!"DRAFT".equals(proposal.getStatus())) {
			throw new IllegalStateException("只有草稿状态的建议可以驳回");
		}
		proposal.setStatus("REJECTED");
		proposal.setConfirmedBy(authentication.getName());
		proposal.setConfirmedAt(LocalDateTime.now());
		proposal = proposals.save(proposal);
		audit(authentication, "SCHEDULING_AGENT_REJECT", "proposalId=" + proposal.getId());
		return proposal;
	}

	/** 教务 Agent 仅执行只读工具查询，返回教师负荷与已存在的硬约束冲突。 */
	@Transactional
	public AcademicAnalysis analyze(String semesterCode, Authentication authentication) {
		String semester = required(semesterCode, "学期编码");
		var scope = dataScopes.resolve(authentication.getName());
		List<CourseOffering> semesterOfferings = dataScopes.visibleOfferings(
				scope,
				offerings.findBySemesterCodeOrderByOfferingCode(semester));
		Set<String> visibleOfferingIds = semesterOfferings.stream()
				.map(CourseOffering::getId)
				.collect(java.util.stream.Collectors.toSet());
		List<ScheduleEntry> semesterEntries = entries
				.findBySemesterCodeOrderByDayOfWeekAscPeriodNoAsc(semester).stream()
				.filter(entry -> visibleOfferingIds.contains(entry.getOfferingId()))
				.toList();
		Map<String, TeacherLoad> loads = new LinkedHashMap<>();
		for (CourseOffering offering : semesterOfferings) {
			TeacherLoad previous = loads.get(offering.getTeacherId());
			int lessons = offering.getWeeklyLessons() == null ? 0 : offering.getWeeklyLessons();
			loads.put(offering.getTeacherId(), new TeacherLoad(
					offering.getTeacherId(),
					offering.getTeacherName(),
					(previous == null ? 0 : previous.weeklyLessons()) + lessons,
					0,
					false));
		}
		Map<String, Integer> maxLessons = new LinkedHashMap<>();
		for (TeacherAcademicProfile teacher : teachers.findAllByOrderByTeacherNo().stream()
				.filter(value -> dataScopes.canAccessTeacher(scope, value.getId()))
				.toList()) {
			maxLessons.put(teacher.getId(), teacher.getMaxWeeklyLessons());
		}
		List<TeacherLoad> teacherLoads = loads.values().stream()
				.map(load -> {
					int maximum = maxLessons.getOrDefault(load.teacherId(), 20);
					return new TeacherLoad(
							load.teacherId(), load.teacherName(), load.weeklyLessons(),
							maximum, load.weeklyLessons() > maximum);
				})
				.sorted(Comparator.comparingInt(TeacherLoad::weeklyLessons).reversed())
				.toList();
		List<ConstraintViolation> violations = new ArrayList<>();
		Map<String, CourseOffering> offeringMap = new LinkedHashMap<>();
		semesterOfferings.forEach(item -> offeringMap.put(item.getId(), item));
		for (ScheduleEntry entry : semesterEntries) {
			CourseOffering offering = offeringMap.get(entry.getOfferingId());
			if (offering == null) {
				continue;
			}
			boolean forbidden = constraints.findBySemesterCodeAndTeacherId(semester, offering.getTeacherId())
					.stream()
					.anyMatch(item -> "FORBIDDEN".equals(item.getConstraintType())
							&& item.getDayOfWeek().equals(entry.getDayOfWeek())
							&& item.getPeriodNo() >= entry.getPeriodNo()
							&& item.getPeriodNo() < entry.getPeriodNo() + entry.getDurationPeriods());
			if (forbidden) {
				violations.add(new ConstraintViolation(
						entry.getId(), offering.getTeacherName(), entry.getDayOfWeek(), entry.getPeriodNo(),
						"课表命中教师禁排时间"));
			}
		}
		audit(authentication, "ACADEMIC_AGENT_ANALYZE", "semesterCode=" + semester);
		return new AcademicAnalysis(
				semester,
				semesterOfferings.size(),
				semesterEntries.size(),
				teacherLoads,
				violations);
	}

	private TeacherAcademicProfile resolveTeacher(
			String requestText,
			com.chronos.education.scheduling.model.EducationDataScope scope) {
		return teachers.findAllByOrderByTeacherNo().stream()
				.filter(item -> dataScopes.canAccessTeacher(scope, item.getId()))
				.filter(item -> requestText.contains(item.getTeacherName())
						|| requestText.contains(item.getTeacherNo()))
				.max(Comparator.comparingInt(item -> item.getTeacherName().length()))
				.orElseThrow(() -> new IllegalArgumentException("未识别到教师姓名或工号"));
	}

	private Integer resolveDay(String requestText) {
		return DAYS.entrySet().stream()
				.filter(entry -> requestText.contains(entry.getKey()))
				.map(Map.Entry::getValue)
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("未识别到星期，请使用星期一或周一等表达"));
	}

	private Integer resolvePeriod(String requestText) {
		Matcher matcher = PERIOD_PATTERN.matcher(requestText);
		if (!matcher.find()) {
			throw new IllegalArgumentException("未识别到节次，请使用第1节等表达");
		}
		return chineseNumber(matcher.group(1));
	}

	private int chineseNumber(String value) {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException ignored) {
			List<String> values = List.of("一", "二", "三", "四", "五", "六", "七", "八", "九", "十");
			int result = values.indexOf(value) + 1;
			if (result < 1) {
				throw new IllegalArgumentException("无法识别节次：" + value);
			}
			return result;
		}
	}

	private void audit(Authentication authentication, String action, String detail) {
		String username = authentication == null ? "UNKNOWN" : authentication.getName();
		auditLogService.log(username, action, detail);
	}

	private String required(String value, String label) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(label + "不能为空");
		}
		return value.trim();
	}

	public record ProposalCommand(String semesterCode, String requestText) {
	}

	public record TeacherLoad(
			String teacherId,
			String teacherName,
			int weeklyLessons,
			int maxWeeklyLessons,
			boolean overloaded) {
	}

	public record ConstraintViolation(
			String scheduleEntryId,
			String teacherName,
			Integer dayOfWeek,
			Integer periodNo,
			String reason) {
	}

	public record AcademicAnalysis(
			String semesterCode,
			int offeringCount,
			int scheduleEntryCount,
			List<TeacherLoad> teacherLoads,
			List<ConstraintViolation> constraintViolations) {
	}
}
