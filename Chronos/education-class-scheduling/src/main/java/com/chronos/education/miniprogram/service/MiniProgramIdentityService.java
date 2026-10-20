package com.chronos.education.miniprogram.service;

import static com.chronos.education.miniprogram.dto.MiniProgramDtos.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.IExternalIdentityRepository;
import com.chronos.Idao.IOrganizationRepository;
import com.chronos.Idao.IRoleRepository;
import com.chronos.education.miniprogram.dao.GuardianBindingInvitationRepository;
import com.chronos.education.miniprogram.dao.MiniProgramBindingSessionRepository;
import com.chronos.education.miniprogram.dao.MiniProgramSchoolEntryRepository;
import com.chronos.education.miniprogram.model.GuardianBindingInvitation;
import com.chronos.education.miniprogram.model.MiniProgramBindingSession;
import com.chronos.education.miniprogram.model.MiniProgramSchoolEntry;
import com.chronos.education.scheduling.dao.EducationUserBindingRepository;
import com.chronos.education.scheduling.dao.ParentProfileRepository;
import com.chronos.education.scheduling.dao.StudentGuardianRepository;
import com.chronos.education.scheduling.dao.StudentProfileRepository;
import com.chronos.education.scheduling.dao.TeacherAcademicProfileRepository;
import com.chronos.education.scheduling.model.EducationUserBinding;
import com.chronos.education.scheduling.model.ParentProfile;
import com.chronos.education.scheduling.model.StudentGuardianRelation;
import com.chronos.education.scheduling.model.StudentProfile;
import com.chronos.education.scheduling.model.TeacherAcademicProfile;
import com.chronos.model.pojo.AdminUser;
import com.chronos.model.pojo.ExternalIdentity;
import com.chronos.model.pojo.Organization;
import com.chronos.model.pojo.Role;
import com.chronos.security.JwtUtil;
import com.chronos.service.iService.IAuditLogService;
import com.chronos.service.iService.IRefreshTokenService;

import io.jsonwebtoken.Claims;

/** Coordinates trusted WeChat identity with existing school accounts and guardian relations. */
@Service
public class MiniProgramIdentityService {
	private static final SecureRandom RANDOM = new SecureRandom();
	private static final int BINDING_MINUTES = 10;

	private final WechatMiniProgramGateway wechat;
	private final MiniProgramSchoolEntryRepository schoolEntries;
	private final MiniProgramBindingSessionRepository sessions;
	private final GuardianBindingInvitationRepository invitations;
	private final IOrganizationRepository organizations;
	private final IExternalIdentityRepository externalIdentities;
	private final IAdminUserRepository users;
	private final IRoleRepository roles;
	private final EducationUserBindingRepository profileBindings;
	private final StudentProfileRepository students;
	private final TeacherAcademicProfileRepository teachers;
	private final ParentProfileRepository parents;
	private final StudentGuardianRepository guardians;
	private final PasswordEncoder passwords;
	private final JwtUtil jwt;
	private final IRefreshTokenService refreshTokens;
	private final IAuditLogService audit;

	public MiniProgramIdentityService(
			WechatMiniProgramGateway wechat,
			MiniProgramSchoolEntryRepository schoolEntries,
			MiniProgramBindingSessionRepository sessions,
			GuardianBindingInvitationRepository invitations,
			IOrganizationRepository organizations,
			IExternalIdentityRepository externalIdentities,
			IAdminUserRepository users,
			IRoleRepository roles,
			EducationUserBindingRepository profileBindings,
			StudentProfileRepository students,
			TeacherAcademicProfileRepository teachers,
			ParentProfileRepository parents,
			StudentGuardianRepository guardians,
			PasswordEncoder passwords,
			JwtUtil jwt,
			IRefreshTokenService refreshTokens,
			IAuditLogService audit) {
		this.wechat = wechat;
		this.schoolEntries = schoolEntries;
		this.sessions = sessions;
		this.invitations = invitations;
		this.organizations = organizations;
		this.externalIdentities = externalIdentities;
		this.users = users;
		this.roles = roles;
		this.profileBindings = profileBindings;
		this.students = students;
		this.teachers = teachers;
		this.parents = parents;
		this.guardians = guardians;
		this.passwords = passwords;
		this.jwt = jwt;
		this.refreshTokens = refreshTokens;
		this.audit = audit;
	}

	@Transactional
	public LoginResult login(LoginCommand command) {
		if (command == null) throw new IllegalArgumentException("登录参数不能为空");
		WechatMiniProgramGateway.WechatSession wechatSession = wechat.exchangeLoginCode(command.code());
		SceneContext context = resolveScene(command.scene());
		ExternalIdentity identity = externalIdentities
				.findBySourceIdAndExternalSubject(wechat.identitySource(), wechatSession.openId())
				.filter(value -> "ACTIVE".equals(value.getStatus()))
				.orElse(null);

		if (identity != null && context.invitation() == null) {
			AdminUser user = requireActiveUser(identity.getUserId());
			assertOrganization(user, context.organizationId());
			identity.setLastLoginAt(LocalDateTime.now());
			externalIdentities.save(identity);
			audit.log(user.getUsername(), "WECHAT_MINI_PROGRAM_LOGIN", wechat.identitySource());
			return new LoginResult("AUTHENTICATED", null, school(context.organizationId()), null, issueTokens(user));
		}

		String rawToken = opaqueToken();
		MiniProgramBindingSession pending = new MiniProgramBindingSession();
		pending.setTokenHash(hash(rawToken));
		pending.setIdentitySource(wechat.identitySource());
		pending.setOpenId(wechatSession.openId());
		pending.setUnionId(blankToNull(wechatSession.unionId()));
		pending.setOrganizationId(context.organizationId());
		pending.setGuardianInvitationId(context.invitation() == null ? null : context.invitation().getId());
		pending.setExpiresAt(LocalDateTime.now().plusMinutes(BINDING_MINUTES));
		pending.setStatus("PENDING");
		sessions.save(pending);

		String state = context.invitation() != null
				? "GUARDIAN_BINDING_REQUIRED"
				: context.organizationId() == null ? "SCHOOL_REQUIRED" : "PROFILE_BINDING_REQUIRED";
		return new LoginResult(
				state,
				rawToken,
				school(context.organizationId()),
				invitationView(context.invitation()),
				null);
	}

	@Transactional
	public LoginResult bindExistingProfile(ProfileBindingCommand command) {
		if (command == null) throw new IllegalArgumentException("绑定参数不能为空");
		String phone = normalizePhone(wechat.exchangePhoneCode(command.phoneCode()));
		MiniProgramBindingSession session = requirePendingSession(command.bindingToken());
		if (session.getOrganizationId() == null) throw new IllegalStateException("请先通过学校专属入口进入小程序");
		if (session.getGuardianInvitationId() != null) throw new IllegalStateException("当前会话只能用于家长绑定");

		String type = required(command.profileType(), "请选择身份类型").toUpperCase(Locale.ROOT);
		String credential = required(command.credentialNo(), "学号或工号不能为空");
		EducationUserBinding binding;
		AdminUser user;
		if ("STUDENT".equals(type)) {
			StudentProfile profile = students.findByStudentNo(credential)
					.orElseThrow(() -> new IllegalArgumentException("没有找到对应学号"));
			if (!"ACTIVE".equals(profile.getEnrollmentStatus())) throw new IllegalStateException("学生当前不是有效在籍状态");
			binding = requireProfileBinding("STUDENT", profile.getId());
			user = requireActiveUserByUsername(binding.getUsername());
			assertVerifiedPhone(phone, profile.getPhone(), user.getPhone());
		} else if ("TEACHER".equals(type)) {
			TeacherAcademicProfile profile = teachers.findByTeacherNo(credential)
					.orElseThrow(() -> new IllegalArgumentException("没有找到对应工号"));
			if (!Boolean.TRUE.equals(profile.getEnabled()) || !"ACTIVE".equals(profile.getEmploymentStatus())) {
				throw new IllegalStateException("教师当前不是有效在职状态");
			}
			binding = requireProfileBinding("TEACHER", profile.getId());
			user = requireActiveUserByUsername(binding.getUsername());
			assertVerifiedPhone(phone, user.getPhone());
		} else {
			throw new IllegalArgumentException("小程序学校入口只支持学生或教师身份绑定");
		}
		assertOrganization(user, session.getOrganizationId());
		bindExternalIdentity(session, user);
		consume(session);
		audit.log(user.getUsername(), "WECHAT_MINI_PROGRAM_PROFILE_BIND", type + ":" + credential);
		return new LoginResult("AUTHENTICATED", null, school(session.getOrganizationId()), null, issueTokens(user));
	}

	@Transactional
	public LoginResult bindGuardian(GuardianBindingCommand command) {
		if (command == null) throw new IllegalArgumentException("家长绑定参数不能为空");
		String phone = normalizePhone(wechat.exchangePhoneCode(command.phoneCode()));
		String parentName = required(command.parentName(), "家长姓名不能为空");
		MiniProgramBindingSession session = requirePendingSession(command.bindingToken());
		if (session.getGuardianInvitationId() == null) throw new IllegalStateException("当前会话不是家长邀请");

		GuardianBindingInvitation invitation = invitations.findById(session.getGuardianInvitationId())
				.orElseThrow(() -> new IllegalStateException("家长邀请不存在"));
		assertUsable(invitation);
		ParentAccount parentAccount = findOrCreateParent(parentName, phone, invitation.getOrganizationId());
		bindExternalIdentity(session, parentAccount.account());

		StudentGuardianRelation relation = guardians
				.findByStudentIdAndParentId(invitation.getStudentId(), parentAccount.profile().getId())
				.orElseGet(StudentGuardianRelation::new);
		if (relation.getId() == null) {
			relation.setStudentId(invitation.getStudentId());
			relation.setParentId(parentAccount.profile().getId());
		}
		if (Boolean.TRUE.equals(invitation.getPrimaryGuardian())) {
			boolean occupied = guardians.findByStudentIdOrderByCreateTime(invitation.getStudentId()).stream()
					.anyMatch(value -> Boolean.TRUE.equals(value.getPrimaryGuardian())
							&& !value.getParentId().equals(parentAccount.profile().getId()));
			if (occupied) throw new IllegalStateException("该学生已经设置主监护人");
		}
		relation.setRelationship(invitation.getRelationship());
		relation.setPrimaryGuardian(invitation.getPrimaryGuardian());
		relation.setEmergencyContact(invitation.getEmergencyContact());
		guardians.save(relation);

		invitation.setStatus("CONSUMED");
		invitation.setConsumedAt(LocalDateTime.now());
		invitation.setConsumedBy(parentAccount.account().getUsername());
		invitations.save(invitation);
		consume(session);
		audit.log(parentAccount.account().getUsername(), "WECHAT_GUARDIAN_BIND",
				"studentId=" + invitation.getStudentId() + ",relationship=" + invitation.getRelationship());
		return new LoginResult(
				"AUTHENTICATED", null, school(invitation.getOrganizationId()), null,
				issueTokens(parentAccount.account()));
	}

	@Transactional
	public SchoolEntryView createOrGetSchoolEntry(String actor, SchoolEntryCommand command) {
		AdminUser account = requireActiveUserByUsername(actor);
		String requested = command == null ? null : blankToNull(command.organizationId());
		String organizationId = requested == null ? account.getOrganizationId() : requested;
		if (organizationId == null) throw new IllegalArgumentException("请选择学校");
		boolean platformAdministrator = isPlatformAdministrator(account);
		if (!platformAdministrator && !organizationId.equals(account.getOrganizationId())) {
			throw new org.springframework.security.access.AccessDeniedException("只能生成本校入口");
		}
		Organization organization = requireSchool(organizationId);
		MiniProgramSchoolEntry entry = schoolEntries.findByOrganizationId(organizationId)
				.orElseGet(MiniProgramSchoolEntry::new);
		if (entry.getId() == null) {
			entry.setOrganizationId(organizationId);
			entry.setSceneCode(scene("s_"));
			entry.setGeneratedAt(LocalDateTime.now());
		}
		entry.setStatus("ACTIVE");
		entry = schoolEntries.save(entry);
		audit.log(actor, "WECHAT_SCHOOL_ENTRY_GENERATE", organizationId);
		return new SchoolEntryView(entry.getSceneCode(), entry.getPagePath(), schoolView(organization), entry.getGeneratedAt());
	}

	@Transactional(readOnly = true)
	public List<SchoolView> availableSchools(String actor) {
		AdminUser account = requireActiveUserByUsername(actor);
		if (!isPlatformAdministrator(account)) {
			if (account.getOrganizationId() == null) return List.of();
			return List.of(school(account.getOrganizationId()));
		}
		return organizations.findAll().stream()
				.filter(value -> Integer.valueOf(1).equals(value.getStatus()))
				.filter(value -> value.getParentOrgId() == null || Set.of(
						"SCHOOL", "CAMPUS", "COLLEGE", "UNIVERSITY")
						.contains(String.valueOf(value.getOrganizationType()).toUpperCase(Locale.ROOT)))
				.sorted(java.util.Comparator.comparing(Organization::getOrganizationName))
				.map(this::schoolView)
				.toList();
	}

	@Transactional
	public byte[] schoolEntryCode(String actor, String organizationId) {
		SchoolEntryView entry = createOrGetSchoolEntry(actor, new SchoolEntryCommand(organizationId));
		return wechat.createUnlimitedCode(entry.scene(), entry.page());
	}

	@Transactional
	public InvitationView createGuardianInvitation(String actor, GuardianInvitationCommand command) {
		if (command == null) throw new IllegalArgumentException("邀请参数不能为空");
		AdminUser account = requireActiveUserByUsername(actor);
		EducationUserBinding binding = profileBindings.findByUsernameAndProfileTypeAndStatus(actor, "STUDENT", "ACTIVE")
				.orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("只有已绑定学生可以邀请家长"));
		StudentProfile student = students.findById(binding.getProfileId())
				.orElseThrow(() -> new IllegalStateException("学生档案不存在"));
		if (account.getOrganizationId() == null) throw new IllegalStateException("学生账号尚未归属学校");
		String relationship = required(command.relationship(), "请选择监护关系");
		if (relationship.length() > 32) throw new IllegalArgumentException("监护关系过长");
		if (Boolean.TRUE.equals(command.primaryGuardian())) {
			boolean occupied = guardians.findByStudentIdOrderByCreateTime(student.getId()).stream()
					.anyMatch(value -> Boolean.TRUE.equals(value.getPrimaryGuardian()));
			if (occupied) throw new IllegalStateException("该学生已经设置主监护人");
		}

		GuardianBindingInvitation invitation = new GuardianBindingInvitation();
		invitation.setSceneCode(scene("g_"));
		invitation.setOrganizationId(account.getOrganizationId());
		invitation.setStudentId(student.getId());
		invitation.setRelationship(relationship);
		invitation.setPrimaryGuardian(Boolean.TRUE.equals(command.primaryGuardian()));
		invitation.setEmergencyContact(Boolean.TRUE.equals(command.emergencyContact()));
		invitation.setIssuedBy(actor);
		invitation.setExpiresAt(LocalDateTime.now().plusMinutes(BINDING_MINUTES));
		invitation.setStatus("PENDING");
		invitations.save(invitation);
		audit.log(actor, "WECHAT_GUARDIAN_INVITATION_CREATE", invitation.getId());
		return invitationView(invitation);
	}

	@Transactional
	public byte[] guardianInvitationCode(String actor, String scene) {
		GuardianBindingInvitation invitation = invitations.findBySceneCode(scene)
				.orElseThrow(() -> new IllegalArgumentException("家长邀请不存在"));
		if (!actor.equals(invitation.getIssuedBy())) {
			throw new org.springframework.security.access.AccessDeniedException("只能查看本人生成的邀请");
		}
		assertUsable(invitation);
		return wechat.createUnlimitedCode(scene, "pages/bind/index");
	}

	private SceneContext resolveScene(String rawScene) {
		String scene = blankToNull(rawScene);
		if (scene == null) return new SceneContext(null, null);
		MiniProgramSchoolEntry schoolEntry = schoolEntries.findBySceneCodeAndStatus(scene, "ACTIVE").orElse(null);
		if (schoolEntry != null) {
			schoolEntry.setLastUsedAt(LocalDateTime.now());
			schoolEntries.save(schoolEntry);
			return new SceneContext(schoolEntry.getOrganizationId(), null);
		}
		GuardianBindingInvitation invitation = invitations.findLockedBySceneCode(scene)
				.orElseThrow(() -> new IllegalArgumentException("学校入口或家长邀请无效"));
		assertUsable(invitation);
		return new SceneContext(invitation.getOrganizationId(), invitation);
	}

	private MiniProgramBindingSession requirePendingSession(String rawToken) {
		String token = required(rawToken, "绑定会话不能为空");
		MiniProgramBindingSession session = sessions.findLockedByTokenHash(hash(token))
				.orElseThrow(() -> new IllegalArgumentException("绑定会话无效"));
		if (!"PENDING".equals(session.getStatus())) throw new IllegalStateException("绑定会话已经使用");
		if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
			session.setStatus("EXPIRED");
			sessions.save(session);
			throw new IllegalStateException("绑定会话已过期，请重新扫码");
		}
		return session;
	}

	private void bindExternalIdentity(MiniProgramBindingSession session, AdminUser user) {
		ExternalIdentity existingSubject = externalIdentities
				.findBySourceIdAndExternalSubject(session.getIdentitySource(), session.getOpenId())
				.orElse(null);
		if (existingSubject != null && !existingSubject.getUserId().equals(user.getId())) {
			throw new IllegalStateException("该微信已经绑定其他平台账号");
		}
		externalIdentities.findBySourceIdAndUserIdAndStatus(session.getIdentitySource(), user.getId(), "ACTIVE")
				.filter(value -> !value.getExternalSubject().equals(session.getOpenId()))
				.ifPresent(value -> { throw new IllegalStateException("该平台账号已经绑定其他微信"); });
		ExternalIdentity identity = existingSubject == null ? new ExternalIdentity() : existingSubject;
		identity.setSourceId(session.getIdentitySource());
		identity.setExternalSubject(session.getOpenId());
		identity.setUserId(user.getId());
		identity.setEmployeeId(user.getEmployeeId());
		identity.setStatus("ACTIVE");
		identity.setLinkedAt(identity.getLinkedAt() == null ? LocalDateTime.now() : identity.getLinkedAt());
		identity.setLastLoginAt(LocalDateTime.now());
		externalIdentities.save(identity);
	}

	private ParentAccount findOrCreateParent(String name, String phone, String organizationId) {
		ParentProfile profile = parents.findFirstByPhoneAndStatusOrderByCreateTime(phone, "ACTIVE").orElse(null);
		if (profile != null) {
			EducationUserBinding binding = profileBindings.findByProfileTypeAndProfileIdAndStatus("PARENT", profile.getId(), "ACTIVE")
					.orElse(null);
			if (binding != null) {
				AdminUser account = requireActiveUserByUsername(binding.getUsername());
				assertOrganization(account, organizationId);
				return new ParentAccount(profile, account);
			}
		}
		if (profile == null) {
			profile = new ParentProfile();
			profile.setParentNo(uniqueParentNo());
			profile.setParentName(name);
			profile.setPhone(phone);
			profile.setStatus("ACTIVE");
			profile = parents.save(profile);
		}

		Role role = roles.findByRoleCode("EDU_PARENT");
		if (role == null) role = roles.findByRoleCode("PARENT");
		if (role == null) throw new IllegalStateException("系统尚未配置家长角色");
		AdminUser account = new AdminUser();
		account.setUsername(uniqueParentUsername());
		account.setPassword(passwords.encode(opaqueToken()));
		account.setDisplayName(profile.getParentName());
		account.setPhone(phone);
		account.setOrganizationId(organizationId);
		account.setAccountType("PARENT");
		account.setAccountLocked(false);
		account.setFailedLoginAttempts(0);
		account.setMustChangePassword(false);
		account.setStatus(1);
		account.setRoles(new HashSet<>(Set.of(role)));
		account = users.save(account);

		EducationUserBinding binding = new EducationUserBinding();
		binding.setUsername(account.getUsername());
		binding.setProfileType("PARENT");
		binding.setProfileId(profile.getId());
		binding.setStatus("ACTIVE");
		binding.setVerifiedAt(LocalDateTime.now());
		profileBindings.save(binding);
		return new ParentAccount(profile, account);
	}

	private Map<String, String> issueTokens(AdminUser user) {
		Map<String, Object> claims = new HashMap<>();
		claims.put("tokenVersion", user.getTokenVersion() == null ? 0 : user.getTokenVersion());
		claims.put("mfaVerified", true);
		claims.put("loginChannel", "WECHAT_MINI_PROGRAM");
		String access = jwt.generateAccessToken(user.getUsername(), claims);
		String refresh = jwt.generateRefreshToken(user.getUsername(), claims);
		Claims parsed = jwt.parseToken(refresh);
		LocalDateTime expiry = Instant.ofEpochMilli(parsed.getExpiration().getTime())
				.atZone(ZoneId.systemDefault()).toLocalDateTime();
		refreshTokens.create(refresh, user.getUsername(), expiry);
		user.setLastLoginAt(LocalDateTime.now());
		users.save(user);
		return Map.of("accessToken", access, "refreshToken", refresh);
	}

	private EducationUserBinding requireProfileBinding(String type, String profileId) {
		return profileBindings.findByProfileTypeAndProfileIdAndStatus(type, profileId, "ACTIVE")
				.orElseThrow(() -> new IllegalStateException("档案尚未创建可登录账号，请联系学校管理员"));
	}

	private AdminUser requireActiveUser(String id) {
		AdminUser user = users.findById(id).orElseThrow(() -> new IllegalStateException("绑定的平台账号不存在"));
		return assertActive(user);
	}

	private AdminUser requireActiveUserByUsername(String username) {
		AdminUser user = users.findByUsername(username);
		if (user == null) throw new IllegalStateException("绑定的平台账号不存在");
		return assertActive(user);
	}

	private AdminUser assertActive(AdminUser user) {
		if (!Integer.valueOf(1).equals(user.getStatus()) || Boolean.TRUE.equals(user.getAccountLocked())) {
			throw new IllegalStateException("平台账号已停用或锁定");
		}
		return user;
	}

	private void assertOrganization(AdminUser user, String organizationId) {
		if (organizationId == null) return;
		if (user.getOrganizationId() == null || !organizationId.equals(user.getOrganizationId())) {
			throw new IllegalStateException("账号不属于当前学校");
		}
	}

	private boolean isPlatformAdministrator(AdminUser account) {
		return account.getRoles().stream()
				.map(Role::getRoleCode)
				.filter(java.util.Objects::nonNull)
				.anyMatch(value -> Set.of("SUPER_ADMIN", "ROLE_PLATFORM_ADMIN", "PLATFORM_ADMIN")
						.contains(value.toUpperCase(Locale.ROOT)));
	}

	private void assertVerifiedPhone(String actual, String... expectedCandidates) {
		for (String expected : expectedCandidates) {
			if (expected != null && !expected.isBlank() && actual.equals(normalizePhone(expected))) return;
		}
		throw new IllegalStateException("微信手机号与学校预留手机号不一致，请联系学校管理员更新资料");
	}

	private void assertUsable(GuardianBindingInvitation invitation) {
		if (!"PENDING".equals(invitation.getStatus())) throw new IllegalStateException("家长邀请已经使用或取消");
		if (invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
			invitation.setStatus("EXPIRED");
			invitations.save(invitation);
			throw new IllegalStateException("家长邀请已过期，请让学生重新生成");
		}
	}

	private void consume(MiniProgramBindingSession session) {
		session.setStatus("CONSUMED");
		session.setConsumedAt(LocalDateTime.now());
		sessions.save(session);
	}

	private SchoolView school(String organizationId) {
		return organizationId == null ? null : schoolView(requireSchool(organizationId));
	}

	private SchoolView schoolView(Organization organization) {
		return new SchoolView(
				organization.getId(), organization.getOrganizationName(), organization.getShortName(), organization.getOrgCode());
	}

	private Organization requireSchool(String organizationId) {
		Organization organization = organizations.findById(organizationId)
				.orElseThrow(() -> new IllegalArgumentException("学校不存在"));
		if (!Integer.valueOf(1).equals(organization.getStatus())) throw new IllegalStateException("学校已停用");
		return organization;
	}

	private InvitationView invitationView(GuardianBindingInvitation invitation) {
		if (invitation == null) return null;
		StudentProfile student = students.findById(invitation.getStudentId())
				.orElseThrow(() -> new IllegalStateException("邀请对应的学生档案不存在"));
		return new InvitationView(
				invitation.getSceneCode(), maskName(student.getStudentName()),
				invitation.getRelationship(), invitation.getExpiresAt());
	}

	private String uniqueParentNo() {
		for (int attempt = 0; attempt < 5; attempt++) {
			String value = "P" + randomCode(14);
			if (!parents.existsByParentNo(value)) return value;
		}
		throw new IllegalStateException("家长编号生成失败，请重试");
	}

	private String uniqueParentUsername() {
		for (int attempt = 0; attempt < 5; attempt++) {
			String value = "wxp_" + randomCode(16).toLowerCase(Locale.ROOT);
			if (users.findByUsername(value) == null) return value;
		}
		throw new IllegalStateException("家长账号生成失败，请重试");
	}

	private String scene(String prefix) {
		return prefix + randomCode(20);
	}

	private String randomCode(int length) {
		final char[] alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
		StringBuilder result = new StringBuilder(length);
		for (int index = 0; index < length; index++) result.append(alphabet[RANDOM.nextInt(alphabet.length)]);
		return result.toString();
	}

	private String opaqueToken() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private String hash(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception exception) {
			throw new IllegalStateException("无法生成安全令牌", exception);
		}
	}

	private String normalizePhone(String value) {
		String normalized = required(value, "手机号不能为空").replaceAll("[^0-9]", "");
		if (normalized.startsWith("86") && normalized.length() == 13) normalized = normalized.substring(2);
		if (normalized.length() < 6 || normalized.length() > 20) throw new IllegalArgumentException("手机号格式错误");
		return normalized;
	}

	private String maskName(String value) {
		if (value == null || value.isBlank()) return "学生";
		return value.length() == 1 ? value : value.substring(0, 1) + "同学";
	}

	private String required(String value, String message) {
		if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
		return value.trim();
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private record SceneContext(String organizationId, GuardianBindingInvitation invitation) {}
	private record ParentAccount(ParentProfile profile, AdminUser account) {}
}
