package com.chronos.education.miniprogram.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.IExternalIdentityRepository;
import com.chronos.Idao.IOrganizationRepository;
import com.chronos.Idao.IRoleRepository;
import com.chronos.education.miniprogram.dao.GuardianBindingInvitationRepository;
import com.chronos.education.miniprogram.dao.MiniProgramBindingSessionRepository;
import com.chronos.education.miniprogram.dao.MiniProgramSchoolEntryRepository;
import com.chronos.education.miniprogram.dto.MiniProgramDtos.LoginCommand;
import com.chronos.education.miniprogram.dto.MiniProgramDtos.ProfileBindingCommand;
import com.chronos.education.miniprogram.dto.MiniProgramDtos.GuardianBindingCommand;
import com.chronos.education.miniprogram.model.GuardianBindingInvitation;
import com.chronos.education.miniprogram.model.MiniProgramBindingSession;
import com.chronos.education.miniprogram.model.MiniProgramSchoolEntry;
import com.chronos.education.scheduling.dao.EducationUserBindingRepository;
import com.chronos.education.scheduling.dao.ParentProfileRepository;
import com.chronos.education.scheduling.dao.StudentGuardianRepository;
import com.chronos.education.scheduling.dao.StudentProfileRepository;
import com.chronos.education.scheduling.dao.TeacherAcademicProfileRepository;
import com.chronos.education.scheduling.model.EducationUserBinding;
import com.chronos.education.scheduling.model.StudentProfile;
import com.chronos.model.pojo.Role;
import com.chronos.model.pojo.AdminUser;
import com.chronos.model.pojo.Organization;
import com.chronos.security.JwtUtil;
import com.chronos.service.iService.IAuditLogService;
import com.chronos.service.iService.IRefreshTokenService;

import io.jsonwebtoken.Claims;

@ExtendWith(MockitoExtension.class)
class MiniProgramIdentityServiceTest {
	@Mock private WechatMiniProgramGateway wechat;
	@Mock private MiniProgramSchoolEntryRepository schoolEntries;
	@Mock private MiniProgramBindingSessionRepository sessions;
	@Mock private GuardianBindingInvitationRepository invitations;
	@Mock private IOrganizationRepository organizations;
	@Mock private IExternalIdentityRepository externalIdentities;
	@Mock private IAdminUserRepository users;
	@Mock private IRoleRepository roles;
	@Mock private EducationUserBindingRepository profileBindings;
	@Mock private StudentProfileRepository students;
	@Mock private TeacherAcademicProfileRepository teachers;
	@Mock private ParentProfileRepository parents;
	@Mock private StudentGuardianRepository guardians;
	@Mock private PasswordEncoder passwords;
	@Mock private JwtUtil jwt;
	@Mock private IRefreshTokenService refreshTokens;
	@Mock private IAuditLogService audit;
	@InjectMocks private MiniProgramIdentityService service;

	@Test
	void schoolSceneCreatesShortLivedProfileBindingSession() {
		MiniProgramSchoolEntry entry = new MiniProgramSchoolEntry();
		entry.setOrganizationId("school-1");
		entry.setSceneCode("s_ENTRY001");
		Organization school = new Organization();
		school.setId("school-1");
		school.setOrganizationName("Chronos 示范学校");
		school.setShortName("示范学校");
		school.setOrgCode("DEMO");
		school.setStatus(1);

		when(wechat.exchangeLoginCode("wx-code"))
				.thenReturn(new WechatMiniProgramGateway.WechatSession("openid-1", "unionid-1"));
		when(wechat.identitySource()).thenReturn("WXMP:wx-test");
		when(schoolEntries.findBySceneCodeAndStatus("s_ENTRY001", "ACTIVE"))
				.thenReturn(Optional.of(entry));
		when(externalIdentities.findBySourceIdAndExternalSubject("WXMP:wx-test", "openid-1"))
				.thenReturn(Optional.empty());
		when(organizations.findById("school-1")).thenReturn(Optional.of(school));
		when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		var result = service.login(new LoginCommand("wx-code", "s_ENTRY001"));

		assertThat(result.state()).isEqualTo("PROFILE_BINDING_REQUIRED");
		assertThat(result.school().name()).isEqualTo("Chronos 示范学校");
		assertThat(result.bindingToken()).isNotBlank();
		ArgumentCaptor<MiniProgramBindingSession> captured =
				ArgumentCaptor.forClass(MiniProgramBindingSession.class);
		verify(sessions).save(captured.capture());
		assertThat(captured.getValue().getTokenHash()).hasSize(64);
		assertThat(captured.getValue().getTokenHash()).doesNotContain(result.bindingToken());
		assertThat(captured.getValue().getOrganizationId()).isEqualTo("school-1");
		assertThat(captured.getValue().getOpenId()).isEqualTo("openid-1");
	}

	@Test
	void verifiedWechatPhoneBindsExistingStudentAccountAndIssuesTokens() {
		MiniProgramBindingSession pending = new MiniProgramBindingSession();
		pending.setIdentitySource("WXMP:wx-test");
		pending.setOpenId("openid-student");
		pending.setOrganizationId("school-1");
		pending.setStatus("PENDING");
		pending.setExpiresAt(LocalDateTime.now().plusMinutes(5));
		StudentProfile student = new StudentProfile();
		student.setId("student-1");
		student.setStudentNo("S20260001");
		student.setPhone("13800138000");
		student.setEnrollmentStatus("ACTIVE");
		EducationUserBinding binding = new EducationUserBinding();
		binding.setUsername("student.demo");
		AdminUser account = new AdminUser();
		account.setId("user-1");
		account.setUsername("student.demo");
		account.setOrganizationId("school-1");
		account.setStatus(1);
		account.setAccountLocked(false);
		Claims claims = org.mockito.Mockito.mock(Claims.class);

		when(wechat.exchangePhoneCode("phone-code")).thenReturn("13800138000");
		when(sessions.findLockedByTokenHash(any())).thenReturn(Optional.of(pending));
		when(students.findByStudentNo("S20260001")).thenReturn(Optional.of(student));
		when(profileBindings.findByProfileTypeAndProfileIdAndStatus("STUDENT", "student-1", "ACTIVE"))
				.thenReturn(Optional.of(binding));
		when(users.findByUsername("student.demo")).thenReturn(account);
		when(externalIdentities.findBySourceIdAndExternalSubject("WXMP:wx-test", "openid-student"))
				.thenReturn(Optional.empty());
		when(externalIdentities.findBySourceIdAndUserIdAndStatus("WXMP:wx-test", "user-1", "ACTIVE"))
				.thenReturn(Optional.empty());
		when(jwt.generateAccessToken(any(), any())).thenReturn("access-token");
		when(jwt.generateRefreshToken(any(), any())).thenReturn("refresh-token");
		when(jwt.parseToken("refresh-token")).thenReturn(claims);
		when(claims.getExpiration()).thenReturn(Date.from(java.time.Instant.now().plusSeconds(3600)));
		Organization school = new Organization();
		school.setId("school-1");
		school.setOrganizationName("Chronos 示范学校");
		school.setOrgCode("DEMO");
		school.setStatus(1);
		when(organizations.findById("school-1")).thenReturn(Optional.of(school));

		var result = service.bindExistingProfile(new ProfileBindingCommand(
				"binding-token", "STUDENT", "S20260001", "phone-code"));

		assertThat(result.state()).isEqualTo("AUTHENTICATED");
		assertThat(result.tokens()).containsEntry("accessToken", "access-token")
				.containsEntry("refreshToken", "refresh-token");
		assertThat(pending.getStatus()).isEqualTo("CONSUMED");
		verify(externalIdentities).save(any());
		verify(refreshTokens).create(org.mockito.ArgumentMatchers.eq("refresh-token"),
				org.mockito.ArgumentMatchers.eq("student.demo"), any());
	}

	@Test
	void guardianInvitationCreatesOneParentAccountAndStudentRelation() {
		MiniProgramBindingSession pending = new MiniProgramBindingSession();
		pending.setIdentitySource("WXMP:wx-test");
		pending.setOpenId("openid-parent");
		pending.setOrganizationId("school-1");
		pending.setGuardianInvitationId("invite-1");
		pending.setStatus("PENDING");
		pending.setExpiresAt(LocalDateTime.now().plusMinutes(5));
		GuardianBindingInvitation invitation = new GuardianBindingInvitation();
		invitation.setId("invite-1");
		invitation.setSceneCode("g_INVITE001");
		invitation.setOrganizationId("school-1");
		invitation.setStudentId("student-1");
		invitation.setRelationship("MOTHER");
		invitation.setPrimaryGuardian(true);
		invitation.setEmergencyContact(true);
		invitation.setStatus("PENDING");
		invitation.setExpiresAt(LocalDateTime.now().plusMinutes(5));
		Role parentRole = new Role();
		parentRole.setRoleCode("EDU_PARENT");

		when(wechat.exchangePhoneCode("phone-code")).thenReturn("13900139000");
		when(sessions.findLockedByTokenHash(any())).thenReturn(Optional.of(pending));
		when(invitations.findById("invite-1")).thenReturn(Optional.of(invitation));
		when(parents.findFirstByPhoneAndStatusOrderByCreateTime("13900139000", "ACTIVE"))
				.thenReturn(Optional.empty());
		when(parents.existsByParentNo(any())).thenReturn(false);
		when(parents.save(any())).thenAnswer(invocation -> {
			var value = invocation.<com.chronos.education.scheduling.model.ParentProfile>getArgument(0);
			value.setId("parent-1");
			return value;
		});
		when(roles.findByRoleCode("EDU_PARENT")).thenReturn(parentRole);
		when(users.findByUsername(any())).thenReturn(null);
		when(users.save(any())).thenAnswer(invocation -> {
			AdminUser value = invocation.getArgument(0);
			value.setId("parent-user-1");
			return value;
		});
		when(passwords.encode(any())).thenReturn("encoded-password");
		when(guardians.findByStudentIdAndParentId("student-1", "parent-1"))
				.thenReturn(Optional.empty());
		when(guardians.findByStudentIdOrderByCreateTime("student-1")).thenReturn(java.util.List.of());
		when(externalIdentities.findBySourceIdAndExternalSubject("WXMP:wx-test", "openid-parent"))
				.thenReturn(Optional.empty());
		when(externalIdentities.findBySourceIdAndUserIdAndStatus("WXMP:wx-test", "parent-user-1", "ACTIVE"))
				.thenReturn(Optional.empty());
		stubTokenIssuance();

		var result = service.bindGuardian(new GuardianBindingCommand(
				"binding-token", "演示家长", "phone-code"));

		assertThat(result.state()).isEqualTo("AUTHENTICATED");
		assertThat(invitation.getStatus()).isEqualTo("CONSUMED");
		assertThat(pending.getStatus()).isEqualTo("CONSUMED");
		verify(profileBindings).save(any(EducationUserBinding.class));
		verify(guardians).save(org.mockito.ArgumentMatchers.argThat(value ->
				"student-1".equals(value.getStudentId())
						&& "parent-1".equals(value.getParentId())
						&& Boolean.TRUE.equals(value.getPrimaryGuardian())));
	}

	private void stubTokenIssuance() {
		Claims claims = org.mockito.Mockito.mock(Claims.class);
		when(jwt.generateAccessToken(any(), any())).thenReturn("access-token");
		when(jwt.generateRefreshToken(any(), any())).thenReturn("refresh-token");
		when(jwt.parseToken("refresh-token")).thenReturn(claims);
		when(claims.getExpiration()).thenReturn(Date.from(java.time.Instant.now().plusSeconds(3600)));
		Organization school = new Organization();
		school.setId("school-1");
		school.setOrganizationName("Chronos 示范学校");
		school.setOrgCode("DEMO");
		school.setStatus(1);
		when(organizations.findById("school-1")).thenReturn(Optional.of(school));
	}
}
