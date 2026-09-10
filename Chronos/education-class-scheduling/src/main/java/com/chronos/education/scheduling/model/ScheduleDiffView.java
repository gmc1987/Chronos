package com.chronos.education.scheduling.model;

import java.util.List;

public record ScheduleDiffView(
		int added,
		int removed,
		int moved,
		int unchanged,
		List<ScheduleDiffItem> items) {
}
