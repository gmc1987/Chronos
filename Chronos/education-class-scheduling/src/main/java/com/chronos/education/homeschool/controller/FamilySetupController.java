package com.chronos.education.homeschool.controller;

import com.chronos.commons.model.PageView;
import com.chronos.commons.model.ResultData;
import com.chronos.education.homeschool.service.FamilyRosterService;
import com.chronos.education.scheduling.model.ParentProfile;
import com.chronos.education.scheduling.model.StudentGuardianRelation;
import com.chronos.education.scheduling.service.AcademicDataService;
import com.chronos.education.scheduling.service.SchoolRosterSetupService;
import com.chronos.model.vo.DictVO;
import com.chronos.service.iService.IDictService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/education/home-school/setup")
public class FamilySetupController {
	private static final String READ = "@iamAuthorization.any(authentication,'education:home-school:parent:view',"
			+ "'education:home-school:notice:view','education:home-school:group:view','education:home-school:feedback:view',"
			+ "'education:home-school:communication:view','education:parent-meeting:manage','education:home-school:guardian:review')";
	private static final String READ_PARENTS = "@iamAuthorization.any(authentication,'education:home-school:parent:view',"
			+ "'education:home-school:parent:manage','education:home-school:guardian:review')";
	private static final String MANAGE = "@iamAuthorization.has(authentication,'education:home-school:parent:manage')";
	private static final String REVIEW = "@iamAuthorization.has(authentication,'education:home-school:guardian:review')";
	private final FamilyRosterService setup;
	private final AcademicDataService academics;
	private final IDictService dictionaries;

	@PostMapping("/roster")
	@PreAuthorize(MANAGE)
	public ResultData<SchoolRosterSetupService.RosterResult> importRoster(@RequestParam String schoolId,
			@RequestBody List<SchoolRosterSetupService.RosterRow> rows, Authentication actor) {
		return ok(setup.importRoster(schoolId, rows, actor.getName()));
	}

	@PutMapping("/head-teacher")
	@PreAuthorize(MANAGE)
	public ResultData<Void> assignHeadTeacher(@RequestBody FamilyRosterService.HeadTeacherInput input, Authentication actor) {
		setup.assignHeadTeacher(input, actor.getName());
		return ok(null);
	}

	@GetMapping("/classes")
	@PreAuthorize(READ)
	public ResultData<List<FamilyRosterService.ClassOption>> classes(@RequestParam(required = false) String schoolId,
			Authentication actor) {
		return ok(setup.classOptions(schoolId, actor.getName()));
	}

	@GetMapping("/students")
	@PreAuthorize(READ)
	public ResultData<PageView<FamilyRosterService.StudentOption>> students(@RequestParam(required = false) String schoolId,
			@RequestParam(required = false) String classId, @RequestParam(required = false) String keyword,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, Authentication actor) {
		return ok(setup.studentOptions(schoolId, classId, keyword, page, size, actor.getName()));
	}

	@GetMapping("/dictionaries/{code}")
	@PreAuthorize(READ)
	public ResultData<List<DictVO>> dictionary(@PathVariable String code) {
		if (!Set.of("COMMON_GENDER", "EDU_GUARDIAN_RELATIONSHIP").contains(code)) {
			throw new AccessDeniedException("家校中心未开放该字典");
		}
		return ok(dictionaries.listByCode(code).stream().filter(item -> Integer.valueOf(1).equals(item.getStatus())).toList());
	}

	@GetMapping("/parents")
	@PreAuthorize(READ_PARENTS)
	public ResultData<PageView<ParentProfile>> parents(@RequestParam(required = false) String schoolId,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, Authentication actor) {
		return ok(setup.parentPage(schoolId, page, size, actor.getName()));
	}

	@GetMapping("/parent-options")
	@PreAuthorize(READ)
	public ResultData<List<ParentOption>> parentOptions(@RequestParam(required = false) String schoolId,
			Authentication actor) {
		return ok(setup.parentPage(schoolId, 0, 200, actor.getName()).content().stream()
				.map(value -> new ParentOption(value.getId(), value.getParentName())).toList());
	}

	@PostMapping("/parents")
	@PreAuthorize(MANAGE)
	public ResultData<ParentProfile> create(@RequestBody FamilyRosterService.ParentInput input, Authentication actor) {
		return ok(setup.saveParent(null, input, actor.getName()));
	}

	@PutMapping("/parents/{id}")
	@PreAuthorize(MANAGE)
	public ResultData<ParentProfile> edit(@PathVariable String id, @RequestBody FamilyRosterService.ParentInput input,
			Authentication actor) {
		return ok(setup.saveParent(id, input, actor.getName()));
	}

	@DeleteMapping("/parents/{id}")
	@PreAuthorize(MANAGE)
	public ResultData<Void> delete(@PathVariable String id, Authentication actor) {
		setup.deleteParent(id, actor.getName());
		return ok(null);
	}

	@GetMapping("/guardians")
	@PreAuthorize(READ_PARENTS)
	public ResultData<List<StudentGuardianRelation>> guardians(@RequestParam String parentId, Authentication actor) {
		return ok(setup.guardians(parentId, actor.getName()));
	}

	@PostMapping("/guardians")
	@PreAuthorize(MANAGE)
	public ResultData<StudentGuardianRelation> add(@RequestBody FamilyRosterService.GuardianInput input,
			Authentication actor) {
		return ok(setup.addGuardian(input, actor.getName()));
	}

	@DeleteMapping("/guardians/{id}")
	@PreAuthorize(MANAGE)
	public ResultData<Void> remove(@PathVariable String id, Authentication actor) {
		setup.deleteGuardian(id, actor.getName());
		return ok(null);
	}

	@PostMapping("/guardians/{id}/verification")
	@PreAuthorize(REVIEW)
	public ResultData<StudentGuardianRelation> verify(@PathVariable String id, @RequestBody Verification body,
			Authentication actor) {
		setup.assertGuardian(id, actor.getName());
		if (body.approved() == null) throw new IllegalArgumentException("请选择核验结果");
		return ok(academics.verifyGuardian(id, body.approved(), body.note(), actor.getName()));
	}

	@PostMapping("/guardians/{id}/consent")
	@PreAuthorize(REVIEW)
	public ResultData<StudentGuardianRelation> consent(@PathVariable String id, @RequestBody Consent body,
			Authentication actor) {
		setup.assertGuardian(id, actor.getName());
		return ok(academics.consentGuardian(id, body.status(), body.expiresAt()));
	}

	@PostMapping("/guardians/{id}/restriction")
	@PreAuthorize(REVIEW)
	public ResultData<StudentGuardianRelation> restrict(@PathVariable String id, @RequestBody Restriction body,
			Authentication actor) {
		setup.assertGuardian(id, actor.getName());
		return ok(academics.restrictGuardian(id, body.level(), body.reason(), body.from(), body.to()));
	}

	public record ParentOption(String id, String parentName) {}
	public record Verification(Boolean approved, String note) {}
	public record Consent(String status, LocalDateTime expiresAt) {}
	public record Restriction(String level, String reason, LocalDate from, LocalDate to) {}

	private static <T> ResultData<T> ok(T value) {
		return ResultData.<T>builder().code("200").msg("ok").data(value).build();
	}
}
