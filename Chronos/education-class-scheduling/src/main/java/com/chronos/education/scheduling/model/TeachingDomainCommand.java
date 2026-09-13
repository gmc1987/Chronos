package com.chronos.education.scheduling.model;

import java.util.Map;

/** Domain API payload. Domain-specific fields are carried in attributes so the
 * API can evolve without introducing another persistence model or table. */
public record TeachingDomainCommand(
		String offeringId,
		String scheduleEntryId,
		String title,
		String name,
		String status,
		Map<String, Object> attributes) {
	public Map<String, Object> toMap() {
		Map<String, Object> values = new java.util.HashMap<>();
		if (attributes != null) values.putAll(attributes);
		put(values, "offeringId", offeringId);
		put(values, "scheduleEntryId", scheduleEntryId);
		put(values, "title", title);
		put(values, "name", name);
		put(values, "status", status);
		return values;
	}

	private static void put(Map<String, Object> values, String key, Object value) {
		if (value != null) values.put(key, value);
	}
}
