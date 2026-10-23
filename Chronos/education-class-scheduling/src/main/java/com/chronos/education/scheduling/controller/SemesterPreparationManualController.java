package com.chronos.education.scheduling.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.model.dto.AdmissionDtos.BatchActionCommand;
import com.chronos.education.scheduling.model.dto.AdmissionDtos.OfferingGenerationCommand;
import com.chronos.education.scheduling.service.SemesterPreparationExecutionService;
import java.time.Year;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 把缺失的开学准备批处理放回已有业务模块，而不是新增一组阶段菜单。 */
@RestController
@RequestMapping("/admin/education")
public class SemesterPreparationManualController {
	private final SemesterPreparationExecutionService execution;

	public SemesterPreparationManualController(SemesterPreparationExecutionService execution) {
		this.execution = execution;
	}

	@PostMapping("/admissions/batch/class-assignment")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:admission:import','education:admission:manage')")
	public ResultData<Map<String, Object>> assignClasses(@RequestBody BatchActionCommand command) {
		return ok(execution.assignClasses(year(command), limit(command, 100), classSize(command), command.dryRun()));
	}

	@PostMapping("/admissions/batch/review")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:admission:review','education:admission:manage')")
	public ResultData<Map<String, Object>> review(@RequestBody BatchActionCommand command,
			Authentication authentication) {
		return ok(execution.reviewAdmissions(year(command), limit(command, 100), command.dryRun(), authentication.getName()));
	}

	@PostMapping("/admissions/batch/enroll")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:admission:enroll','education:admission:manage')")
	public ResultData<Map<String, Object>> enroll(@RequestBody BatchActionCommand command) {
		return ok(execution.enrollAdmissions(year(command), limit(command, 50), command.dryRun()));
	}

	@PostMapping("/course-offerings/generate-from-assignments")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:course:create','education:course:manage','education:scheduling:manage')")
	public ResultData<Map<String, Object>> generateOfferings(@RequestBody OfferingGenerationCommand command) {
		if (command.semesterCode() == null || command.semesterCode().isBlank()) {
			throw new IllegalArgumentException("请选择学期");
		}
		return ok(execution.generateOfferings(command.semesterCode().trim(),
				command.batchLimit() == null ? 100 : command.batchLimit(), command.dryRun()));
	}

	private int year(BatchActionCommand command) {
		return command.admissionYear() == null ? Year.now().getValue() : command.admissionYear();
	}

	private int limit(BatchActionCommand command, int fallback) {
		return command.batchLimit() == null ? fallback : command.batchLimit();
	}

	private int classSize(BatchActionCommand command) {
		return command.targetClassSize() == null ? 50 : command.targetClassSize();
	}

	private <T> ResultData<T> ok(T data) {
		ResultData<T> result = new ResultData<>();
		result.setCode("200");
		result.setMsg("success");
		result.setData(data);
		return result;
	}
}
