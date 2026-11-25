package com.chronos.education.scheduling.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

import com.chronos.education.scheduling.service.AcademicCatalogImportService.Kind;
import com.chronos.security.IamAuthorization;

public final class AcademicCatalogPermissions {
	private AcademicCatalogPermissions() {
	}

	public static void requireCreate(IamAuthorization authorization, Kind kind, Authentication authentication) {
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
