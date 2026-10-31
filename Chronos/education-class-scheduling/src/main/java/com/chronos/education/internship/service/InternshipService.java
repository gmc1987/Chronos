package com.chronos.education.internship.service;

import static com.chronos.education.internship.dto.InternshipDtos.*;

import com.chronos.education.internship.dao.*;
import com.chronos.education.internship.model.*;
import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.dao.StudentProfileRepository;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.service.iService.IAuditLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 高职实习实训的正式业务状态机，所有批准动作均与提交人分离。 */
@Service
public class InternshipService {
	private final InternshipCompanyRepository companies;
	private final InternshipPositionRepository positions;
	private final InternshipPlacementRepository placements;
	private final InternshipActivityRepository activities;
	private final InternshipAssessmentRepository assessments;
	private final InternshipIncidentRepository incidents;
	private final StudentProfileRepository students;
	private final AcademicTermRepository terms;
	private final EducationDataScopeService dataScopes;
	private final IAuditLogService audit;
	private final ObjectMapper json;

	public InternshipService(
			InternshipCompanyRepository companies,
			InternshipPositionRepository positions,
			InternshipPlacementRepository placements,
			InternshipActivityRepository activities,
			InternshipAssessmentRepository assessments,
			InternshipIncidentRepository incidents,
			StudentProfileRepository students,
			AcademicTermRepository terms,
			EducationDataScopeService dataScopes,
			IAuditLogService audit,
			ObjectMapper json) {
		this.companies = companies;
		this.positions = positions;
		this.placements = placements;
		this.activities = activities;
		this.assessments = assessments;
		this.incidents = incidents;
		this.students = students;
		this.terms = terms;
		this.dataScopes = dataScopes;
		this.audit = audit;
		this.json = json;
	}

	@Transactional(readOnly = true)
	public List<InternshipCompany> companies(String schoolId, String actor) {
		assertSchool(actor, schoolId);
		return companies.findBySchoolIdOrderByCreateTimeDesc(schoolId);
	}

	@Transactional
	public InternshipCompany createCompany(CompanyCommand command, String actor) {
		assertSchool(actor, command.schoolId());
		require(command.companyCode(), "单位编码不能为空");
		require(command.companyName(), "单位名称不能为空");
		if (companies.existsBySchoolIdAndCompanyCode(command.schoolId(), command.companyCode().trim())) {
			throw new IllegalStateException("实习单位编码已存在");
		}
		String risk = upper(command.riskLevel());
		if (!Set.of("LOW", "MEDIUM", "HIGH").contains(risk)) {
			throw new IllegalArgumentException("单位风险等级无效");
		}
		InternshipCompany value = new InternshipCompany();
		value.setSchoolId(command.schoolId());
		value.setCompanyCode(command.companyCode().trim());
		value.setCompanyName(command.companyName().trim());
		value.setCreditCode(trim(command.creditCode()));
		value.setContactName(trim(command.contactName()));
		value.setContactPhone(trim(command.contactPhone()));
		value.setAddress(trim(command.address()));
		value.setRiskLevel(risk);
		value = companies.save(value);
		audit.log(actor, "INTERNSHIP_COMPANY_CREATE", "companyId=" + value.getId());
		return value;
	}

	@Transactional
	public InternshipCompany submitCompany(String id, String actor) {
		InternshipCompany value = companyForUpdate(id, actor);
		if (!Set.of("DRAFT", "REJECTED").contains(value.getStatus())) {
			throw new IllegalStateException("当前实习单位不可提交准入审核");
		}
		value.setStatus("SUBMITTED");
		value.setSubmittedBy(actor);
		value.setSubmittedAt(LocalDateTime.now());
		value.setDecisionComment(null);
		return companies.save(value);
	}

	@Transactional
	public InternshipCompany decideCompany(String id, DecisionCommand command, String actor) {
		InternshipCompany value = companyForUpdate(id, actor);
		if (!"SUBMITTED".equals(value.getStatus())) {
			throw new IllegalStateException("当前实习单位不可审核");
		}
		assertDifferent(actor, value.getSubmittedBy(), "实习单位提交人不能审核自己的准入申请");
		if (!command.approved()) {
			require(command.comment(), "驳回原因不能为空");
		}
		value.setStatus(command.approved() ? "APPROVED" : "REJECTED");
		value.setApprovedBy(actor);
		value.setApprovedAt(LocalDateTime.now());
		value.setDecisionComment(trim(command.comment()));
		audit.log(actor, "INTERNSHIP_COMPANY_DECIDE", "companyId=" + id + ",status=" + value.getStatus());
		return companies.save(value);
	}

	@Transactional(readOnly = true)
	public List<InternshipPosition> positions(String schoolId, String actor) {
		assertSchool(actor, schoolId);
		return positions.findBySchoolIdOrderByCreateTimeDesc(schoolId);
	}

	@Transactional
	public InternshipPosition createPosition(PositionCommand command, String actor) {
		assertSchool(actor, command.schoolId());
		InternshipCompany company = companies.findById(command.companyId())
				.orElseThrow(() -> new IllegalArgumentException("实习单位不存在"));
		if (!command.schoolId().equals(company.getSchoolId()) || !"APPROVED".equals(company.getStatus())) {
			throw new IllegalStateException("只能为本校已准入单位创建岗位");
		}
		if (command.capacity() == null || command.capacity() < 1) {
			throw new IllegalArgumentException("岗位容量必须大于零");
		}
		InternshipPosition value = new InternshipPosition();
		value.setSchoolId(command.schoolId());
		value.setCompanyId(company.getId());
		value.setPositionName(require(command.positionName(), "岗位名称不能为空"));
		value.setCapacity(command.capacity());
		value.setMentorName(trim(command.mentorName()));
		value.setMentorPhone(trim(command.mentorPhone()));
		value.setSafetyRequirements(trim(command.safetyRequirements()));
		return positions.save(value);
	}

	@Transactional(readOnly = true)
	public List<InternshipPlacement> placements(String schoolId, String termCode, String actor) {
		assertSchool(actor, schoolId);
		return placements.findBySchoolIdAndTermCodeOrderByCreateTimeDesc(schoolId, termCode);
	}

	@Transactional
	public InternshipPlacement createPlacement(PlacementCommand command, String actor) {
		assertSchool(actor, command.schoolId());
		dataScopes.assertStudentAccess(dataScopes.resolve(actor), command.studentId());
		terms.findByTermCode(command.termCode()).orElseThrow(() -> new IllegalArgumentException("学期不存在"));
		var student = students.findById(command.studentId())
				.orElseThrow(() -> new IllegalArgumentException("学生不存在"));
		if (!"ACTIVE".equals(student.getEnrollmentStatus())) {
			throw new IllegalStateException("学生当前学籍状态不可参加实习");
		}
		InternshipCompany company = companies.findById(command.companyId())
				.orElseThrow(() -> new IllegalArgumentException("实习单位不存在"));
		InternshipPosition position = positions.findById(command.positionId())
				.orElseThrow(() -> new IllegalArgumentException("实习岗位不存在"));
		if (!command.schoolId().equals(company.getSchoolId()) || !"APPROVED".equals(company.getStatus())
				|| !company.getId().equals(position.getCompanyId()) || !"ACTIVE".equals(position.getStatus())) {
			throw new IllegalStateException("实习单位或岗位当前不可用");
		}
		if (command.startDate() == null || command.endDate() == null || command.endDate().isBefore(command.startDate())) {
			throw new IllegalArgumentException("实习起止日期无效");
		}
		require(command.agreementFileId(), "必须绑定实习协议");
		require(command.consentFileId(), "必须绑定知情同意材料");
		require(command.insuranceFileId(), "必须绑定实习保险凭证");
		long occupied = placements.countByPositionIdAndStatusIn(position.getId(),
				List.of("SUBMITTED", "APPROVED", "ACTIVE"));
		if (occupied >= position.getCapacity()) {
			throw new IllegalStateException("实习岗位容量已满");
		}
		InternshipPlacement value = new InternshipPlacement();
		value.setSchoolId(command.schoolId());
		value.setTermCode(command.termCode());
		value.setStudentId(command.studentId());
		value.setCompanyId(company.getId());
		value.setPositionId(position.getId());
		value.setStartDate(command.startDate());
		value.setEndDate(command.endDate());
		value.setAgreementFileId(command.agreementFileId().trim());
		value.setConsentFileId(command.consentFileId().trim());
		value.setInsuranceFileId(command.insuranceFileId().trim());
		return placements.save(value);
	}

	@Transactional
	public InternshipPlacement submitPlacement(String id, String actor) {
		InternshipPlacement value = placementForUpdate(id, actor);
		if (!Set.of("DRAFT", "REJECTED").contains(value.getStatus())) {
			throw new IllegalStateException("当前实习安排不可提交");
		}
		String snapshot = write(Map.of(
				"studentId", value.getStudentId(), "companyId", value.getCompanyId(),
				"positionId", value.getPositionId(), "agreementFileId", value.getAgreementFileId(),
				"consentFileId", value.getConsentFileId(), "insuranceFileId", value.getInsuranceFileId(),
				"startDate", value.getStartDate(), "endDate", value.getEndDate()));
		value.setSnapshotJson(snapshot);
		value.setSnapshotHash(hash(snapshot));
		value.setStatus("SUBMITTED");
		value.setSubmittedBy(actor);
		value.setSubmittedAt(LocalDateTime.now());
		return placements.save(value);
	}

	@Transactional
	public InternshipPlacement decidePlacement(String id, DecisionCommand command, String actor) {
		InternshipPlacement value = placementForUpdate(id, actor);
		if (!"SUBMITTED".equals(value.getStatus())) {
			throw new IllegalStateException("当前实习安排不可审核");
		}
		assertDifferent(actor, value.getSubmittedBy(), "实习安排提交人不能审核自己的安排");
		if (!command.approved()) {
			require(command.comment(), "驳回原因不能为空");
		}
		value.setStatus(command.approved() ? "APPROVED" : "REJECTED");
		value.setApprovedBy(actor);
		value.setApprovedAt(LocalDateTime.now());
		value.setDecisionComment(trim(command.comment()));
		return placements.save(value);
	}

	@Transactional
	public InternshipPlacement activatePlacement(String id, String actor) {
		InternshipPlacement value = placementForUpdate(id, actor);
		if (!"APPROVED".equals(value.getStatus())) {
			throw new IllegalStateException("只有已批准实习安排可以开始");
		}
		value.setStatus("ACTIVE");
		return placements.save(value);
	}

	@Transactional
	public InternshipActivity addActivity(String placementId, ActivityCommand command, String actor) {
		InternshipPlacement placement = placement(placementId, actor);
		if (!Set.of("APPROVED", "ACTIVE").contains(placement.getStatus())) {
			throw new IllegalStateException("当前实习安排不可记录日志或巡查");
		}
		String type = upper(command.activityType());
		if (!Set.of("STUDENT_LOG", "SCHOOL_INSPECTION").contains(type)) {
			throw new IllegalArgumentException("过程记录类型无效");
		}
		InternshipActivity value = new InternshipActivity();
		value.setPlacementId(placementId);
		value.setActivityType(type);
		value.setActivityDate(command.activityDate() == null ? LocalDate.now() : command.activityDate());
		value.setContent(require(command.content(), "过程记录内容不能为空"));
		value.setResult(trim(command.result()));
		value.setRiskLevel(trim(command.riskLevel()));
		value.setStatus("HIGH".equals(upper(command.riskLevel())) ? "FOLLOW_UP" : "RECORDED");
		return activities.save(value);
	}

	@Transactional(readOnly = true)
	public List<InternshipActivity> activities(String placementId, String actor) {
		placement(placementId, actor);
		return activities.findByPlacementIdOrderByActivityDateDesc(placementId);
	}

	@Transactional
	public InternshipAssessment submitAssessment(String placementId, AssessmentCommand command, String actor) {
		InternshipPlacement placement = placement(placementId, actor);
		if (!Set.of("APPROVED", "ACTIVE").contains(placement.getStatus())) {
			throw new IllegalStateException("当前实习安排不可提交考核");
		}
		if (assessments.findByPlacementId(placementId).isPresent()) {
			throw new IllegalStateException("该实习安排已有考核记录");
		}
		validateScore(command.companyScore());
		validateScore(command.schoolScore());
		validateScore(command.finalScore());
		InternshipAssessment value = new InternshipAssessment();
		value.setPlacementId(placementId);
		value.setCompanyScore(command.companyScore());
		value.setSchoolScore(command.schoolScore());
		value.setFinalScore(command.finalScore());
		value.setComment(trim(command.comment()));
		value.setSubmittedBy(actor);
		return assessments.save(value);
	}

	@Transactional(readOnly = true)
	public InternshipAssessment assessment(String placementId, String actor) {
		placement(placementId, actor);
		return assessments.findByPlacementId(placementId).orElse(null);
	}

	@Transactional
	public InternshipAssessment decideAssessment(String id, DecisionCommand command, String actor) {
		InternshipAssessment value = assessments.findForUpdate(id)
				.orElseThrow(() -> new IllegalArgumentException("实习考核不存在"));
		InternshipPlacement placement = placement(value.getPlacementId(), actor);
		if (!"SUBMITTED".equals(value.getStatus())) {
			throw new IllegalStateException("当前实习考核不可审批");
		}
		assertDifferent(actor, value.getSubmittedBy(), "实习考核提交人不能审批自己的考核");
		value.setStatus(command.approved() ? "APPROVED" : "REJECTED");
		value.setApprovedBy(actor);
		value.setApprovedAt(LocalDateTime.now());
		if (command.approved()) {
			InternshipPlacement locked = placements.findForUpdate(placement.getId()).orElseThrow();
			locked.setStatus("COMPLETED");
			placements.save(locked);
		}
		return assessments.save(value);
	}

	@Transactional
	public InternshipIncident reportIncident(String placementId, IncidentCommand command, String actor) {
		placement(placementId, actor);
		String severity = upper(command.severity());
		if (!Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL").contains(severity)) {
			throw new IllegalArgumentException("安全事件等级无效");
		}
		InternshipIncident value = new InternshipIncident();
		value.setPlacementId(placementId);
		value.setOccurredAt(command.occurredAt() == null ? LocalDateTime.now() : command.occurredAt());
		value.setSeverity(severity);
		value.setDescription(require(command.description(), "安全事件描述不能为空"));
		value.setEmergencyAction(require(command.emergencyAction(), "应急处置不能为空"));
		value.setReportedBy(actor);
		value.setStatus(Set.of("HIGH", "CRITICAL").contains(severity) ? "INVESTIGATING" : "REPORTED");
		audit.log(actor, "INTERNSHIP_INCIDENT_REPORT", "placementId=" + placementId + ",severity=" + severity);
		return incidents.save(value);
	}

	@Transactional(readOnly = true)
	public List<InternshipIncident> incidents(String placementId, String actor) {
		placement(placementId, actor);
		return incidents.findByPlacementIdOrderByOccurredAtDesc(placementId);
	}

	@Transactional
	public InternshipIncident closeIncident(String id, ClosureCommand command, String actor) {
		InternshipIncident value = incidents.findForUpdate(id)
				.orElseThrow(() -> new IllegalArgumentException("实习安全事件不存在"));
		placement(value.getPlacementId(), actor);
		if ("CLOSED".equals(value.getStatus())) {
			return value;
		}
		assertDifferent(actor, value.getReportedBy(), "安全事件上报人不能独立关闭自己上报的事件");
		value.setClosureComment(require(command.comment(), "结案说明不能为空"));
		value.setClosedBy(actor);
		value.setClosedAt(LocalDateTime.now());
		value.setStatus("CLOSED");
		return incidents.save(value);
	}

	private InternshipCompany companyForUpdate(String id, String actor) {
		InternshipCompany value = companies.findForUpdate(id)
				.orElseThrow(() -> new IllegalArgumentException("实习单位不存在"));
		assertSchool(actor, value.getSchoolId());
		return value;
	}

	private InternshipPlacement placementForUpdate(String id, String actor) {
		InternshipPlacement value = placements.findForUpdate(id)
				.orElseThrow(() -> new IllegalArgumentException("实习安排不存在"));
		assertSchool(actor, value.getSchoolId());
		return value;
	}

	private InternshipPlacement placement(String id, String actor) {
		InternshipPlacement value = placements.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("实习安排不存在"));
		assertSchool(actor, value.getSchoolId());
		return value;
	}

	private void assertSchool(String actor, String schoolId) {
		dataScopes.assertSchoolAccess(dataScopes.resolve(actor), schoolId);
	}

	private void assertDifferent(String actor, String submitter, String message) {
		if (actor.equals(submitter)) {
			throw new AccessDeniedException(message);
		}
	}

	private String require(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(message);
		}
		return value.trim();
	}

	private String trim(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private String upper(String value) {
		return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
	}

	private void validateScore(BigDecimal value) {
		if (value == null || value.signum() < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0) {
			throw new IllegalArgumentException("实习考核分数必须在 0 到 100 之间");
		}
	}

	private String write(Object value) {
		try {
			return json.writeValueAsString(value);
		} catch (Exception exception) {
			throw new IllegalStateException("实习业务快照生成失败", exception);
		}
	}

	private String hash(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception exception) {
			throw new IllegalStateException("实习业务快照摘要生成失败", exception);
		}
	}
}
