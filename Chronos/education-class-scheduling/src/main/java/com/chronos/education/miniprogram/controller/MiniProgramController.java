package com.chronos.education.miniprogram.controller;

import static com.chronos.education.miniprogram.dto.MiniProgramDtos.*;

import java.util.Map;
import java.util.List;

import org.springframework.http.CacheControl;
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

import com.chronos.commons.model.ResultData;
import com.chronos.education.miniprogram.service.MiniProgramIdentityService;

/** Native WeChat mini-program login, account binding and QR entry APIs. */
@RestController
@RequestMapping("/api/mini-program")
public class MiniProgramController {
	private final MiniProgramIdentityService identities;

	public MiniProgramController(MiniProgramIdentityService identities) {
		this.identities = identities;
	}

	@PostMapping("/auth/login")
	public ResultData<LoginResult> login(@RequestBody LoginCommand command) {
		return ok(identities.login(command));
	}

	@PostMapping("/auth/bind-profile")
	public ResultData<LoginResult> bindProfile(@RequestBody ProfileBindingCommand command) {
		return ok(identities.bindExistingProfile(command));
	}

	@PostMapping("/auth/bind-guardian")
	public ResultData<LoginResult> bindGuardian(@RequestBody GuardianBindingCommand command) {
		return ok(identities.bindGuardian(command));
	}

	@PostMapping("/guardian-invitations")
	public ResultData<InvitationView> createGuardianInvitation(
			Authentication authentication,
			@RequestBody GuardianInvitationCommand command) {
		return ok(identities.createGuardianInvitation(authentication.getName(), command));
	}

	@GetMapping(value = "/guardian-invitations/{scene}/code", produces = MediaType.IMAGE_PNG_VALUE)
	public ResponseEntity<byte[]> guardianInvitationCode(
			Authentication authentication,
			@PathVariable String scene) {
		return image(identities.guardianInvitationCode(authentication.getName(), scene));
	}

	@PostMapping("/admin/school-entry")
	@PreAuthorize("hasAuthority('education:mini-program:entry:manage') or hasAuthority('ROLE_CODE_SUPER_ADMIN')")
	public ResultData<SchoolEntryView> schoolEntry(
			Authentication authentication,
			@RequestBody(required = false) SchoolEntryCommand command) {
		return ok(identities.createOrGetSchoolEntry(authentication.getName(), command));
	}

	@GetMapping("/admin/schools")
	@PreAuthorize("hasAuthority('education:mini-program:entry:manage') or hasAuthority('ROLE_CODE_SUPER_ADMIN')")
	public ResultData<List<SchoolView>> schools(Authentication authentication) {
		return ok(identities.availableSchools(authentication.getName()));
	}

	@GetMapping(value = "/admin/school-entry/code", produces = MediaType.IMAGE_PNG_VALUE)
	@PreAuthorize("hasAuthority('education:mini-program:entry:manage') or hasAuthority('ROLE_CODE_SUPER_ADMIN')")
	public ResponseEntity<byte[]> schoolEntryCode(
			Authentication authentication,
			@RequestParam(required = false) String organizationId) {
		return image(identities.schoolEntryCode(authentication.getName(), organizationId));
	}

	private <T> ResultData<T> ok(T value) {
		return ResultData.<T>builder().code("200").msg("ok").data(value).build();
	}

	private ResponseEntity<byte[]> image(byte[] value) {
		return ResponseEntity.ok()
				.contentType(MediaType.IMAGE_PNG)
				.cacheControl(CacheControl.noStore())
				.body(value);
	}
}
