package com.chronos.education.scheduling.model;

import java.util.List;

public record BellScheduleView(
		BellSchedule schedule,
		List<BellPeriod> periods) {
}
