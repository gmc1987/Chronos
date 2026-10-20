package com.chronos.education.miniprogram.dto;

import java.time.LocalDateTime;
import java.util.Map;

public final class MiniProgramDtos {
	private MiniProgramDtos() {}

	public record LoginCommand(String code, String scene) {}

	public record ProfileBindingCommand(
			String bindingToken,
			String profileType,
			String credentialNo,
			String phoneCode) {}

	public record GuardianBindingCommand(
			String bindingToken,
			String parentName,
			String phoneCode) {}

	public record SchoolEntryCommand(String organizationId) {}

	public record GuardianInvitationCommand(
			String relationship,
			Boolean primaryGuardian,
			Boolean emergencyContact) {}

	public record SchoolView(String id, String name, String shortName, String code) {}

	public record InvitationView(
			String scene,
			String studentName,
			String relationship,
			LocalDateTime expiresAt) {}

	public record LoginResult(
			String state,
			String bindingToken,
			SchoolView school,
			InvitationView invitation,
			Map<String, String> tokens) {}

	public record SchoolEntryView(
			String scene,
			String page,
			SchoolView school,
			LocalDateTime generatedAt) {}
}
