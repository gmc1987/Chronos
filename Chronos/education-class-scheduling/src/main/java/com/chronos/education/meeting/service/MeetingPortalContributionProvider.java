package com.chronos.education.meeting.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.education.meeting.model.MeetingView;
import com.chronos.portal.spi.PortalContribution;
import com.chronos.portal.spi.PortalContributionProvider;

/** Adds the user's meeting summary to the single portal bootstrap response. */
@Component
public class MeetingPortalContributionProvider implements PortalContributionProvider {
	private final MeetingCenterService meetings;

	public MeetingPortalContributionProvider(MeetingCenterService meetings) {
		this.meetings = meetings;
	}

	@Override
	public String providerCode() {
		return "MEETING";
	}

	@Override
	@Transactional(readOnly = true)
	public PortalContribution load(String username) {
		LocalDateTime now = LocalDateTime.now();
		List<MeetingView> upcoming = meetings.myMeetings(username).stream()
				.filter(value -> !"CANCELLED".equals(value.meeting().getStatus()))
				.filter(value -> value.meeting().getEndTime() == null
						|| !value.meeting().getEndTime().isBefore(now))
				.sorted(Comparator.comparing(value -> value.meeting().getStartTime(),
						Comparator.nullsLast(Comparator.naturalOrder())))
				.limit(20)
				.toList();
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("total", upcoming.size());
		data.put("meetings", upcoming);
		data.put("allRoute", "/portal/education/meetings");
		return new PortalContribution(providerCode(), true, "ok", data);
	}
}
