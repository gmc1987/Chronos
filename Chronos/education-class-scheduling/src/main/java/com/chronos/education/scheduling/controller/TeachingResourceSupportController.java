package com.chronos.education.scheduling.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.TeachingReviewRecord;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.education.scheduling.service.TeachingManagedFileAccessPolicy;
import com.chronos.education.scheduling.service.TeachingResourceSetupService;
import com.chronos.education.scheduling.service.TeachingReviewService;
import com.chronos.file.dao.ManagedFileRepository;
import com.chronos.file.model.ManagedFileView;
import com.chronos.file.service.ManagedFileService;
import com.chronos.model.vo.DictVO;
import com.chronos.service.iService.IDictService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/education/teaching-center/support")
public class TeachingResourceSupportController {
	private static final String READ = "@iamAuthorization.any(authentication,'education:teaching:view',"
			+ "'education:teaching:manage','education:teaching:plan:view','education:teaching:lesson:view',"
			+ "'education:teaching:preparation:view','education:teaching:material:view','education:teaching:research:view',"
			+ "'education:teaching:review')";
	private static final String REVIEW = "@iamAuthorization.has(authentication,'education:teaching:review')";
	private static final Set<String> DICTIONARIES = Set.of("COMMON_STATUS", "EDU_LESSON_TYPE", "EDU_TEACHING_PLAN_TYPE",
			"EDU_PREPARATION_TYPE", "EDU_MATERIAL_CATEGORY", "EDU_QUESTION_TYPE", "EDU_QUESTION_DIFFICULTY",
			"EDU_RESOURCE_CATEGORY", "EDU_RESOURCE_SHARE_SCOPE", "EDU_RESOURCE_SOURCE", "EDU_RESOURCE_VISIBILITY",
			"EDU_RESEARCH_ACTIVITY_TYPE", "EDU_RESEARCH_ATTENDANCE_STATUS", "EDU_RESEARCH_RESULT_TYPE", "EDU_RESEARCH_STATUS");
	private final IDictService dictionaries;
	private final CourseOfferingRepository offerings;
	private final EducationDataScopeService scopes;
	private final ManagedFileService files;
	private final ManagedFileRepository fileEntries;
	private final TeachingManagedFileAccessPolicy attachmentPolicy;

	private final TeachingResourceSetupService setup;
	private final TeachingReviewService reviews;
	private static final String MANAGE = "@iamAuthorization.has(authentication,'education:teaching:manage')";

	@GetMapping("/reviews")
	@PreAuthorize(REVIEW)
	public ResultData<List<TeachingReviewService.ReviewItem>> reviewQueue(Authentication actor) {
		return ok(reviews.productPending(actor));
	}

	@GetMapping("/reviews/status/{type}/{id}")
	@PreAuthorize(READ)
	public ResultData<TeachingReviewRecord> reviewStatus(@PathVariable String type, @PathVariable String id,
			Authentication actor) {
		return ok(reviews.productStatus(type, id, actor));
	}

	@PutMapping("/reviews/{id}/decision")
	@PreAuthorize(REVIEW)
	public ResultData<TeachingReviewRecord> reviewDecision(
			@PathVariable String id, @RequestBody ReviewDecision decision, Authentication actor) {
		return ok(reviews.decideProductReview(id, decision.approved(), decision.comment(), actor));
	}

	public record ReviewDecision(Boolean approved, String comment) {}

	@GetMapping("/setup")
	@PreAuthorize(MANAGE)
	public ResultData<TeachingResourceSetupService.Options> setupOptions(@RequestParam String schoolId, Authentication actor) {
		return ok(setup.options(schoolId, actor.getName()));
	}

	@PostMapping("/setup")
	@PreAuthorize(MANAGE)
	public ResultData<CourseOffering> setupOffering(@RequestParam String schoolId,
			@RequestBody TeachingResourceSetupService.Command command, Authentication actor) {
		return ok(setup.create(schoolId, command, actor.getName()));
	}

	@PostMapping("/setup/teachers")
	@PreAuthorize(MANAGE)
	public ResultData<TeachingResourceSetupService.Option> setupTeacher(@RequestParam String schoolId,
			@RequestBody TeachingResourceSetupService.Teacher command, Authentication actor) {
		return ok(setup.registerTeacher(schoolId, command, actor.getName()));
	}

	@PostMapping("/setup/accounts")
	@PreAuthorize(MANAGE)
	public ResultData<Void> setupTeacherAccount(@RequestParam String schoolId,
			@RequestBody TeachingResourceSetupService.TeacherLink command, Authentication actor) {
		setup.linkTeacher(schoolId, command, actor.getName());
		return ok(null);
	}

	@GetMapping("/dictionaries/{code}")
	@PreAuthorize(READ)
	public ResultData<List<DictVO>> dictionary(@PathVariable String code) {
		if (!DICTIONARIES.contains(code)) {
			throw new AccessDeniedException("教学资源未开放该字典");
		}
		return ok(dictionaries.listByCode(code).stream()
				.filter(item -> Integer.valueOf(1).equals(item.getStatus())).toList());
	}

	@GetMapping("/courses")
	@PreAuthorize(READ)
	public ResultData<List<CourseOption>> courses(Authentication actor) {
		var scope = scopes.resolve(actor.getName());
		var visible = scopes.visibleOfferings(scope, offerings.findAll()).stream()
				.filter(item -> !CourseOffering.GRADE_ONLY.equals(item.getStatus())
						&& !CourseOffering.HOMEWORK_ONLY.equals(item.getStatus()))
				.map(item -> new CourseOption(item.getCourseCode(), item.getCourseName()))
				.distinct()
				.sorted(java.util.Comparator.comparing(CourseOption::courseName).thenComparing(CourseOption::courseCode))
				.toList();
		return ok(visible);
	}

	@GetMapping("/offerings/{offeringId}/preparation-teachers")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:preparation:manage','education:teaching:manage')")
	public ResultData<List<TeachingResourceSetupService.Option>> preparationTeachers(
			@PathVariable String offeringId, Authentication actor) {
		return ok(setup.preparationTeachers(offeringId, actor.getName()));
	}

	@PostMapping(value = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:manage','education:teaching:update',"
			+ "'education:teaching:preparation:manage','education:teaching:material:manage','education:teaching:research:manage')")
	public ResultData<ManagedFileView> upload(@RequestPart("file") MultipartFile file, Authentication actor) {
		return ok(files.upload(file, "WORKFLOW_FORM_DRAFT", null, actor));
	}

	@GetMapping("/attachments/{id}")
	@PreAuthorize(READ)
	public ResponseEntity<byte[]> download(@PathVariable String id, Authentication actor) {
		var entry = fileEntries.findById(id)
				.filter(file -> "ACTIVE".equals(file.getStatus()) && "BOUND".equals(file.getBindState()))
				.filter(file -> attachmentPolicy.canRead(actor.getName(), file.getBusinessType(), file.getBusinessId()))
				.orElseThrow(() -> new AccessDeniedException("无权读取该教学资源附件"));
		// 先按教学对象判权，即使文件是本人上传，也不能绕过调岗或退出教研组后的限制。
		var content = files.read(entry.getId(), actor);
		return ResponseEntity.ok().contentType(MediaType.parseMediaType(content.contentType()))
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
						.filename(content.filename(), StandardCharsets.UTF_8).build().toString())
				.body(content.content());
	}

	public record CourseOption(String courseCode, String courseName) {}

	private static <T> ResultData<T> ok(T value) {
		return ResultData.<T>builder().code("200").msg("ok").data(value).build();
	}
}
