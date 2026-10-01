package com.chronos.workflow;

import java.security.Principal;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.chronos.commons.model.ResultData;

@RestController
public class WorkflowArchiveController {
	private final WorkflowArchiveService archives;

	public WorkflowArchiveController(WorkflowArchiveService archives) {
		this.archives = archives;
	}

	@GetMapping("/admin/workflow-instances/{id}/archive-package")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:manage') and @workflowSecurity.canManageInstance(authentication.name,#id)")
	public ResultData<WorkflowArchiveService.ArchivePackage> export(
			@PathVariable String id,
			Principal principal) {
		return ResultData.<WorkflowArchiveService.ArchivePackage>builder()
				.code("200")
				.msg("ok")
				.data(archives.export(id, principal.getName()))
				.build();
	}
}
