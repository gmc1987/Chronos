package com.chronos.education.internship.controller;

import static com.chronos.education.internship.dto.InternshipDtos.*;

import com.chronos.Idao.IOrganizationRepository;
import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.service.SchoolRosterSetupService;
import com.chronos.education.scheduling.service.AcademicDataService;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.dao.StudentProfileRepository;
import com.chronos.education.scheduling.model.AcademicTerm;
import com.chronos.education.scheduling.model.StudentProfile;
import com.chronos.file.service.ManagedFileService;
import com.chronos.file.model.ManagedFileView;
import com.chronos.education.internship.model.InternshipActivity;
import com.chronos.education.internship.model.InternshipAssessment;
import com.chronos.education.internship.model.InternshipCompany;
import com.chronos.education.internship.model.InternshipIncident;
import com.chronos.education.internship.model.InternshipPlacement;
import com.chronos.education.internship.model.InternshipPosition;
import com.chronos.education.internship.service.InternshipService;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

/** 高职实习实训的人工业务入口，审批和安全事件结案由独立权限控制。 */
@RestController
@RequestMapping("/admin/education/internships")
public class InternshipController {
	private final InternshipService service;
	private final SchoolRosterSetupService setup;
	private final IOrganizationRepository organizations;
	private final AcademicTermRepository terms;
	private final AcademicDataService academic;
	private final StudentProfileRepository students;
	private final EducationDataScopeService scopes;
	private final ManagedFileService files;

	public InternshipController(InternshipService service, SchoolRosterSetupService setup,
			IOrganizationRepository organizations, AcademicTermRepository terms, AcademicDataService academic,
			StudentProfileRepository students, EducationDataScopeService scopes, ManagedFileService files) {
		this.service = service;
		this.setup = setup;
		this.organizations = organizations;
		this.terms = terms;
		this.academic = academic;
		this.students = students;
		this.scopes = scopes;
		this.files = files;
	}

	@GetMapping("/terms")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:internship:view')")
	public ResultData<List<TermOption>> terms(@RequestParam String schoolId, Authentication actor) {
		service.assertSchool(actor.getName(), schoolId);
		return ok(terms.findByOrganizationIdOrderByStartDateDesc(schoolId).stream()
				.filter(term -> "ACTIVE".equals(term.getStatus()))
				.map(term -> new TermOption(term.getId(), term.getTermCode(), term.getTermName(),
						Boolean.TRUE.equals(term.getCurrentTerm())))
				.toList());
	}

	@PostMapping("/terms")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:internship:manage')")
	public ResultData<AcademicTerm> createTerm(@RequestParam String schoolId,
			@RequestBody SchoolRosterSetupService.TermCommand command, Authentication actor) {
		assertActiveSchool(schoolId, actor);
		return ok(setup.createTerm(schoolId, command));
	}

	@PostMapping("/roster")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:internship:manage')")
	public ResultData<SchoolRosterSetupService.RosterResult> importRoster(@RequestParam String schoolId,
			@RequestBody List<SchoolRosterSetupService.RosterRow> rows, Authentication actor) {
		assertActiveSchool(schoolId, actor);
		return ok(setup.importRoster(schoolId, rows));
	}

	@GetMapping("/student-options")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:internship:view')")
	public ResultData<List<StudentOption>> studentOptions(@RequestParam String schoolId,
			@RequestParam(required = false) String keyword, @RequestParam(required = false) String ids,
			Authentication actor) {
		service.assertSchool(actor.getName(), schoolId);
		var scope = scopes.resolve(actor.getName());
		if (ids != null) {
			var selected = Arrays.stream(ids.split(",")).filter(id -> !id.isBlank()).distinct().toList();
			if (selected.size() > 100) {
				throw new IllegalArgumentException("每次最多查询100名学生");
			}
			return ok(students.findAllById(selected).stream().map(student -> {
				scopes.assertStudentInSchool(scope, schoolId, student);
				scopes.assertStudentAccess(scope, student.getId());
				return StudentOption.from(student);
			}).toList());
		}
		var campuses = scopes.scheduleOrganizationCampuses(scope, schoolId);
		return ok(academic.scheduleStudentOptions(scope, keyword, 0, 10, campuses).stream()
				.filter(student -> "ACTIVE".equals(student.getEnrollmentStatus()))
				.map(StudentOption::from).toList());
	}

	@PostMapping(value = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("@iamAuthorization.has(authentication,'education:internship:manage')")
	public ResultData<ManagedFileView> uploadAttachment(@RequestParam String schoolId,
			@RequestPart("file") MultipartFile file, Authentication actor) {
		assertActiveSchool(schoolId, actor);
		return ok(files.upload(file, "WORKFLOW_FORM_DRAFT", null, actor));
	}

	@GetMapping("/placements/{id}/attachments/{fileId}")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:internship:view')")
	public ResponseEntity<byte[]> downloadAttachment(@PathVariable String id, @PathVariable String fileId,
			Authentication actor) {
		var file = service.downloadAttachment(id, fileId, actor);
		return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType()))
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
						.filename(file.filename(), StandardCharsets.UTF_8).build().toString())
				.body(file.content());
	}

	private void assertActiveSchool(String schoolId, Authentication actor) {
		service.assertSchool(actor.getName(), schoolId);
		if (organizations.findById(schoolId).filter(item -> "SCHOOL".equals(item.getOrganizationType())
				&& Integer.valueOf(1).equals(item.getStatus())).isEmpty()) {
			throw new IllegalArgumentException("请选择启用的学校");
		}
	}

	public record TermOption(String id, String termCode, String termName, boolean currentTerm) {}
	public record StudentOption(String id, String studentNo, String studentName) {
		private static StudentOption from(StudentProfile student) {
			return new StudentOption(student.getId(), student.getStudentNo(), student.getStudentName());
		}
	}

	@GetMapping("/companies")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:internship:view')")
	public ResultData<List<InternshipCompany>> companies(
			@RequestParam String schoolId,
			Authentication authentication) {
		return ok(service.companies(schoolId, authentication.getName()));
	}

	@PostMapping("/companies")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:internship:manage')")
	public ResultData<InternshipCompany> createCompany(
			@RequestBody CompanyCommand command,
			Authentication authentication) {
		return ok(service.createCompany(command, authentication.getName()));
	}

	@PostMapping("/companies/{id}/submit")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:internship:manage')")
	public ResultData<InternshipCompany> submitCompany(
			@PathVariable String id,
			Authentication authentication) {
		return ok(service.submitCompany(id, authentication.getName()));
	}

	@PostMapping("/companies/{id}/decision")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:internship:approve')")
	public ResultData<InternshipCompany> decideCompany(
			@PathVariable String id,
			@RequestBody DecisionCommand command,
			Authentication authentication) {
		return ok(service.decideCompany(id, command, authentication.getName()));
	}

	@GetMapping("/positions")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:internship:view')")
	public ResultData<List<InternshipPosition>> positions(
			@RequestParam String schoolId,
			Authentication authentication) {
		return ok(service.positions(schoolId, authentication.getName()));
	}

	@PostMapping("/positions")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:internship:manage')")
	public ResultData<InternshipPosition> createPosition(
			@RequestBody PositionCommand command,
			Authentication authentication) {
		return ok(service.createPosition(command, authentication.getName()));
	}

	@PostMapping("/positions/{id}/submit")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:internship:manage')")
	public ResultData<InternshipPosition> submitPosition(@PathVariable String id, Authentication actor) {
		return ok(service.submitPosition(id, actor.getName()));
	}

	@PostMapping("/positions/{id}/decision")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:internship:approve')")
	public ResultData<InternshipPosition> decidePosition(@PathVariable String id, @RequestBody DecisionCommand command,
			Authentication actor) {
		return ok(service.decidePosition(id, command, actor.getName()));
	}

	@GetMapping("/placements")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:internship:view')")
	public ResultData<List<InternshipPlacement>> placements(
			@RequestParam String schoolId,
			@RequestParam String termCode,
			Authentication authentication) {
		return ok(service.placements(schoolId, termCode, authentication.getName()));
	}

	@PostMapping("/placements")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:internship:manage')")
	public ResultData<InternshipPlacement> createPlacement(
			@RequestBody PlacementCommand command,
			Authentication authentication) {
		return ok(service.createPlacement(command, authentication.getName()));
	}

	@PostMapping("/placements/{id}/submit")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:internship:manage')")
	public ResultData<InternshipPlacement> submitPlacement(
			@PathVariable String id,
			Authentication authentication) {
		return ok(service.submitPlacement(id, authentication.getName()));
	}

	@PostMapping("/placements/{id}/decision")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:internship:approve')")
	public ResultData<InternshipPlacement> decidePlacement(
			@PathVariable String id,
			@RequestBody DecisionCommand command,
			Authentication authentication) {
		return ok(service.decidePlacement(id, command, authentication.getName()));
	}

	@PostMapping("/placements/{id}/activate")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:internship:manage')")
	public ResultData<InternshipPlacement> activatePlacement(
			@PathVariable String id,
			Authentication authentication) {
		return ok(service.activatePlacement(id, authentication.getName()));
	}

	@GetMapping("/placements/{id}/activities")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:internship:view')")
	public ResultData<List<InternshipActivity>> activities(
			@PathVariable String id,
			Authentication authentication) {
		return ok(service.activities(id, authentication.getName()));
	}

	@PostMapping("/placements/{id}/activities")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:internship:manage')")
	public ResultData<InternshipActivity> addActivity(
			@PathVariable String id,
			@RequestBody ActivityCommand command,
			Authentication authentication) {
		return ok(service.addActivity(id, command, authentication.getName()));
	}

	@GetMapping("/placements/{id}/assessment")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:internship:view')")
	public ResultData<InternshipAssessment> assessment(
			@PathVariable String id,
			Authentication authentication) {
		return ok(service.assessment(id, authentication.getName()));
	}

	@PostMapping("/placements/{id}/assessment")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:internship:manage')")
	public ResultData<InternshipAssessment> submitAssessment(
			@PathVariable String id,
			@RequestBody AssessmentCommand command,
			Authentication authentication) {
		return ok(service.submitAssessment(id, command, authentication.getName()));
	}

	@PostMapping("/assessments/{id}/decision")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:internship:approve')")
	public ResultData<InternshipAssessment> decideAssessment(
			@PathVariable String id,
			@RequestBody DecisionCommand command,
			Authentication authentication) {
		return ok(service.decideAssessment(id, command, authentication.getName()));
	}

	@GetMapping("/placements/{id}/incidents")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:internship:view')")
	public ResultData<List<InternshipIncident>> incidents(
			@PathVariable String id,
			Authentication authentication) {
		return ok(service.incidents(id, authentication.getName()));
	}

	@PostMapping("/placements/{id}/incidents")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:internship:safety')")
	public ResultData<InternshipIncident> reportIncident(
			@PathVariable String id,
			@RequestBody IncidentCommand command,
			Authentication authentication) {
		return ok(service.reportIncident(id, command, authentication.getName()));
	}

	@PostMapping("/incidents/{id}/close")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:internship:safety')")
	public ResultData<InternshipIncident> closeIncident(
			@PathVariable String id,
			@RequestBody ClosureCommand command,
			Authentication authentication) {
		return ok(service.closeIncident(id, command, authentication.getName()));
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("success").data(data).build();
	}
}
