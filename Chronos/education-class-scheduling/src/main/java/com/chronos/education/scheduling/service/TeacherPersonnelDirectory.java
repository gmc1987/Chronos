package com.chronos.education.scheduling.service;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.IEmployeeRepository;
import com.chronos.Idao.IJobLevelRepository;
import com.chronos.Idao.IOrganizationRepository;
import com.chronos.Idao.IOrganizationUnitRepository;
import com.chronos.Idao.IPositionRepository;
import com.chronos.education.scheduling.dao.TeacherAcademicProfileRepository;
import com.chronos.model.dto.EmployeeAssignmentDTO;
import com.chronos.model.dto.OrganizationUnitDTO;
import com.chronos.model.pojo.Employee;
import com.chronos.model.pojo.JobLevel;
import com.chronos.model.pojo.Organization;
import com.chronos.model.pojo.Position;
import com.chronos.security.DirectoryDataScopeGuard;
import com.chronos.service.iService.IAuditLogService;
import com.chronos.service.iService.IIamDirectoryService;
import com.chronos.service.iService.IRefreshTokenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TeacherPersonnelDirectory {
	private final DirectoryDataScopeGuard scope;
	private final IIamDirectoryService directory;
	private final IOrganizationRepository organizations;
	private final IOrganizationUnitRepository units;
	private final IPositionRepository positions;
	private final IJobLevelRepository levels;
	private final IEmployeeRepository employees;
	private final IAdminUserRepository users;
	private final TeacherAcademicProfileRepository teachers;
	private final EducationResourceTransactionLock locks;
	private final PasswordEncoder encoder;
	private final IRefreshTokenService tokens;
	private final IAuditLogService audit;

	public record NamedCode(@NotBlank @Size(max = 48) String code, @NotBlank @Size(max = 100) String name) {}
	public record AssignmentInput(@NotBlank String schoolId, @NotNull @Valid NamedCode department,
			@NotNull @Valid NamedCode position, @Valid NamedCode level, @NotNull LocalDate effectiveFrom) {}
	public record Onboard(@NotBlank @Size(max = 100) String name, @NotNull @Valid AssignmentInput assignment) {}
	public record Transfer(@NotBlank String sourceSchoolId, @NotBlank @Size(max = 64) String employeeCode,
			@NotNull @Valid AssignmentInput assignment) {}
	public record Handover(@NotBlank @Size(min = 12, max = 64)
			@Pattern(regexp = "(?=.*[A-Z])(?=.*[a-z])(?=.*[0-9]).+") String password) {}
	public record EmployeeOption(String employeeCode, String employeeName) {}
	public record Structure(List<NamedCode> departments, List<NamedCode> positions, List<NamedCode> levels) {}

	@Transactional
	public EmployeeOption prepareOnboard(Onboard input, Authentication actor) {
		String school = input.assignment().schoolId();
		requireSchool(school, actor);
		var employee = new Employee();
		employee.setEmployeeName(input.name().strip());
		employee.setEmployeeType("TEACHER");
		employee.setHireDate(input.assignment().effectiveFrom());
		employee.setNumberingOrganizationId(school);
		var saved = directory.saveEmployee(employee);
		saveAssignment(saved.getId(), input.assignment(), true);
		audit.log(actor.getName(), "EDU_TEACHER_PERSONNEL_PREPARE_ONBOARD", "employeeId=" + saved.getId());
		return new EmployeeOption(saved.getEmployeeCode(), saved.getEmployeeName());
	}

	@Transactional
	public void prepareTransfer(Transfer input, Authentication actor) {
		requireSchool(input.sourceSchoolId(), actor);
		requireSchool(input.assignment().schoolId(), actor);
		if (input.sourceSchoolId().equals(input.assignment().schoolId())) {
			throw new IllegalArgumentException("调入学校须不同于当前学校");
		}
		var teacher = teachers.findByTeacherNo(input.employeeCode())
				.filter(item -> input.sourceSchoolId().equals(item.getDepartmentId()))
				.orElseThrow(() -> new AccessDeniedException("无权办理该教师的调动"));
		saveAssignment(teacher.getEmployeeId(), input.assignment(), false);
		audit.log(actor.getName(), "EDU_TEACHER_PERSONNEL_PREPARE_TRANSFER", "teacherId=" + teacher.getId());
	}

	private void saveAssignment(String employeeId, AssignmentInput input, boolean primary) {
		String school = input.schoolId();
		// Serialize additions to the same school's directory so matching codes can be reused.
		locks.lockSemester("teacher-personnel-directory:" + school);
		var command = new EmployeeAssignmentDTO();
		command.setEmployeeId(employeeId);
		command.setOrganizationId(school);
		command.setDepartmentId(units.findByOrgIdAndOrganizationUnitCode(school, input.department().code().strip())
				.map(unit -> existingName(unit.getId(), unit.getOrganizationUnitName(), input.department()))
				.orElseGet(() -> {
					var value = new OrganizationUnitDTO();
					value.setOrganizationId(school);
					value.setDepartmentCode(input.department().code().strip());
					value.setDepartmentName(input.department().name().strip());
					return directory.saveOrganizationUnit(value).getId();
				}));
		command.setPositionId(positions.findByOrganizationIdAndPositionCode(school, input.position().code().strip())
				.map(position -> existingName(position.getId(), position.getPositionName(), input.position()))
				.orElseGet(() -> {
					var value = new Position();
					value.setOrganizationId(school);
					value.setPositionCode(input.position().code().strip());
					value.setPositionName(input.position().name().strip());
					return directory.savePosition(value).getId();
				}));
		if (input.level() != null) {
			command.setJobLevelId(levels.findByOrganizationIdAndLevelCode(school, input.level().code().strip())
					.map(level -> existingName(level.getId(), level.getLevelName(), input.level()))
					.orElseGet(() -> {
						var value = new JobLevel();
						value.setOrganizationId(school);
						value.setLevelCode(input.level().code().strip());
						value.setLevelName(input.level().name().strip());
						return directory.saveJobLevel(value).getId();
					}));
		}
		command.setPrimaryAssignment(primary);
		command.setEffectiveFrom(input.effectiveFrom());
		directory.saveAssignment(command);
	}

	private String existingName(String id, String existing, NamedCode input) {
		if (!existing.equals(input.name().strip())) {
			throw new IllegalArgumentException("编码 " + input.code() + " 已用于「" + existing + "」，请核对名称");
		}
		return id;
	}

	@Transactional
	public void handover(String school, String code, Handover input, Authentication actor) {
		requireSchool(school, actor);
		var teacher = teachers.findByTeacherNo(code)
				.filter(item -> school.equals(item.getDepartmentId()) && "ACTIVE".equals(item.getEmploymentStatus()))
				.orElseThrow(() -> new AccessDeniedException("仅可办理本校在岗教师的账号交接"));
		locks.lockSemester("teacher-personnel:" + teacher.getEmployeeId());
		var employee = employees.findById(teacher.getEmployeeId()).orElseThrow();
		var account = users.findByEmployeeId(employee.getId()).orElseThrow();
		var initialRoles = Set.of("ROLE_PLATFORM_USER", "EDU_TEACHER", "TEACHER");
		if (!code.equals(account.getUsername()) || !Boolean.TRUE.equals(account.getMustChangePassword())
				|| !Integer.valueOf(1).equals(account.getStatus()) || !("ACTIVE".equals(employee.getEmploymentStatus()))
				|| account.getRoles().stream().anyMatch(role -> !initialRoles.contains(role.getRoleCode()))) {
			throw new AccessDeniedException("仅可交接尚未完成首次改密的普通教师账号");
		}
		account.setPassword(encoder.encode(input.password()));
		account.setPasswordChangedAt(LocalDateTime.now());
		account.setTokenVersion((account.getTokenVersion() == null ? 0 : account.getTokenVersion()) + 1);
		users.save(account);
		tokens.revokeAll(account.getUsername());
		audit.log(actor.getName(), "EDU_TEACHER_PERSONNEL_CREDENTIAL_HANDOVER", "teacherId=" + teacher.getId());
	}

	@Transactional(readOnly = true)
	public Structure structure(String school, Authentication actor) {
		requireSchool(school, actor);
		var departments = units.findByOrgIdAndStatusOrderBySortOrderAsc(school, 1).stream()
				.map(unit -> new NamedCode(unit.getOrganizationUnitCode(), unit.getOrganizationUnitName())).toList();
		var jobs = positions.findByOrganizationIdOrderBySortOrderAsc(school).stream()
				.filter(position -> Integer.valueOf(1).equals(position.getStatus()))
				.map(position -> new NamedCode(position.getPositionCode(), position.getPositionName())).toList();
		var ranks = levels.findByOrganizationIdOrderBySortOrderAsc(school).stream()
				.filter(level -> Integer.valueOf(1).equals(level.getStatus()))
				.map(level -> new NamedCode(level.getLevelCode(), level.getLevelName())).toList();
		return new Structure(departments, jobs, ranks);
	}

	@Transactional(readOnly = true)
	public Page<EmployeeOption> employees(String school, String keyword, int page, Authentication actor) {
		requireSchool(school, actor);
		return directory.pageEmployees(school, keyword, PageRequest.of(Math.max(0, page), 20))
				.map(employee -> new EmployeeOption(employee.getEmployeeCode(), employee.getEmployeeName()));
	}

	public String schoolCode(String school, Authentication actor) {
		return requireSchool(school, actor).getOrgCode();
	}

	Organization requireSchool(String school, Authentication actor) {
		scope.requireOrganization(actor, school);
		return organizations.findById(school).filter(item -> "SCHOOL".equals(item.getOrganizationType())
				&& Integer.valueOf(1).equals(item.getStatus()))
				.orElseThrow(() -> new IllegalArgumentException("请选择启用的学校"));
	}
}
