package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chronos.ai.service.AiModelChatService;
import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.TeacherAcademicProfileRepository;
import com.chronos.education.scheduling.model.AcademicTerm;
import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.education.scheduling.model.SchedulingAiRunRequest;
import com.chronos.education.scheduling.model.TeacherAcademicProfile;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class SchedulingAiRequirementParserTest {
	@Test
	void modelMayRejectButCannotOverrideServerTeacherResolution() {
		AcademicTermRepository terms = mock(AcademicTermRepository.class);
		TeacherAcademicProfileRepository teachers = mock(TeacherAcademicProfileRepository.class);
		EducationDataScopeService scopes = mock(EducationDataScopeService.class);
		AiModelChatService model = mock(AiModelChatService.class);
		String input = "张老师周三第3节不能上课";
		when(model.chatStructured(null, "schedule.requirement.clauses.v1", input))
				.thenReturn("""
						{"clauses":[{"text":"张老师周三第3节不能上课","classification":"TEACHER_SLOT"}]}
						""", """
						{"clauses":[{"text":"张老师周三第3节不能上课","classification":"UNSUPPORTED"}]}
						""");
		var scope = new EducationDataScope(true, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
		when(scopes.resolve("admin")).thenReturn(scope);
		when(terms.findByTermCode("2026-2027-1")).thenReturn(Optional.of(new AcademicTerm()));
		when(teachers.findAllByOrderByTeacherNo()).thenReturn(List.of(teacher("real-id", "张老师", "T001")));
		when(scopes.canAccessTeacher(scope, "real-id")).thenReturn(true);
		var parser = new SchedulingAiRequirementParser(terms, mock(CourseOfferingRepository.class),
				teachers, scopes, new SchedulingAiModelClassifier(model, new ObjectMapper()));
		var request = new SchedulingAiRunRequest("model-test", "2026-2027-1", "GLOBAL",
				Set.of(), 1, input);

		var supported = parser.parse(request, "admin").plan();
		assertThat(supported.readyForConfirmation()).isTrue();
		assertThat(supported.constraints().getFirst().teacherId()).isEqualTo("real-id");
		var rejected = parser.parse(request, "admin").plan();
		assertThat(rejected.readyForConfirmation()).isFalse();
		assertThat(rejected.unsupported()).isNotEmpty();
	}

	@Test
	void resolvesAuthoritativeTeacherAndTimeWithoutAcceptingModelIds() {
		AcademicTermRepository terms = mock(AcademicTermRepository.class);
		CourseOfferingRepository offerings = mock(CourseOfferingRepository.class);
		TeacherAcademicProfileRepository teachers = mock(TeacherAcademicProfileRepository.class);
		EducationDataScopeService scopes = mock(EducationDataScopeService.class);
		TeacherAcademicProfile teacher = teacher("teacher-real", "张老师", "T001");
		EducationDataScope scope = new EducationDataScope(
				true, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
		when(terms.findByTermCode("2026-2027-1")).thenReturn(Optional.of(new AcademicTerm()));
		when(scopes.resolve("admin")).thenReturn(scope);
		when(teachers.findAllByOrderByTeacherNo()).thenReturn(List.of(teacher));
		when(scopes.canAccessTeacher(scope, "teacher-real")).thenReturn(true);
		SchedulingAiRequirementParser parser = new SchedulingAiRequirementParser(
				terms, offerings, teachers, scopes);

		var parsed = parser.parse(new SchedulingAiRunRequest(
				"request-1",
				"2026-2027-1",
				"GLOBAL",
				Set.of(),
				3,
				"张老师周三第3节不能上课"),
				"admin");

		assertThat(parsed.plan().readyForConfirmation()).isTrue();
		assertThat(parsed.plan().constraints())
				.singleElement()
				.satisfies(constraint -> {
					assertThat(constraint.teacherId()).isEqualTo("teacher-real");
					assertThat(constraint.dayOfWeek()).isEqualTo(3);
					assertThat(constraint.periodNo()).isEqualTo(3);
				});
	}

	@Test
	void unknownRulesCannotBeConfirmedOrSilentlyIgnored() {
		AcademicTermRepository terms = mock(AcademicTermRepository.class);
		CourseOfferingRepository offerings = mock(CourseOfferingRepository.class);
		TeacherAcademicProfileRepository teachers = mock(TeacherAcademicProfileRepository.class);
		EducationDataScopeService scopes = mock(EducationDataScopeService.class);
		when(terms.findByTermCode("2026-2027-1")).thenReturn(Optional.of(new AcademicTerm()));
		when(scopes.resolve("admin")).thenReturn(new EducationDataScope(
				true, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of()));
		when(teachers.findAllByOrderByTeacherNo()).thenReturn(List.of());
		SchedulingAiRequirementParser parser = new SchedulingAiRequirementParser(
				terms, offerings, teachers, scopes);

		var parsed = parser.parse(new SchedulingAiRunRequest(
				"unknown-1", "2026-2027-1", "GLOBAL", Set.of(), 3,
				"帮我排课；数学课尽量安排在上午"), "admin");

		assertThat(parsed.plan().readyForConfirmation()).isFalse();
		assertThat(parsed.plan().unsupported()).anyMatch(value -> value.contains("数学课"));
	}

	@Test
	void multipleSlotsWithinOneClauseCannotSilentlyUseTheFirstSlot() {
		AcademicTermRepository terms = mock(AcademicTermRepository.class);
		TeacherAcademicProfileRepository teachers = mock(TeacherAcademicProfileRepository.class);
		EducationDataScopeService scopes = mock(EducationDataScopeService.class);
		var scope = new EducationDataScope(true, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
		when(terms.findByTermCode("2026-2027-1")).thenReturn(Optional.of(new AcademicTerm()));
		when(scopes.resolve("admin")).thenReturn(scope);
		when(teachers.findAllByOrderByTeacherNo()).thenReturn(List.of(teacher("teacher-1", "张老师", "T001")));
		when(scopes.canAccessTeacher(scope, "teacher-1")).thenReturn(true);
		var parser = new SchedulingAiRequirementParser(terms, mock(CourseOfferingRepository.class),
				teachers, scopes);

		var result = parser.parse(new SchedulingAiRunRequest("multi", "2026-2027-1", "GLOBAL",
				Set.of(), 1, "张老师周三第3节和第4节不能上课"), "admin");

		assertThat(result.plan().readyForConfirmation()).isFalse();
		assertThat(result.plan().constraints()).isEmpty();
		assertThat(result.plan().unsupported()).anyMatch(value -> value.contains("多个时段"));
	}

	@Test
	void ambiguousTeacherEntersClarificationInsteadOfGuessing() {
		AcademicTermRepository terms = mock(AcademicTermRepository.class);
		CourseOfferingRepository offerings = mock(CourseOfferingRepository.class);
		TeacherAcademicProfileRepository teachers = mock(TeacherAcademicProfileRepository.class);
		EducationDataScopeService scopes = mock(EducationDataScopeService.class);
		EducationDataScope scope = new EducationDataScope(
				true, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
		when(terms.findByTermCode("2026-2027-1")).thenReturn(Optional.of(new AcademicTerm()));
		when(scopes.resolve("admin")).thenReturn(scope);
		when(teachers.findAllByOrderByTeacherNo()).thenReturn(List.of(
				teacher("teacher-1", "张老师", "T001"),
				teacher("teacher-2", "张老师", "T002")));
		when(scopes.canAccessTeacher(scope, "teacher-1")).thenReturn(true);
		when(scopes.canAccessTeacher(scope, "teacher-2")).thenReturn(true);
		SchedulingAiRequirementParser parser = new SchedulingAiRequirementParser(
				terms, offerings, teachers, scopes);

		var parsed = parser.parse(new SchedulingAiRunRequest(
				"request-2", "2026-2027-1", "GLOBAL", Set.of(), 1,
				"张老师周三第3节不能上课"), "admin");

		assertThat(parsed.plan().readyForConfirmation()).isFalse();
		assertThat(parsed.plan().clarifications()).anyMatch(value -> value.contains("多个匹配"));
		assertThat(parsed.plan().constraints()).isEmpty();
	}

	private TeacherAcademicProfile teacher(String id, String name, String no) {
		TeacherAcademicProfile teacher = new TeacherAcademicProfile();
		teacher.setId(id);
		teacher.setTeacherName(name);
		teacher.setTeacherNo(no);
		teacher.setEnabled(true);
		return teacher;
	}
}
