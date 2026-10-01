package com.chronos.education.meeting.model;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 会议材料只保存受控文件引用，文件内容和安全校验由 platform-file 负责。 */
@Entity
@Getter
@Setter
@Table(name = "edu_meeting_material")
public class MeetingMaterial extends BaseEntity {
	@Column(name = "meeting_id", nullable = false, length = 64)
	private String meetingId;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(name = "file_id", nullable = false, length = 64, unique = true)
	private String fileId;
}
