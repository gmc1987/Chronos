package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chronos.ai.service.AiModelChatService;
import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.ScheduleEntryRepository;
import com.chronos.education.scheduling.dao.TeacherAcademicProfileRepository;
import com.chronos.education.scheduling.model.AcademicTerm;
import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.education.scheduling.model.SchedulingAiRunRequest;
import com.chronos.education.scheduling.model.TeacherAcademicProfile;
import com.chronos.education.scheduling.model.ScheduleEntry;
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
		when(model.chatStructured(org.mockito.ArgumentMatchers.isNull(),
				org.mockito.ArgumentMatchers.eq("schedule.requirement.clauses.v1"),
				org.mockito.ArgumentMatchers.endsWith(input)))
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
	void afternoonRuleExpandsOnlyToVerifiedCampusPeriods() {
		AcademicTermRepository terms = mock(AcademicTermRepository.class);
		TeacherAcademicProfileRepository teachers = mock(TeacherAcademicProfileRepository.class);
		EducationDataScopeService scopes = mock(EducationDataScopeService.class);
		SchedulingAgentTimetableService timetable = mock(SchedulingAgentTimetableService.class);
		var scope = new EducationDataScope(true, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
		when(terms.findByTermCode("2026-2027-1")).thenReturn(Optional.of(new AcademicTerm()));
		when(scopes.resolve("admin")).thenReturn(scope);
		when(teachers.findAllByOrderByTeacherNo()).thenReturn(List.of(teacher("teacher-1", "张老师", "T001")));
		when(scopes.canAccessTeacher(scope, "teacher-1")).thenReturn(true);
		when(timetable.segment("2026-2027-1", "teacher-1", "GLOBAL", Set.of(), "AFTERNOON"))
				.thenReturn(new SchedulingAgentTimetableService.SegmentResolution(List.of(5, 6), null));
		var parser = new SchedulingAiRequirementParser(terms, mock(CourseOfferingRepository.class),
				teachers, scopes, null, timetable);
		var request = new SchedulingAiRunRequest("afternoon", "2026-2027-1", "GLOBAL",
				Set.of(), 1, "张老师周三下午不能上课");

		var plan = parser.parse(request, "admin").plan();

		assertThat(plan.readyForConfirmation()).isTrue();
		assertThat(plan.constraints()).extracting(item -> item.periodNo()).containsExactly(5, 6);
		assertThat(plan.constraints()).allSatisfy(item -> {
			assertThat(item.timePhrase()).isEqualTo("AFTERNOON");
			assertThat(item.strength()).isEqualTo("HARD");
		});
	}

	@Test
	void missingTimetableAndWeekendRulesCannotBeConfirmed() {
		AcademicTermRepository terms = mock(AcademicTermRepository.class);
		TeacherAcademicProfileRepository teachers = mock(TeacherAcademicProfileRepository.class);
		EducationDataScopeService scopes = mock(EducationDataScopeService.class);
		SchedulingAgentTimetableService timetable = mock(SchedulingAgentTimetableService.class);
		var scope = new EducationDataScope(true, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
		when(terms.findByTermCode("2026-2027-1")).thenReturn(Optional.of(new AcademicTerm()));
		when(scopes.resolve("admin")).thenReturn(scope);
		when(teachers.findAllByOrderByTeacherNo()).thenReturn(List.of(teacher("teacher-1", "张老师", "T001")));
		when(scopes.canAccessTeacher(scope, "teacher-1")).thenReturn(true);
		when(timetable.segment("2026-2027-1", "teacher-1", "GLOBAL", Set.of(), "MORNING"))
				.thenReturn(new SchedulingAgentTimetableService.SegmentResolution(
						List.of(), "教师排课范围没有唯一的校区"));
		var parser = new SchedulingAiRequirementParser(terms, mock(CourseOfferingRepository.class),
				teachers, scopes, null, timetable);

		var missing = parser.parse(new SchedulingAiRunRequest("missing", "2026-2027-1",
				"GLOBAL", Set.of(), 1, "张老师周三上午不能排"), "admin").plan();
		assertThat(missing.readyForConfirmation()).isFalse();
		assertThat(missing.constraints()).isEmpty();
		assertThat(missing.unresolvedClauses()).containsExactly("张老师周三上午不能排");
		var weekend = parser.parse(new SchedulingAiRunRequest("weekend", "2026-2027-1",
				"GLOBAL", Set.of(), 1, "张老师周六第3节不能上课"), "admin").plan();
		assertThat(weekend.readyForConfirmation()).isFalse();
		assertThat(weekend.unsupported()).anyMatch(item -> item.contains("周一至周五"));
	}

	@Test
	void resolvesConsecutivePreferenceOnlyForAuthorizedCourseOfferings() {
		AcademicTermRepository terms = mock(AcademicTermRepository.class);
		CourseOfferingRepository offerings = mock(CourseOfferingRepository.class);
		TeacherAcademicProfileRepository teachers = mock(TeacherAcademicProfileRepository.class);
		EducationDataScopeService scopes = mock(EducationDataScopeService.class);
		var scope = new EducationDataScope(true, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
		var offering = new com.chronos.education.scheduling.model.CourseOffering();
		offering.setId("offering-plc");
		offering.setCourseCode("PLC");
		offering.setCourseName("PLC 实训");
		offering.setStatus("ACTIVE");
		offering.setWeeklyLessons(2);
		when(terms.findByTermCode("2026-2027-1")).thenReturn(Optional.of(new AcademicTerm()));
		when(scopes.resolve("admin")).thenReturn(scope);
		when(offerings.findBySemesterCodeOrderByOfferingCode("2026-2027-1"))
				.thenReturn(List.of(offering));
		when(scopes.visibleOfferings(org.mockito.ArgumentMatchers.eq(scope),
				org.mockito.ArgumentMatchers.anyList()))
				.thenReturn(List.of(offering));
		var parser = new SchedulingAiRequirementParser(terms, offerings, teachers, scopes);
		var plan = parser.parse(new SchedulingAiRunRequest("plc", "2026-2027-1", "GLOBAL",
				Set.of(), 1, "PLC 实训尽量连堂"), "admin").plan();
		assertThat(plan.readyForConfirmation()).isTrue();
		assertThat(plan.offeringConstraints()).singleElement()
				.satisfies(rule -> {
					assertThat(rule.offeringId()).isEqualTo("offering-plc");
					assertThat(rule.periods()).isEqualTo(2);
				});
		var invalid = parser.parse(new SchedulingAiRunRequest("unknown", "2026-2027-1", "GLOBAL",
				Set.of(), 1, "未知课程尽量连堂"), "admin").plan();
		assertThat(invalid.readyForConfirmation()).isFalse();
		assertThat(invalid.unresolvedClauses()).containsExactly("未知课程尽量连堂");
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

	@Test
	void resolvesOddEvenAndContiguousWeekRulesToOneAuthorizedOffering() {
		AcademicTermRepository terms = mock(AcademicTermRepository.class);
		CourseOfferingRepository offerings = mock(CourseOfferingRepository.class);
		TeacherAcademicProfileRepository teachers = mock(TeacherAcademicProfileRepository.class);
		EducationDataScopeService scopes = mock(EducationDataScopeService.class);
		EducationDataScope scope = new EducationDataScope(
				true, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
		var offering = offering("offering-1", "BIO-101", "生物实验", "BIO-T1");
		AcademicTerm term = new AcademicTerm();
		term.setWeekCount(18);
		term.setStatus("ACTIVE");
		when(terms.findByTermCode("2026-2027-1")).thenReturn(Optional.of(term));
		when(scopes.resolve("admin")).thenReturn(scope);
		when(offerings.findBySemesterCodeOrderByOfferingCode("2026-2027-1"))
				.thenReturn(List.of(offering));
		when(scopes.visibleOfferings(org.mockito.ArgumentMatchers.eq(scope),
				org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of(offering));
		when(scopes.canAccessOffering(scope, offering)).thenReturn(true);
		var parser = new SchedulingAiRequirementParser(terms, offerings, teachers, scopes);

		var odd = parser.parse(request("odd", "BIO-T1仅单周"), "admin").plan();
		assertThat(odd.readyForConfirmation()).isTrue();
		assertThat(odd.weekRules()).singleElement().satisfies(rule -> {
			assertThat(rule.offeringId()).isEqualTo("offering-1");
			assertThat(rule.weekPattern()).isEqualTo("ODD");
			assertThat(rule.startWeek()).isEqualTo(1);
			assertThat(rule.endWeek()).isEqualTo(18);
			assertThat(rule.sourceText()).isEqualTo("BIO-T1仅单周");
		});

		var bounded = parser.parse(request("bounded", "生物实验第3周至第9周"), "admin").plan();
		assertThat(bounded.readyForConfirmation()).isTrue();
		assertThat(bounded.weekRules()).singleElement().satisfies(rule -> {
			assertThat(rule.weekPattern()).isEqualTo("ALL");
			assertThat(rule.startWeek()).isEqualTo(3);
			assertThat(rule.endWeek()).isEqualTo(9);
		});
		var outOfRange = parser.parse(request("invalid", "生物实验第3周至第19周"), "admin").plan();
		assertThat(outOfRange.readyForConfirmation()).isFalse();
		assertThat(outOfRange.weekRules()).isEmpty();
		assertThat(outOfRange.unresolvedClauses()).containsExactly("生物实验第3周至第19周");
		var noOddWeek = parser.parse(request("no-odd", "BIO-T1仅单周第2周至第2周"), "admin").plan();
		assertThat(noOddWeek.readyForConfirmation()).isFalse();
		assertThat(noOddWeek.weekRules()).isEmpty();
		assertThat(noOddWeek.clarifications()).anyMatch(value -> value.contains("没有交集"));
		var noEvenWeek = parser.parse(request("no-even", "BIO-T1仅双周第3周至第3周"), "admin").plan();
		assertThat(noEvenWeek.readyForConfirmation()).isFalse();
		assertThat(noEvenWeek.weekRules()).isEmpty();
	}

	@Test
	void failsClosedOnAmbiguousAndConflictingWeekRules() {
		AcademicTermRepository terms = mock(AcademicTermRepository.class);
		CourseOfferingRepository offerings = mock(CourseOfferingRepository.class);
		TeacherAcademicProfileRepository teachers = mock(TeacherAcademicProfileRepository.class);
		EducationDataScopeService scopes = mock(EducationDataScopeService.class);
		EducationDataScope scope = new EducationDataScope(
				true, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
		AcademicTerm term = new AcademicTerm();
		term.setWeekCount(18);
		term.setStatus("ACTIVE");
		var first = offering("offering-1", "BIO-101", "生物实验", "BIO-T1");
		var second = offering("offering-2", "BIO-101", "生物实验", "BIO-T2");
		when(terms.findByTermCode("2026-2027-1")).thenReturn(Optional.of(term));
		when(scopes.resolve("admin")).thenReturn(scope);
		when(offerings.findBySemesterCodeOrderByOfferingCode("2026-2027-1"))
				.thenReturn(List.of(first, second));
		when(scopes.visibleOfferings(org.mockito.ArgumentMatchers.eq(scope),
				org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of(first, second));
		var parser = new SchedulingAiRequirementParser(terms, offerings, teachers, scopes);

		var ambiguous = parser.parse(request("ambiguous", "生物实验仅双周"), "admin").plan();
		assertThat(ambiguous.readyForConfirmation()).isFalse();
		assertThat(ambiguous.weekRules()).isEmpty();
		assertThat(ambiguous.clarifications()).anyMatch(value -> value.contains("多个匹配"));

		var conflicting = parser.parse(request("conflict",
				"BIO-T1仅单周；BIO-T1仅双周"), "admin").plan();
		assertThat(conflicting.readyForConfirmation()).isFalse();
		assertThat(conflicting.weekRules()).hasSize(1);
		assertThat(conflicting.clarifications()).anyMatch(value -> value.contains("相互冲突"));

		var duplicate = parser.parse(request("duplicate",
				"BIO-T1仅单周；BIO-T1仅单周"), "admin").plan();
		assertThat(duplicate.readyForConfirmation()).isFalse();
		assertThat(duplicate.unresolvedClauses()).containsExactly("BIO-T1仅单周");
		assertThat(duplicate.clarifications()).anyMatch(value -> value.contains("重复指定"));
	}

	@Test
	void resolvesTemporaryLockFromCurrentSemesterAndAuthorizedTargetAndTeacherSoftPriorities() {
		AcademicTermRepository terms = mock(AcademicTermRepository.class);
		CourseOfferingRepository offerings = mock(CourseOfferingRepository.class);
		TeacherAcademicProfileRepository teachers = mock(TeacherAcademicProfileRepository.class);
		ScheduleEntryRepository entries = mock(ScheduleEntryRepository.class);
		EducationDataScopeService scopes = mock(EducationDataScopeService.class);
		EducationDataScope scope = new EducationDataScope(
				true, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
		var offering = offering("offering-1", "BIO-101", "生物实验", "BIO-T1");
		var teacher = teacher("teacher-1", "张老师", "T001");
		var entry = new ScheduleEntry();
		entry.setId("entry-17");
		entry.setSemesterCode("2026-2027-1");
		entry.setOfferingId("offering-1");
		entry.setStatus("SCHEDULED");
		entry.setDayOfWeek(3);
		entry.setPeriodNo(4);
		AcademicTerm term = new AcademicTerm();
		term.setWeekCount(18);
		term.setStatus("ACTIVE");
		when(terms.findByTermCode("2026-2027-1")).thenReturn(Optional.of(term));
		when(scopes.resolve("admin")).thenReturn(scope);
		when(offerings.findBySemesterCodeOrderByOfferingCode("2026-2027-1"))
				.thenReturn(List.of(offering));
		when(offerings.findById("offering-1")).thenReturn(Optional.of(offering));
		when(scopes.visibleOfferings(org.mockito.ArgumentMatchers.eq(scope),
				org.mockito.ArgumentMatchers.anyList())).thenReturn(List.of(offering));
		when(scopes.canAccessOffering(scope, offering)).thenReturn(true);
		when(entries.findById("entry-17")).thenReturn(Optional.of(entry));
		when(entries.findBySemesterCodeOrderByDayOfWeekAscPeriodNoAsc("2026-2027-1"))
				.thenReturn(List.of(entry));
		when(teachers.findAllByOrderByTeacherNo()).thenReturn(List.of(teacher));
		when(scopes.canAccessTeacher(scope, "teacher-1")).thenReturn(true);
		var parser = new SchedulingAiRequirementParser(terms, offerings, teachers, scopes,
				null, null, entries);

		var plan = parser.parse(request("lock-priority",
				"保留现有课表条目 entry-17；张老师仅单周；张老师减少空档；张老师同一天集中"),
				"admin").plan();
		assertThat(plan.readyForConfirmation()).isTrue();
		assertThat(plan.weekRules()).singleElement()
				.satisfies(rule -> assertThat(rule.offeringId()).isEqualTo("offering-1"));
		assertThat(plan.lockedEntries()).singleElement().satisfies(item -> {
			assertThat(item.entryId()).isEqualTo("entry-17");
			assertThat(item.offeringId()).isEqualTo("offering-1");
			assertThat(item.sourceText()).isEqualTo("保留现有课表条目 entry-17");
		});
		assertThat(plan.softPriorities()).extracting(item -> item.kind())
				.containsExactly("TEACHER_GAP", "SAME_DAY");

		var slotLock = parser.parse(request("slot-lock", "保留BIO-T1周三第4节"), "admin").plan();
		assertThat(slotLock.readyForConfirmation()).isTrue();
		assertThat(slotLock.lockedEntries()).singleElement()
				.satisfies(item -> assertThat(item.entryId()).isEqualTo("entry-17"));
		var uniqueCourseLock = parser.parse(request("unique-course-lock", "保留BIO-T1"),
				"admin").plan();
		assertThat(uniqueCourseLock.readyForConfirmation()).isTrue();
		assertThat(uniqueCourseLock.lockedEntries()).singleElement()
				.satisfies(item -> assertThat(item.entryId()).isEqualTo("entry-17"));

		var secondEntry = new ScheduleEntry();
		secondEntry.setId("entry-18");
		secondEntry.setSemesterCode("2026-2027-1");
		secondEntry.setOfferingId("offering-1");
		secondEntry.setStatus("SCHEDULED");
		secondEntry.setDayOfWeek(4);
		secondEntry.setPeriodNo(5);
		when(entries.findBySemesterCodeOrderByDayOfWeekAscPeriodNoAsc("2026-2027-1"))
				.thenReturn(List.of(entry, secondEntry));
		var unbounded = parser.parse(request("unbounded-lock", "保留BIO-T1"), "admin").plan();
		assertThat(unbounded.readyForConfirmation()).isFalse();
		assertThat(unbounded.lockedEntries()).isEmpty();
		assertThat(unbounded.clarifications()).anyMatch(value -> value.contains("多个现有课表项"));

		entry.setSemesterCode("2025-2026-2");
		var stale = parser.parse(request("stale-lock", "保留现有课表条目 entry-17"), "admin").plan();
		assertThat(stale.readyForConfirmation()).isFalse();
		assertThat(stale.lockedEntries()).isEmpty();
	}

	private SchedulingAiRunRequest request(String id, String text) {
		return new SchedulingAiRunRequest(id, "2026-2027-1", "GLOBAL", Set.of(), 1, text);
	}

	private com.chronos.education.scheduling.model.CourseOffering offering(
			String id, String courseCode, String courseName, String offeringCode) {
		var offering = new com.chronos.education.scheduling.model.CourseOffering();
		offering.setId(id);
		offering.setCourseCode(courseCode);
		offering.setCourseName(courseName);
		offering.setOfferingCode(offeringCode);
		offering.setTeachingClassName(offeringCode);
		offering.setTeacherId("teacher-1");
		offering.setTeacherName("张老师");
		offering.setStatus("ACTIVE");
		offering.setWeeklyLessons(2);
		return offering;
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
