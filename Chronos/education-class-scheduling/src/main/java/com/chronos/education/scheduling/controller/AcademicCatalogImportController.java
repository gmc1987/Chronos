package com.chronos.education.scheduling.controller;

import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.service.AcademicCatalogImportService;
import com.chronos.education.scheduling.service.AcademicCatalogImportService.Kind;
import com.chronos.security.DirectoryDataScopeGuard;
import com.chronos.security.IamAuthorization;

@RestController
@RequestMapping("/admin/education/catalog-import")
public class AcademicCatalogImportController {
	private final AcademicCatalogImportService imports;
	private final IamAuthorization authorization;
	private final DirectoryDataScopeGuard directory;

	public AcademicCatalogImportController(AcademicCatalogImportService imports, IamAuthorization authorization,
			DirectoryDataScopeGuard directory) {
		this.imports = imports;
		this.authorization = authorization;
		this.directory = directory;
	}

	@GetMapping("/{kind}/template")
	public ResponseEntity<byte[]> template(@PathVariable Kind kind, Authentication authentication) throws IOException {
		requireCreate(kind, authentication);
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + kind.name() + ".xlsx")
				.body(imports.template(kind));
	}

	@PostMapping("/{kind}")
	public ResultData<AcademicCatalogImportService.Result> upload(@PathVariable Kind kind,
			@RequestParam MultipartFile file, @RequestParam(defaultValue = "true") boolean dryRun,
			@RequestParam(required = false) String organizationId, Authentication authentication) throws IOException {
		requireCreate(kind, authentication);
		String organization = directory.resolveOrganization(authentication, organizationId);
		boolean canUpdateAssignment = kind == Kind.ASSIGNMENTS && authorization.any(authentication,
				"education:teaching-assignment:update", "education:teaching-assignment:manage");
		return ResultData.<AcademicCatalogImportService.Result>builder().code("200").msg("ok")
				.data(imports.run(kind, file, dryRun, organization, authentication, canUpdateAssignment)).build();
	}

	private void requireCreate(Kind kind, Authentication authentication) {
		if (kind == Kind.TEACHER_PROFILES) {
			if (!authorization.any(authentication, "education:teacher:business:update", "education:teacher:business:manage")) {
				throw new AccessDeniedException("没有教师档案修改权限");
			}
			return;
		}
		String prefix = switch (kind) {
			case SUBJECTS -> "education:subject";
			case COURSES -> "education:course";
			case MAJORS, CLASSES -> "education:class";
			case TERMS -> "education:term";
			case GRADES -> "education:grade";
			case TEACHER_PROFILES, PROGRAMS, PROGRAM_COURSES, TERM_COURSE_PLANS -> throw new IllegalArgumentException("请从对应业务页面导入");
			case ASSIGNMENTS -> "education:teaching-assignment";
		};
		if (!authorization.any(authentication, prefix + ":create", prefix + ":manage")) {
			throw new AccessDeniedException("没有该模块的新增权限");
		}
	}
}
