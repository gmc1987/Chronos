package com.chronos.education.meeting.service;

import org.springframework.stereotype.Component;

import com.chronos.file.service.ManagedFileAccessPolicy;
import com.chronos.education.meeting.dao.MeetingMaterialRepository;
import com.chronos.education.meeting.dao.MeetingParticipantRepository;
import com.chronos.education.meeting.dao.MeetingRepository;

/** 会议组织者和参会人可以读取材料，只有组织者可以删除材料文件。 */
@Component
public class MeetingManagedFileAccessPolicy implements ManagedFileAccessPolicy {
	private final MeetingMaterialRepository materials;
	private final MeetingRepository meetings;
	private final MeetingParticipantRepository participants;

	public MeetingManagedFileAccessPolicy(
			MeetingMaterialRepository materials,
			MeetingRepository meetings,
			MeetingParticipantRepository participants) {
		this.materials = materials;
		this.meetings = meetings;
		this.participants = participants;
	}

	@Override
	public boolean canRead(String username, String businessType, String businessId) {
		return "EDUCATION_MEETING".equals(businessType)
				&& materials.findById(businessId)
						.flatMap(material -> meetings.findById(material.getMeetingId()))
						.map(meeting -> username.equals(meeting.getOrganizerUsername())
								|| participants.findByMeetingIdAndUsername(
										meeting.getId(), username).isPresent())
						.orElse(false);
	}

	@Override
	public boolean canWrite(String username, String businessType, String businessId) {
		return "EDUCATION_MEETING".equals(businessType)
				&& materials.findById(businessId)
						.flatMap(material -> meetings.findById(material.getMeetingId()))
						.map(meeting -> username.equals(meeting.getOrganizerUsername()))
						.orElse(false);
	}

	@Override
	public boolean canWrite(
			String username,
			String businessType,
			String businessId,
			String fileId) {
		return canWrite(username, businessType, businessId);
	}
}
