package com.chronos.education.scheduling.service;

import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.chronos.education.scheduling.dao.AdmissionCandidateRepository;
import com.chronos.education.scheduling.dao.AdministrativeClassRepository;
import com.chronos.education.scheduling.dao.EducationGradeRepository;
import com.chronos.education.scheduling.dao.MajorRepository;
import com.chronos.education.scheduling.dao.StudentProfileRepository;
import com.chronos.education.scheduling.model.AdmissionCandidate;
import com.chronos.education.scheduling.model.EducationGrade;
import com.chronos.education.scheduling.model.StudentProfile;
import com.chronos.education.scheduling.model.dto.AdmissionDtos.EnrollmentCommand;
import com.chronos.education.scheduling.model.dto.AdmissionDtos.RegistrationCommand;
import com.chronos.education.scheduling.model.dto.AdmissionDtos.ReviewCommand;
import com.chronos.service.impl.BusinessNumberService;

/** 新生录取、资格复查、报到建档与教育主管部门学籍注册结果回填。 */
@Service
public class AdmissionService {
	private final AdmissionCandidateRepository candidates;
	private final AdministrativeClassRepository classes;
	private final EducationGradeRepository grades;
	private final MajorRepository majors;
	private final StudentProfileRepository students;
	private final BusinessNumberService businessNumbers;
	private final StudentAccountProvisioningService studentAccounts;

	public AdmissionService(AdmissionCandidateRepository candidates, AdministrativeClassRepository classes,
			EducationGradeRepository grades, MajorRepository majors, StudentProfileRepository students,
			BusinessNumberService businessNumbers, StudentAccountProvisioningService studentAccounts) {
		this.candidates = candidates; this.classes = classes; this.grades = grades; this.majors = majors;
		this.students = students; this.businessNumbers = businessNumbers; this.studentAccounts = studentAccounts;
	}

	public Page<AdmissionCandidate> list(String status, String keyword, int page, int size) {
		return candidates.search(normalize(status), normalize(keyword).toLowerCase(Locale.ROOT),
				PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
	}

	@Transactional
	public AdmissionCandidate save(AdmissionCandidate command) {
		validate(command);
		AdmissionCandidate value = command.getId() == null ? new AdmissionCandidate()
				: candidates.findById(command.getId()).orElseThrow(() -> new IllegalArgumentException("录取记录不存在"));
		if (value.getId() != null && !"ADMITTED".equals(value.getAdmissionStatus()))
			throw new IllegalStateException("已进入资格复查的录取记录不能直接修改");
		candidates.findByAdmissionYearAndCandidateNo(command.getAdmissionYear(), command.getCandidateNo())
				.filter(existing -> !existing.getId().equals(value.getId()))
				.ifPresent(existing -> { throw new IllegalArgumentException("同一入学年份的考生号已存在"); });
		value.setSourceBatchNo(command.getSourceBatchNo()); value.setSourceType(defaultText(command.getSourceType(), "PROVINCIAL_IMPORT"));
		value.setCandidateNo(command.getCandidateNo().trim()); value.setAdmissionNo(trim(command.getAdmissionNo()));
		value.setStudentName(command.getStudentName().trim()); value.setGender(trim(command.getGender())); value.setPhone(trim(command.getPhone()));
		value.setAdmissionYear(command.getAdmissionYear()); value.setEducationLevel(command.getEducationLevel().trim());
		value.setAdmittedMajorId(blankToNull(command.getAdmittedMajorId())); value.setPlannedClassId(blankToNull(command.getPlannedClassId()));
		return candidates.save(value);
	}

	@Transactional
	public AdmissionCandidate review(String id, ReviewCommand command, String operator) {
		AdmissionCandidate value = require(id);
		if (!"ADMITTED".equals(value.getAdmissionStatus())) throw new IllegalStateException("当前录取记录不能重复资格复查");
		if (!command.passed() && normalize(command.remark()).isEmpty()) throw new IllegalArgumentException("复查不通过时必须填写原因");
		value.setAdmissionStatus(command.passed() ? "VERIFIED" : "REJECTED");
		value.setReviewedBy(operator); value.setReviewedAt(LocalDateTime.now()); value.setReviewRemark(trim(command.remark()));
		return candidates.save(value);
	}

	/** Agent 分班工具只能为尚未复查的录取记录设置经过校验的拟分班。 */
	@Transactional
	public AdmissionCandidate assignPlannedClass(String id, String administrativeClassId) {
		AdmissionCandidate value = require(id);
		if (!"ADMITTED".equals(value.getAdmissionStatus())) {
			throw new IllegalStateException("只有待资格复查的新生可以调整拟分班");
		}
		var targetClass = classes.findById(administrativeClassId)
				.orElseThrow(() -> new IllegalArgumentException("拟分行政班不存在"));
		if (!"ACTIVE".equals(targetClass.getStatus())) throw new IllegalArgumentException("拟分行政班未启用");
		if (!value.getAdmissionYear().equals(targetClass.getGradeYear())) {
			throw new IllegalArgumentException("行政班入学年份与录取年份不一致");
		}
		if (value.getAdmittedMajorId() == null) throw new IllegalArgumentException("新生尚未确定录取专业");
		if (!value.getAdmittedMajorId().equals(targetClass.getMajorId())) {
			throw new IllegalArgumentException("行政班专业与录取专业不一致");
		}
		value.setPlannedClassId(targetClass.getId());
		return candidates.save(value);
	}

	@Transactional
	public AdmissionCandidate enroll(String id, EnrollmentCommand command) {
		AdmissionCandidate value = require(id);
		if (!"VERIFIED".equals(value.getAdmissionStatus())) throw new IllegalStateException("只有资格复查通过的新生才能报到建档");
		if (value.getStudentId() != null) throw new IllegalStateException("该录取记录已经生成学生档案");
		var targetClass = classes.findById(command.administrativeClassId())
				.orElseThrow(() -> new IllegalArgumentException("报到行政班不存在"));
		EducationGrade grade = grades.findById(targetClass.getGradeId())
				.orElseThrow(() -> new IllegalStateException("行政班未关联有效年级"));
		if (!value.getAdmissionYear().equals(targetClass.getGradeYear())) throw new IllegalArgumentException("行政班入学年份与录取年份不一致");
		if (value.getAdmittedMajorId() != null && !value.getAdmittedMajorId().equals(targetClass.getMajorId()))
			throw new IllegalArgumentException("行政班专业与录取专业不一致");
		StudentProfile student = new StudentProfile();
		student.setStudentNo(businessNumbers.nextStudentNumber(targetClass.getCampusId(), value.getAdmissionYear(), value.getEducationLevel()));
		student.setStudentName(value.getStudentName()); student.setGender(value.getGender()); student.setPhone(value.getPhone());
		student.setGradeYear(targetClass.getGradeYear()); student.setGradeId(targetClass.getGradeId());
		student.setMajorId(targetClass.getMajorId()); student.setAdministrativeClassId(targetClass.getId()); student.setEnrollmentStatus("ACTIVE");
		student.setCandidateNo(value.getCandidateNo()); student.setAdmissionNo(value.getAdmissionNo()); student.setRegistrationStatus("NOT_SUBMITTED");
		student = students.save(student);
		studentAccounts.provision(student, targetClass.getCampusId());
		value.setStudentId(student.getId()); value.setPlannedClassId(targetClass.getId()); value.setCheckedInAt(LocalDateTime.now());
		value.setAdmissionStatus("ENROLLED"); return candidates.save(value);
	}

	@Transactional
	public AdmissionCandidate updateRegistration(String id, RegistrationCommand command) {
		AdmissionCandidate value = require(id);
		if (!"ENROLLED".equals(value.getAdmissionStatus())) throw new IllegalStateException("新生尚未报到建档，不能登记学籍注册结果");
		String status = normalize(command.status());
		if (!java.util.Set.of("PENDING", "REGISTERED", "REJECTED").contains(status)) throw new IllegalArgumentException("不支持的学籍注册状态");
		if ("REGISTERED".equals(status) && normalize(command.nationalStudentNo()).isEmpty())
			throw new IllegalArgumentException("注册成功时必须填写国家学籍号或学籍标识");
		if ("REJECTED".equals(status) && normalize(command.errorMessage()).isEmpty())
			throw new IllegalArgumentException("注册失败时必须填写退回原因");
		value.setRegistrationStatus(status); value.setNationalStudentNo(blankToNull(command.nationalStudentNo()));
		value.setRegistrationError(blankToNull(command.errorMessage())); value.setRegisteredAt("REGISTERED".equals(status) ? LocalDateTime.now() : null);
		StudentProfile student = students.findById(value.getStudentId()).orElseThrow(() -> new IllegalStateException("关联学生档案不存在"));
		// 兼容升级前已经完成报到、尚未建立账号绑定的学生记录。
		studentAccounts.provision(student, classes.findById(student.getAdministrativeClassId())
				.map(item -> item.getCampusId()).orElse(null));
		student.setRegistrationStatus(status); student.setNationalStudentNo(value.getNationalStudentNo()); students.save(student);
		return candidates.save(value);
	}

	private AdmissionCandidate require(String id) { return candidates.findById(id).orElseThrow(() -> new IllegalArgumentException("录取记录不存在")); }
	private void validate(AdmissionCandidate value) {
		if (normalize(value.getSourceBatchNo()).isEmpty()) throw new IllegalArgumentException("来源批次不能为空");
		if (normalize(value.getCandidateNo()).isEmpty()) throw new IllegalArgumentException("考生号不能为空");
		if (normalize(value.getStudentName()).isEmpty()) throw new IllegalArgumentException("学生姓名不能为空");
		if (value.getAdmissionYear() == null) throw new IllegalArgumentException("入学年份不能为空");
		if (normalize(value.getEducationLevel()).isEmpty()) throw new IllegalArgumentException("培养层次不能为空");
		if (value.getAdmittedMajorId() != null && !value.getAdmittedMajorId().isBlank()) majors.findById(value.getAdmittedMajorId())
				.orElseThrow(() -> new IllegalArgumentException("录取专业不存在"));
		if (value.getPlannedClassId() != null && !value.getPlannedClassId().isBlank()) classes.findById(value.getPlannedClassId())
				.orElseThrow(() -> new IllegalArgumentException("拟分行政班不存在"));
	}
	private String defaultText(String value, String fallback) { return normalize(value).isEmpty() ? fallback : value.trim(); }
	private String blankToNull(String value) { return normalize(value).isEmpty() ? null : value.trim(); }
	private String trim(String value) { return value == null ? null : value.trim(); }
	private String normalize(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT); }
}
