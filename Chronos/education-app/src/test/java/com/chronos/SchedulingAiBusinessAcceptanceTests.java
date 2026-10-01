package com.chronos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.IRoleDataScopeRepository;
import com.chronos.Idao.IRoleRepository;
import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.dao.ClassroomRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.ScheduleCandidatePlanRepository;
import com.chronos.education.scheduling.dao.ScheduleEntryRepository;
import com.chronos.education.scheduling.dao.TeacherAcademicProfileRepository;
import com.chronos.education.scheduling.model.AcademicTerm;
import com.chronos.education.scheduling.model.AutoScheduleCommand;
import com.chronos.education.scheduling.model.Classroom;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.SchedulingAiConfirmRequest;
import com.chronos.education.scheduling.model.SchedulingAiRunRequest;
import com.chronos.education.scheduling.model.TeacherAcademicProfile;
import com.chronos.education.scheduling.service.AutoSchedulingService;
import com.chronos.education.scheduling.service.SchedulingAiRunService;
import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.education.scheduling.service.SchedulePlanVersionService;
import com.chronos.model.pojo.AdminUser;
import com.chronos.model.pojo.RoleDataScope;
import com.chronos.security.AdminUserDetailsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

/** Runs only on an explicitly selected disposable PostgreSQL acceptance database. */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "CHRONOS_ACCEPTANCE_CLONE", matches = "true")
@EnabledIfEnvironmentVariable(named = "CHRONOS_AI_BUSINESS_ACCEPTANCE", matches = "true")
@EnabledIfEnvironmentVariable(
		named = "CHRONOS_DB_URL",
		matches = "jdbc:postgresql:.*/[^/?]*(test|verify)[^/?]*(\\?.*)?")
class SchedulingAiBusinessAcceptanceTests {
	@Autowired private AcademicTermRepository terms;
	@Autowired private TeacherAcademicProfileRepository teachers;
	@Autowired private CourseOfferingRepository offerings;
	@Autowired private ClassroomRepository classrooms;
	@Autowired private ScheduleEntryRepository entries;
	@Autowired private ScheduleCandidatePlanRepository candidates;
	@Autowired private SchedulingAiRunService runs;
	@Autowired private AutoSchedulingService scheduling;
	@Autowired private EducationDataScopeService scopes;
	@Autowired private TransactionTemplate transactions;
	@Autowired private ObjectMapper json;
	@Autowired private IAdminUserRepository users;
	@Autowired private IRoleDataScopeRepository roleScopes;
	@Autowired private IRoleRepository roles;
	@Autowired private AdminUserDetailsService identities;
	@Autowired private SchedulePlanVersionService versions;
	@Autowired private PasswordEncoder passwordEncoder;

	@AfterEach
	void clearAuthentication() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void liveModelToConfirmedRunToVerifiedCandidateLeavesOrdinarySchedulingUnchanged()
			throws Exception {
		String actor = "acceptance_admin";
		transactions.executeWithoutResult(ignored -> {
			var user = users.findByUsername(actor);
			if (user == null) {
				var role = roles.findByRoleCode("SUPER_ADMIN");
				assertThat(role).as("the copied database needs the platform administrator role")
						.isNotNull();
				user = new AdminUser();
				user.setUsername(actor);
				user.setPassword(passwordEncoder.encode("Aa!1" + UUID.randomUUID()));
				user.setDisplayName("隔离验收排课员");
				user.setStatus(1);
				user.getRoles().add(role);
				user = users.saveAndFlush(user);
			}
			assertThat(user).isNotNull();
			String roleId = user.getRoles().iterator().next().getId();
			if (roleScopes.findByRoleIdIn(List.of(roleId)).stream()
					.noneMatch(item -> "ALL".equals(item.getScopeType()))) {
				RoleDataScope fullAccess = new RoleDataScope();
				fullAccess.setRoleId(roleId);
				fullAccess.setScopeType("ALL");
				roleScopes.saveAndFlush(fullAccess);
			}
			if (users.findByUsername("acceptance_reviewer") == null) {
				AdminUser reviewer = new AdminUser();
				reviewer.setUsername("acceptance_reviewer");
				reviewer.setPassword(user.getPassword());
				reviewer.setDisplayName("隔离验收审核员");
				reviewer.setStatus(1);
				reviewer.getRoles().addAll(user.getRoles());
				users.saveAndFlush(reviewer);
			}
		});
		assertThat(scopes.resolve(actor).fullAccess()).isTrue();
		var identity = identities.loadUserByUsername(actor);
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(identity, null, identity.getAuthorities()));
		String termCode = "AI-VERIFY-" + UUID.randomUUID().toString().substring(0, 8);
		CourseOffering offering = transactions.execute(ignored -> {
			AcademicTerm term = new AcademicTerm();
			term.setTermCode(termCode);
			term.setTermName("AI 端到端验收学期");
			term.setAcademicYear("2026-2027");
			term.setTermNo(1);
			term.setStartDate(LocalDate.of(2026, 9, 1));
			term.setEndDate(LocalDate.of(2027, 1, 15));
			term.setWeekCount(18);
			terms.saveAndFlush(term);

			TeacherAcademicProfile teacher = new TeacherAcademicProfile();
			teacher.setEmployeeId("ai-verify-" + termCode);
			teacher.setTeacherNo("T-" + termCode);
			teacher.setTeacherName("验收教师");
			teacher = teachers.saveAndFlush(teacher);

			Classroom room = new Classroom();
			room.setRoomCode("R-" + termCode);
			room.setRoomName("AI 验收教室");
			room.setCampusId("ai-verify-campus");
			room.setCapacity(40);
			classrooms.saveAndFlush(room);

			CourseOffering source = new CourseOffering();
			source.setSemesterCode(termCode);
			source.setOfferingCode("O-" + termCode);
			source.setCourseCode("AI-VERIFY-COURSE");
			source.setCourseName("AI 验收课程");
			source.setTeachingClassName("AI 验收教学班");
			source.setTeacherId(teacher.getId());
			source.setTeacherName(teacher.getTeacherName());
			source.setCampusId(room.getCampusId());
			source.setStudentCount(1);
			source.setWeeklyLessons(1);
			return offerings.saveAndFlush(source);
		});
		assertThat(offering).isNotNull();

		int formalEntriesBefore = entries.findBySemesterCodeOrderByDayOfWeekAscPeriodNoAsc(termCode).size();
		String requestText = "验收教师周一第1节和第2节不能上课";
		var request = new SchedulingAiRunRequest("acceptance-" + termCode,
				termCode, "LOCAL", Set.of(offering.getId()), 2, requestText);
		var draft = runs.create(request, actor);
		assertThat(runs.create(request, actor).id()).isEqualTo(draft.id());
		assertThat(draft.status()).isEqualTo("READY_FOR_CONFIRMATION");
		assertThat(draft.plan().slotRules()).hasSize(2)
				.allSatisfy(rule -> {
					assertThat(rule.targetId()).isEqualTo(offering.getTeacherId());
					assertThat(rule.dayOfWeek()).isEqualTo(1);
					assertThat(rule.sourceText()).isEqualTo(requestText);
				});
		var confirmed = runs.confirm(draft.id(),
				new SchedulingAiConfirmRequest(draft.planVersion()), actor);
		assertThat(confirmed.status()).isEqualTo("CONFIRMED");
		var queued = runs.generate(draft.id(), actor);
		assertThat(queued.status()).isIn("QUEUED", "RUNNING", "CANDIDATES_READY");
		assertThat(queued.relatedJobId()).isNotBlank();

		var deadline = java.time.Instant.now().plus(Duration.ofSeconds(90));
		var current = runs.get(draft.id(), actor, false);
		while (Set.of("QUEUED", "RUNNING").contains(current.status())
				&& java.time.Instant.now().isBefore(deadline)) {
			Thread.sleep(250);
			current = runs.get(draft.id(), actor, false);
		}
		assertThat(current.status()).describedAs("AI job failure: %s", current.errorMessage())
				.isEqualTo("CANDIDATES_READY");
		var aiCandidate = runs.candidates(draft.id(), actor, false);
		assertThat(aiCandidate).hasSize(2).allSatisfy(candidate -> {
			assertThat(candidate.unscheduledLessons()).isZero();
			assertThat(candidate.metrics().slotRuleChecks()).hasSize(2)
					.allSatisfy(check -> assertThat(check.violations()).isZero());
		});
		assertThat(runs.compare(draft.id(), actor, aiCandidate.stream()
				.map(candidate -> candidate.id()).toList())).hasSize(2);
		assertThat(runs.preview(draft.id(), actor, aiCandidate.getFirst().id())).isNotNull();
		var aiEntries = json.readTree(candidates.findById(aiCandidate.getFirst().id())
				.orElseThrow().getSnapshotJson());
		assertThat(aiEntries).singleElement().satisfies(entry -> {
			assertThat(entry.path("dayOfWeek").asInt()).isEqualTo(1);
			assertThat(entry.path("periodNo").asInt()).isGreaterThan(2);
		});
		var ordinary = scheduling.generate(new AutoScheduleCommand(termCode, "ordinary",
				"LOCAL", Set.of(offering.getId()), 1, 5, 8, 1, 18), actor).getFirst();
		var ordinaryEntries = json.readTree(candidates.findById(ordinary.id())
				.orElseThrow().getSnapshotJson());
		assertThat(ordinaryEntries).singleElement().satisfies(entry -> {
			assertThat(entry.path("dayOfWeek").asInt()).isEqualTo(1);
			assertThat(entry.path("periodNo").asInt()).isEqualTo(1);
		});
		assertThat(entries.findBySemesterCodeOrderByDayOfWeekAscPeriodNoAsc(termCode))
				.hasSize(formalEntriesBefore);
		String candidateId = aiCandidate.getFirst().id();
		assertThatThrownBy(() -> scheduling.apply(candidateId, actor))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("审核");
		assertThat(scheduling.submitReview(candidateId, actor).reviewStatus())
				.isEqualTo("SUBMITTED");
		assertThatThrownBy(() -> scheduling.review(candidateId, true, "验收", actor))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("不能审核自己的方案");
		var reviewer = identities.loadUserByUsername("acceptance_reviewer");
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(reviewer, null, reviewer.getAuthorities()));
		assertThat(scheduling.review(candidateId, true, "已核验", "acceptance_reviewer")
				.reviewStatus()).isEqualTo("APPROVED");
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(identity, null, identity.getAuthorities()));
		assertThat(scheduling.apply(candidateId, actor).status()).isEqualTo("APPLIED");
		assertThat(entries.findBySemesterCodeOrderByDayOfWeekAscPeriodNoAsc(termCode))
				.singleElement().satisfies(entry -> {
					assertThat(entry.getDayOfWeek()).isEqualTo(1);
					assertThat(entry.getPeriodNo()).isGreaterThan(2);
				});
		assertThat(versions.publish(termCode, actor).getVersionNo()).isEqualTo(1);
		assertThat(versions.versions(termCode)).hasSize(1);
	}
}
