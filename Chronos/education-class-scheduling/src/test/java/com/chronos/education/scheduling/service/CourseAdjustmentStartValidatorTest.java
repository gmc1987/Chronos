package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chronos.education.scheduling.dao.ScheduleEntryRepository;
import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.education.scheduling.model.ScheduleEntry;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class CourseAdjustmentStartValidatorTest {
	private ScheduleEntryRepository entries;
	private EducationDataScopeService dataScopes;
	private CourseAdjustmentStartValidator validator;
	private EducationDataScope scope;

	@BeforeEach
	void setUp() {
		entries = mock(ScheduleEntryRepository.class);
		dataScopes = mock(EducationDataScopeService.class);
		validator = new CourseAdjustmentStartValidator(entries, dataScopes);
		scope = new EducationDataScope(
				false,
				Set.of("campus-1"),
				Set.of(),
				Set.of(),
				Set.of());
		when(dataScopes.resolve("teacher.one")).thenReturn(scope);
		ScheduleEntry source = new ScheduleEntry();
		source.setId("entry-1");
		source.setClassroomId("room-1");
		when(entries.findById("entry-1")).thenReturn(Optional.of(source));
	}

	@Test
	void validatesSourceAndTargetRoomBeforeMoveStarts() {
		validator.validate("teacher.one", Map.of(
				"scheduleEntryId",
				"entry-1",
				"adjustmentType",
				"MOVE",
				"targetClassroomId",
				"room-2"));

		verify(dataScopes).assertScheduleEntryAccess(scope, "entry-1");
		verify(dataScopes).assertClassroomAccess(scope, "room-2");
	}

	@Test
	void propagatesCrossCampusTargetRoomDenial() {
		org.mockito.Mockito.doThrow(new AccessDeniedException("无权访问该教室"))
				.when(dataScopes)
				.assertClassroomAccess(scope, "room-other-campus");

		assertThatThrownBy(() -> validator.validate("teacher.one", Map.of(
				"scheduleEntryId",
				"entry-1",
				"adjustmentType",
				"MAKEUP",
				"targetClassroomId",
				"room-other-campus")))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessage("无权访问该教室");
	}

	@Test
	void rejectsUnsupportedAdjustmentType() {
		assertThatThrownBy(() -> validator.validate("teacher.one", Map.of(
				"scheduleEntryId",
				"entry-1",
				"adjustmentType",
				"DELETE_ALL")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("不支持的调整类型：DELETE_ALL");
	}
}
