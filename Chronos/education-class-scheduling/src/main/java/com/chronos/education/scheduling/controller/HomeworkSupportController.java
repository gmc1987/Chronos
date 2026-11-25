package com.chronos.education.scheduling.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.education.grade.service.GradeRosterSetupService;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.ErrorItem;
import com.chronos.education.scheduling.service.HomeworkManagedFileAccessPolicy;
import com.chronos.education.scheduling.service.HomeworkMistakeService;
import com.chronos.education.scheduling.service.HomeworkService;
import com.chronos.education.scheduling.service.SchoolAssessmentIdentityService;
import com.chronos.file.dao.ManagedFileRepository;
import com.chronos.file.model.ManagedFileView;
import com.chronos.file.service.ManagedFileService;
import com.chronos.security.IamAuthorization;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/education/teaching-center/homework-support")
public class HomeworkSupportController {
	private static final String MANAGE = "@iamAuthorization.has(authentication,'education:homework:manage')";
	private static final String READ = "@iamAuthorization.any(authentication,'education:homework:view',"
			+ "'education:homework:submission:view','education:homework:analysis:view','education:homework:manage')";
	private final GradeRosterSetupService rosters;
	private final SchoolAssessmentIdentityService identities;
	private final HomeworkService homework;
	private final HomeworkMistakeService mistakes;
	private final ManagedFileService files;
	private final ManagedFileRepository fileEntries;
	private final HomeworkManagedFileAccessPolicy fileAccess;
	private final IamAuthorization authorization;

	@GetMapping("/roster-setup")
	@PreAuthorize(MANAGE)
	public ResultData<GradeRosterSetupService.Options> setup(@RequestParam String schoolId, Authentication actor) {
		return ok(rosters.options(schoolId, actor.getName()));
	}

	@PostMapping("/roster-setup")
	@PreAuthorize(MANAGE)
	public ResultData<GradeRosterSetupService.Result> importRoster(@RequestParam String schoolId,
			@RequestBody GradeRosterSetupService.Command body, Authentication actor) {
		return ok(rosters.createHomeworkRoster(schoolId, body, actor.getName()));
	}

	@PostMapping("/roster-setup/teachers")
	@PreAuthorize(MANAGE)
	public ResultData<GradeRosterSetupService.Option> teacher(@RequestParam String schoolId,
			@RequestBody Teacher body, Authentication actor) {
		return ok(rosters.registerTeacher(schoolId, body.code(), body.name(), actor.getName()));
	}

	@PostMapping("/roster-setup/student-links")
	@PreAuthorize(MANAGE)
	public ResultData<Void> student(@RequestParam String schoolId, @RequestBody StudentLink body, Authentication actor) {
		rosters.linkStudent(schoolId, body.studentNo(), body.username(), actor.getName());
		return ok(null);
	}

	@PostMapping("/roster-setup/teacher-links")
	@PreAuthorize(MANAGE)
	public ResultData<Void> teacherLink(@RequestParam String schoolId, @RequestBody TeacherLink body, Authentication actor) {
		identities.linkAccount(schoolId, new SchoolAssessmentIdentityService.AccountLink(
				"TEACHER", body.teacherNo(), body.username()), actor.getName());
		return ok(null);
	}

	@GetMapping("/offerings")
	@PreAuthorize(READ)
	public ResultData<List<CourseOffering>> offerings(Authentication actor) {
		return ok(homework.offeringOptions(actor));
	}

	@GetMapping("/offerings/{offeringId}/my-mistakes")
	@PreAuthorize(READ)
	public ResultData<List<ErrorItem>> myMistakes(@PathVariable String offeringId, Authentication actor) {
		return ok(mistakes.personal(offeringId, actor));
	}

	@PostMapping("/offerings/{offeringId}/my-mistakes")
	@PreAuthorize(READ)
	public ResultData<ErrorItem> saveMistake(@PathVariable String offeringId, @RequestBody MistakeNote body,
			Authentication actor) {
		return ok(mistakes.add(offeringId, body.note(), actor));
	}

	@PutMapping("/offerings/{offeringId}/my-mistakes/{id}")
	@PreAuthorize(READ)
	public ResultData<ErrorItem> updateMastery(@PathVariable String offeringId, @PathVariable String id,
			@RequestBody Mastery body, Authentication actor) {
		if (body.mastered() == null) throw new IllegalArgumentException("请选择掌握状态");
		return ok(mistakes.mastery(offeringId, id, body.mastered(), actor));
	}

	@GetMapping("/offerings/{offeringId}/mistake-summary")
	@PreAuthorize(MANAGE)
	public ResultData<HomeworkMistakeService.Counts> mistakeSummary(@PathVariable String offeringId,
			Authentication actor) {
		return ok(mistakes.counts(offeringId, actor));
	}

	@PostMapping(value = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("@iamAuthorization.any(authentication,'education:homework:manage','education:homework:create',"
			+ "'education:homework:submission:create','education:homework:view')")
	public ResultData<ManagedFileView> upload(@RequestPart("file") MultipartFile file, Authentication actor) {
		homework.assertAttachmentUploader(actor,
				authorization.any(actor, "education:homework:manage", "education:homework:create"));
		return ok(files.upload(file, "WORKFLOW_FORM_DRAFT", null, actor));
	}

	@GetMapping("/attachments/{id}")
	@PreAuthorize(READ)
	public ResponseEntity<byte[]> download(@PathVariable String id, Authentication actor) {
		var entry = fileEntries.findById(id)
				.filter(file -> "ACTIVE".equals(file.getStatus()) && "BOUND".equals(file.getBindState()))
				.filter(file -> fileAccess.canRead(actor.getName(), file.getBusinessType(), file.getBusinessId()))
				.orElseThrow(() -> new AccessDeniedException("无权读取该作业附件"));
		var content = files.read(entry.getId(), actor);
		return ResponseEntity.ok().contentType(MediaType.parseMediaType(content.contentType()))
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
						.filename(content.filename(), StandardCharsets.UTF_8).build().toString())
				.body(content.content());
	}

	public record Teacher(String code, String name) {}
	public record StudentLink(String studentNo, String username) {}
	public record TeacherLink(String teacherNo, String username) {}
	public record MistakeNote(String note) {}
	public record Mastery(Boolean mastered) {}

	private static <T> ResultData<T> ok(T value) {
		return ResultData.<T>builder().code("200").msg("ok").data(value).build();
	}
}
