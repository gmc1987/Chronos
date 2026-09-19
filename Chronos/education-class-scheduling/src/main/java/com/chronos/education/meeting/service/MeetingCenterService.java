package com.chronos.education.meeting.service;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.education.meeting.dao.MeetingParticipantRepository;
import com.chronos.education.meeting.dao.MeetingMaterialRepository;
import com.chronos.education.meeting.dao.MeetingMinutesRepository;
import com.chronos.education.meeting.dao.MeetingActionItemRepository;
import com.chronos.education.meeting.dao.MeetingRepository;
import com.chronos.education.meeting.dao.MeetingRoomRepository;
import com.chronos.education.meeting.model.Meeting;
import com.chronos.education.meeting.model.MeetingCommands;
import com.chronos.education.meeting.model.MeetingParticipant;
import com.chronos.education.meeting.model.MeetingMaterial;
import com.chronos.education.meeting.model.MeetingMinutes;
import com.chronos.education.meeting.model.MeetingActionItem;
import com.chronos.education.meeting.model.MeetingRoom;
import com.chronos.education.meeting.model.MeetingView;
import com.chronos.model.pojo.AdminUser;
import com.chronos.service.iService.IAuditLogService;
import com.chronos.file.service.ManagedFileService;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MeetingCenterService {
	private static final Set<String> ACTIVE_ROOM_STATUSES = Set.of(
			"PENDING_ROOM", "PUBLISHED");

	private final MeetingRoomRepository rooms;
	private final MeetingRepository meetings;
	private final MeetingParticipantRepository participants;
	private final MeetingMaterialRepository materials;
	private final MeetingMinutesRepository minutes;
	private final MeetingActionItemRepository actionItems;
	private final IAdminUserRepository users;
	private final ManagedFileService managedFiles;
	private final MeetingNotificationService notifications;
	private final IAuditLogService auditLogs;
	private final EntityManager entityManager;

	@Transactional(readOnly = true)
	public List<MeetingRoom> rooms() {
		return rooms.findAllByOrderByRoomNameAsc();
	}

	@Transactional(readOnly = true)
	public List<MeetingCommands.ParticipantOption> participantOptions(String keyword) {
		String normalized = keyword == null ? "" : keyword.trim();
		return users.searchActiveMeetingUsers(normalized, PageRequest.of(0, 100))
				.stream()
				.map(user -> new MeetingCommands.ParticipantOption(
						user.getUsername(),
						blank(user.getDisplayName())
								? user.getUsername() : user.getDisplayName()))
				.toList();
	}

	@Transactional
	public int completeExpiredMeetings() {
		return meetings.completeExpired(LocalDateTime.now());
	}

	@Transactional
	public MeetingRoom saveRoom(String id, MeetingCommands.Room command) {
		if (command == null || blank(command.roomCode()) || blank(command.roomName())
				|| command.capacity() == null || command.capacity() <= 0) {
			throw new IllegalArgumentException("请填写会议室编码、名称和有效容量");
		}
		String mode = upper(command.approvalMode());
		if (!Set.of("AUTO", "MANUAL").contains(mode)) {
			throw new IllegalArgumentException("会议室审批模式只允许 AUTO 或 MANUAL");
		}
		if ("MANUAL".equals(mode)) {
			requireActiveUser(command.approverUsername(), "审批人");
		}
		if ((id == null && rooms.existsByRoomCode(command.roomCode().trim()))
				|| (id != null && rooms.existsByRoomCodeAndIdNot(command.roomCode().trim(), id))) {
			throw new IllegalArgumentException("会议室编码已存在");
		}
		MeetingRoom value = id == null ? new MeetingRoom() : requireRoom(id);
		if (id != null && !Objects.equals(value.getRecordVersion(), command.recordVersion())) {
			throw new IllegalStateException("会议室已被其他用户更新，请刷新后重试");
		}
		value.setRoomCode(command.roomCode().trim());
		value.setRoomName(command.roomName().trim());
		value.setCampusId(trim(command.campusId()));
		value.setBuildingName(trim(command.buildingName()));
		value.setLocation(trim(command.location()));
		value.setCapacity(command.capacity());
		value.setEquipmentJson(trim(command.equipmentJson()));
		value.setApprovalMode(mode);
		value.setApproverUsername("MANUAL".equals(mode)
				? command.approverUsername().trim() : null);
		value.setEnabled(command.enabled() == null || command.enabled());
		return rooms.save(value);
	}

	@Transactional
	public void deleteRoom(String id) {
		MeetingRoom room = requireRoom(id);
		if (meetings.existsByRoomId(id)) {
			throw new IllegalStateException("会议室已有预约历史，请停用而不是删除");
		}
		rooms.delete(room);
	}

	@Transactional(readOnly = true)
	public List<MeetingView> allMeetings() {
		return meetings.findAllByOrderByStartTimeDesc().stream()
				.map(this::view)
				.toList();
	}

	@Transactional(readOnly = true)
	public Page<MeetingView> meetingPage(
			String keyword,
			String status,
			int page,
			int size) {
		// 会议数量会持续增长，管理端分页必须在数据库执行，不能先读取全表再截取。
		int safePage = Math.max(page, 0);
		int safeSize = Math.min(Math.max(size, 1), 200);
		return meetings.search(
				normalizeSearch(keyword),
				normalizeSearch(status).toUpperCase(),
				PageRequest.of(safePage, safeSize))
				.map(this::view);
	}

	@Transactional(readOnly = true)
	public List<MeetingView> myMeetings(String username) {
		Set<String> ids = new LinkedHashSet<>(participants.findMeetingIdsByUsername(username));
		meetings.findByOrganizerUsernameOrderByStartTimeDesc(username).stream()
				.map(Meeting::getId)
				.forEach(ids::add);
		return meetings.findAllById(ids).stream()
				.sorted((left, right) -> right.getStartTime().compareTo(left.getStartTime()))
				.map(meeting -> view(meeting, username))
				.toList();
	}

	@Transactional
	public MeetingView saveMeeting(
			String id,
			MeetingCommands.Save command,
			String username) {
		validateMeeting(command);
		Meeting value = id == null ? new Meeting() : requireMeeting(id);
		if (id != null && !value.getOrganizerUsername().equals(username)) {
			throw new AccessDeniedException("只有会议组织者可以修改会议");
		}
		if (id != null && !Objects.equals(value.getRecordVersion(), command.recordVersion())) {
			throw new IllegalStateException("会议已被其他用户更新，请刷新后重试");
		}
		if ("CANCELLED".equals(value.getStatus()) || "COMPLETED".equals(value.getStatus())) {
			throw new IllegalStateException("已取消或已完成的会议不能修改");
		}
		boolean wasPublished = "PUBLISHED".equals(value.getStatus());
		value.setTitle(command.title().trim());
		value.setAgenda(trim(command.agenda()));
		value.setMeetingType(upper(command.meetingType()));
		value.setStartTime(command.startTime());
		value.setEndTime(command.endTime());
		value.setOrganizerUsername(username);
		value.setRoomId(trim(command.roomId()));
		value.setMeetingProvider(trim(command.meetingProvider()));
		value.setExternalMeetingId(trim(command.externalMeetingId()));
		value.setJoinUrl(trim(command.joinUrl()));
		value.setOnlineAccessCode(trim(command.onlineAccessCode()));
		value = meetings.save(value);
		ParticipantChange participantChange = replaceParticipants(
				value.getId(), command.participantUsernames(), username);
		if (wasPublished) {
			// 已发布会议的关键字段变更必须重新占用资源；人工会议室会回到待审批。
			preparePublication(value);
			for (String removed : participantChange.removedUsernames()) {
				notifications.removed(value, removed);
			}
			if ("PUBLISHED".equals(value.getStatus())) {
				notifyParticipantChanges(value, participantChange);
			}
		}
		auditLogs.log("MEETING_SAVE", "MEETING", value.getId());
		return view(value);
	}

	@Transactional
	public MeetingView publish(String id, String username) {
		Meeting meeting = requireOrganizer(id, username);
		if (!Set.of("DRAFT", "REJECTED").contains(meeting.getStatus())) {
			throw new IllegalStateException("当前会议状态不能发布");
		}
		preparePublication(meeting);
		if ("PUBLISHED".equals(meeting.getStatus())) {
			notifyParticipants(meeting, false);
		}
		auditLogs.log("MEETING_PUBLISH", "MEETING", meeting.getId());
		return view(meetings.save(meeting));
	}

	@Transactional
	public void deleteMeeting(String id, String username) {
		Meeting meeting = requireOrganizer(id, username);
		if (!Set.of("DRAFT", "REJECTED").contains(meeting.getStatus())) {
			throw new IllegalStateException("只有草稿或已驳回会议可以删除");
		}
		participants.deleteByMeetingId(id);
		meetings.delete(meeting);
	}

	@Transactional
	public MeetingView decideRoom(
			String id,
			MeetingCommands.Decision command,
			String username,
			boolean hasManagePermission) {
		Meeting meeting = requireMeeting(id);
		if (!"PENDING_ROOM".equals(meeting.getStatus())) {
			throw new IllegalStateException("会议室预约不是待审批状态");
		}
		MeetingRoom room = requireRoom(meeting.getRoomId());
		if (!hasManagePermission && !username.equals(room.getApproverUsername())) {
			throw new AccessDeniedException("仅会议室审批人可以处理预约");
		}
		// 普通审批人不得自审；会议室管理员和超级管理员保留应急接管能力。
		if (username.equals(meeting.getOrganizerUsername())
				&& !hasManagePermission) {
			throw new AccessDeniedException("会议组织者不能审批自己的会议室申请");
		}
		meeting.setRoomDecisionBy(username);
		meeting.setRoomDecisionAt(LocalDateTime.now());
		meeting.setRoomDecisionComment(trim(command.comment()));
		if (command.approve()) {
			lockRoom(room.getId());
			assertRoomAvailable(meeting);
			meeting.setStatus("PUBLISHED");
			meeting.setPublishedAt(LocalDateTime.now());
			meetings.save(meeting);
			notifyParticipants(meeting, false);
			notifications.organizerResult(meeting, "已批准");
		} else {
			meeting.setStatus("REJECTED");
			meetings.save(meeting);
			notifications.organizerResult(meeting, "已驳回");
		}
		auditLogs.log("MEETING_ROOM_DECIDE", "MEETING", meeting.getId());
		return view(meeting);
	}

	@Transactional
	public MeetingView respond(
			String id,
			MeetingCommands.Response command,
			String username) {
		Meeting meeting = requireMeeting(id);
		if (!"PUBLISHED".equals(meeting.getStatus())) {
			throw new IllegalStateException("会议尚未发布或已经取消");
		}
		MeetingParticipant participant = participants
				.findByMeetingIdAndUsername(id, username)
				.orElseThrow(() -> new AccessDeniedException("您不是该会议的受邀人员"));
		String response = upper(command.status());
		if (!Set.of("ACCEPTED", "DECLINED", "LEAVE").contains(response)) {
			throw new IllegalArgumentException("参会反馈状态无效");
		}
		participant.setResponseStatus(response);
		participant.setResponseComment(trim(command.comment()));
		participant.setRespondedAt(LocalDateTime.now());
		participants.save(participant);
		auditLogs.log("MEETING_RESPONSE", "MEETING", id);
		return view(meeting);
	}

	@Transactional
	public MeetingView checkIn(String id, String username) {
		Meeting meeting = requireMeeting(id);
		if (!"PUBLISHED".equals(meeting.getStatus())) {
			throw new IllegalStateException("会议当前不能签到");
		}
		LocalDateTime now = LocalDateTime.now();
		if (now.isBefore(meeting.getStartTime().minusMinutes(30))
				|| now.isAfter(meeting.getEndTime().plusHours(2))) {
			throw new IllegalStateException("签到仅在会议开始前30分钟至结束后2小时开放");
		}
		MeetingParticipant participant = participants
				.findByMeetingIdAndUsername(id, username)
				.orElseThrow(() -> new AccessDeniedException("您不是该会议的受邀人员"));
		if (participant.getCheckedInAt() == null) {
			participant.setCheckedInAt(now);
			participant.setCheckInMethod("PORTAL");
			participants.save(participant);
			auditLogs.log("MEETING_CHECK_IN", "MEETING", id);
		}
		return view(meeting, username);
	}

	@Transactional
	public MeetingMaterial addMaterial(
			String meetingId,
			MeetingCommands.Material command,
			String username) {
		Meeting meeting = requireOrganizer(meetingId, username);
		if (!Set.of("PUBLISHED", "COMPLETED").contains(meeting.getStatus())) {
			throw new IllegalStateException("会议发布后才能添加材料");
		}
		MeetingMaterial material = new MeetingMaterial();
		material.setMeetingId(meetingId);
		material.setTitle(command.title().trim());
		material.setFileId(command.fileId().trim());
		material = materials.save(material);
		// 文件先以草稿上传，保存材料时再原子绑定到材料记录，避免裸MinIO地址进入业务表。
		managedFiles.bind(
				List.of(material.getFileId()),
				"EDUCATION_MEETING",
				material.getId(),
				username);
		auditLogs.log("MEETING_MATERIAL_ADD", "MEETING", meetingId);
		return material;
	}

	@Transactional
	public void deleteMaterial(
			String meetingId,
			String materialId,
			Authentication authentication) {
		requireOrganizer(meetingId, authentication.getName());
		MeetingMaterial material = materials.findById(materialId)
				.filter(value -> meetingId.equals(value.getMeetingId()))
				.orElseThrow(() -> new IllegalArgumentException("会议材料不存在"));
		// 必须在删除领域引用前删除文件，否则文件策略将无法再判断材料归属。
		managedFiles.delete(material.getFileId(), authentication);
		materials.delete(material);
		auditLogs.log("MEETING_MATERIAL_DELETE", "MEETING", meetingId);
	}

	@Transactional
	public MeetingMinutes saveMinutes(
			String meetingId,
			MeetingCommands.Minutes command,
			String username) {
		Meeting meeting = requireOrganizer(meetingId, username);
		if (!Set.of("PUBLISHED", "COMPLETED").contains(meeting.getStatus())) {
			throw new IllegalStateException("会议发布后才能维护纪要");
		}
		MeetingMinutes value = minutes.findByMeetingId(meetingId)
				.orElseGet(MeetingMinutes::new);
		if (value.getId() != null && "PUBLISHED".equals(value.getStatus())) {
			throw new IllegalStateException("已发布纪要不能直接修改");
		}
		if (value.getId() != null
				&& !Objects.equals(value.getRecordVersion(), command.recordVersion())) {
			throw new IllegalStateException("会议纪要已被其他用户更新，请刷新后重试");
		}
		value.setMeetingId(meetingId);
		value.setContent(command.content().trim());
		value.setDecisionsText(trim(command.decisionsText()));
		value.setStatus("DRAFT");
		value = minutes.save(value);
		auditLogs.log("MEETING_MINUTES_SAVE", "MEETING", meetingId);
		return value;
	}

	@Transactional
	public MeetingMinutes publishMinutes(String meetingId, String username) {
		requireOrganizer(meetingId, username);
		MeetingMinutes value = minutes.findByMeetingId(meetingId)
				.orElseThrow(() -> new IllegalArgumentException("请先保存会议纪要"));
		if (!"PUBLISHED".equals(value.getStatus())) {
			value.setStatus("PUBLISHED");
			value.setPublishedAt(LocalDateTime.now());
			minutes.save(value);
			for (MeetingParticipant participant : participants
					.findByMeetingIdOrderByCreateTimeAsc(meetingId)) {
				notifications.minutesPublished(
						requireMeeting(meetingId),
						value,
						participant.getUsername());
			}
			auditLogs.log("MEETING_MINUTES_PUBLISH", "MEETING", meetingId);
		}
		return value;
	}

	@Transactional
	public MeetingActionItem saveActionItem(
			String meetingId,
			String itemId,
			MeetingCommands.ActionItem command,
			String username) {
		Meeting meeting = requireOrganizer(meetingId, username);
		if (!Set.of("PUBLISHED", "COMPLETED").contains(meeting.getStatus())) {
			throw new IllegalStateException("会议发布后才能维护行动项");
		}
		requireActiveUser(command.assigneeUsername(), "责任人");
		if (!isMeetingMember(meeting, command.assigneeUsername())) {
			throw new IllegalArgumentException("行动项责任人必须是会议组织者或参会人");
		}
		MeetingActionItem value = itemId == null
				? new MeetingActionItem()
				: requireActionItem(meetingId, itemId);
		if (itemId != null
				&& !Objects.equals(value.getRecordVersion(), command.recordVersion())) {
			throw new IllegalStateException("行动项已被其他用户更新，请刷新后重试");
		}
		if ("DONE".equals(value.getStatus()) || "CANCELLED".equals(value.getStatus())) {
			throw new IllegalStateException("已完成或已取消行动项不能修改");
		}
		value.setMeetingId(meetingId);
		value.setTitle(command.title().trim());
		value.setDescription(trim(command.description()));
		value.setAssigneeUsername(command.assigneeUsername().trim());
		value.setDueAt(command.dueAt());
		boolean created = itemId == null;
		value = actionItems.save(value);
		if (created) {
			notifications.actionAssigned(meeting, value);
		}
		auditLogs.log("MEETING_ACTION_SAVE", "MEETING", meetingId);
		return value;
	}

	@Transactional
	public MeetingActionItem updateActionStatus(
			String meetingId,
			String itemId,
			MeetingCommands.ActionStatus command,
			String username) {
		Meeting meeting = requireMeeting(meetingId);
		MeetingActionItem value = requireActionItem(meetingId, itemId);
		if (!username.equals(meeting.getOrganizerUsername())
				&& !username.equals(value.getAssigneeUsername())) {
			throw new AccessDeniedException("只有会议组织者或行动项责任人可以更新状态");
		}
		if (!Objects.equals(value.getRecordVersion(), command.recordVersion())) {
			throw new IllegalStateException("行动项已被其他用户更新，请刷新后重试");
		}
		String status = upper(command.status());
		if (!Set.of("OPEN", "IN_PROGRESS", "DONE", "CANCELLED").contains(status)) {
			throw new IllegalArgumentException("行动项状态无效");
		}
		value.setStatus(status);
		value.setCompletedAt("DONE".equals(status) ? LocalDateTime.now() : null);
		value = actionItems.save(value);
		auditLogs.log("MEETING_ACTION_STATUS", "MEETING", meetingId);
		return value;
	}

	@Transactional
	public MeetingView cancel(
			String id,
			MeetingCommands.Cancellation command,
			String username) {
		Meeting meeting = requireOrganizer(id, username);
		if (blank(command == null ? null : command.reason())) {
			throw new IllegalArgumentException("请填写取消原因");
		}
		if ("CANCELLED".equals(meeting.getStatus())) {
			return view(meeting);
		}
		if (meeting.getStartTime().isBefore(LocalDateTime.now())) {
			throw new IllegalStateException("已经开始的会议不能直接取消");
		}
		meeting.setStatus("CANCELLED");
		meeting.setCancelledAt(LocalDateTime.now());
		meeting.setCancelReason(command.reason().trim());
		meetings.save(meeting);
		for (MeetingParticipant participant : participants
				.findByMeetingIdOrderByCreateTimeAsc(id)) {
			notifications.cancelled(meeting, participant.getUsername());
		}
		auditLogs.log("MEETING_CANCEL", "MEETING", id);
		return view(meeting);
	}

	private void preparePublication(Meeting meeting) {
		if (meeting.getRoomId() == null) {
			meeting.setStatus("PUBLISHED");
			meeting.setPublishedAt(LocalDateTime.now());
			meetings.save(meeting);
			return;
		}
		MeetingRoom room = requireRoom(meeting.getRoomId());
		if (!Boolean.TRUE.equals(room.getEnabled())) {
			throw new IllegalStateException("所选会议室已经停用");
		}
		long attendeeCount = participants.countByMeetingId(meeting.getId()) + 1;
		if (attendeeCount > room.getCapacity()) {
			throw new IllegalStateException(
					"参会人数超过会议室容量（含组织者共"
							+ attendeeCount + "人，容量" + room.getCapacity() + "人）");
		}
		lockRoom(room.getId());
		assertRoomAvailable(meeting);
		if ("MANUAL".equals(room.getApprovalMode())) {
			meeting.setStatus("PENDING_ROOM");
			meetings.save(meeting);
			notifications.roomApproval(meeting, room.getApproverUsername());
		} else {
			meeting.setStatus("PUBLISHED");
			meeting.setPublishedAt(LocalDateTime.now());
			meetings.save(meeting);
		}
	}

	private void validateMeeting(MeetingCommands.Save command) {
		if (command == null || blank(command.title()) || command.startTime() == null
				|| command.endTime() == null
				|| !command.endTime().isAfter(command.startTime())) {
			throw new IllegalArgumentException("请填写会议主题和有效的起止时间");
		}
		if (command.startTime().isBefore(LocalDateTime.now())) {
			throw new IllegalArgumentException("会议开始时间不能早于当前时间");
		}
		String type = upper(command.meetingType());
		if (!Set.of("ONSITE", "ONLINE", "HYBRID").contains(type)) {
			throw new IllegalArgumentException("会议形式无效");
		}
		if (("ONSITE".equals(type) || "HYBRID".equals(type))
				&& blank(command.roomId())) {
			throw new IllegalArgumentException("线下或混合会议必须选择会议室");
		}
		if (("ONLINE".equals(type) || "HYBRID".equals(type))
				&& blank(command.joinUrl())) {
			throw new IllegalArgumentException("线上或混合会议必须填写加入链接");
		}
		if (!blank(command.joinUrl())) {
			try {
				URI uri = URI.create(command.joinUrl().trim());
				if (!Set.of("http", "https").contains(uri.getScheme())) {
					throw new IllegalArgumentException("线上会议链接只允许 HTTP/HTTPS");
				}
			} catch (IllegalArgumentException exception) {
				throw new IllegalArgumentException("线上会议链接格式无效");
			}
		}
	}

	private String normalizeSearch(String value) {
		return value == null ? "" : value.trim();
	}

	private ParticipantChange replaceParticipants(
			String meetingId,
			List<String> usernames,
			String organizer) {
		List<MeetingParticipant> oldValues = participants
				.findByMeetingIdOrderByCreateTimeAsc(meetingId);
		Set<String> requested = new LinkedHashSet<>();
		if (usernames != null) {
			requested.addAll(usernames.stream()
					.filter(value -> !blank(value))
					.map(String::trim)
					.filter(value -> !organizer.equals(value))
					.toList());
		}
		for (String username : requested) {
			requireActiveUser(username, "参会人");
		}
		List<String> removedUsernames = oldValues.stream()
				.filter(value -> !requested.contains(value.getUsername()))
				.map(MeetingParticipant::getUsername)
				.toList();
		participants.deleteAll(oldValues.stream()
				.filter(value -> removedUsernames.contains(value.getUsername()))
				.toList());
		Set<String> existing = new LinkedHashSet<>();
		for (MeetingParticipant value : oldValues) {
			if (requested.contains(value.getUsername())) {
				existing.add(value.getUsername());
			}
		}
		List<MeetingParticipant> additions = new ArrayList<>();
		for (String username : requested) {
			if (existing.add(username)) {
				MeetingParticipant value = new MeetingParticipant();
				value.setMeetingId(meetingId);
				value.setUsername(username);
				additions.add(value);
			}
		}
		participants.saveAll(additions);
		return new ParticipantChange(
				additions.stream().map(MeetingParticipant::getUsername).toList(),
				removedUsernames);
	}

	private void notifyParticipantChanges(
			Meeting meeting,
			ParticipantChange change) {
		Set<String> additions = new LinkedHashSet<>(change.addedUsernames());
		for (MeetingParticipant participant : participants
				.findByMeetingIdOrderByCreateTimeAsc(meeting.getId())) {
			if (additions.contains(participant.getUsername())) {
				notifications.invite(
						meeting,
						participant.getUsername(),
						"PUBLISHED:" + meeting.getRecordVersion());
			} else {
				notifications.changed(meeting, participant.getUsername());
			}
		}
	}

	private void notifyParticipants(Meeting meeting, boolean changed) {
		for (MeetingParticipant participant : participants
				.findByMeetingIdOrderByCreateTimeAsc(meeting.getId())) {
			if (changed) {
				notifications.changed(meeting, participant.getUsername());
			} else {
				notifications.invite(
						meeting,
						participant.getUsername(),
						"PUBLISHED:" + meeting.getRecordVersion());
			}
		}
	}

	private void assertRoomAvailable(Meeting meeting) {
		if (!meetings.findRoomConflicts(
				meeting.getRoomId(), meeting.getId(), meeting.getStartTime(),
				meeting.getEndTime(), ACTIVE_ROOM_STATUSES).isEmpty()) {
			throw new IllegalStateException("所选会议室在该时段已被占用");
		}
	}

	private void lockRoom(String roomId) {
		// PostgreSQL 事务级咨询锁串行化同一会议室的最终检查，避免并发预约穿透。
		entityManager.createNativeQuery("select pg_advisory_xact_lock(hashtext(?1))")
				.setParameter(1, "MEETING_ROOM:" + roomId)
				.getSingleResult();
	}

	private MeetingView view(Meeting meeting) {
		return view(meeting, null);
	}

	private MeetingView view(Meeting meeting, String currentUsername) {
		MeetingRoom room = meeting.getRoomId() == null ? null
				: rooms.findById(meeting.getRoomId()).orElse(null);
		List<MeetingParticipant> meetingParticipants = participants
				.findByMeetingIdOrderByCreateTimeAsc(meeting.getId());
		MeetingParticipant currentParticipant = currentUsername == null ? null
				: meetingParticipants.stream()
						.filter(value -> currentUsername.equals(value.getUsername()))
						.findFirst()
						.orElse(null);
		MeetingMinutes meetingMinutes = minutes.findByMeetingId(meeting.getId())
				.orElse(null);
		if (meetingMinutes != null
				&& currentUsername != null
				&& !currentUsername.equals(meeting.getOrganizerUsername())
				&& !"PUBLISHED".equals(meetingMinutes.getStatus())) {
			meetingMinutes = null;
		}
		return new MeetingView(
				meeting,
				room,
				meetingParticipants,
				currentParticipant,
				currentUsername,
				materials.findByMeetingIdOrderByCreateTimeAsc(meeting.getId()),
				meetingMinutes,
				actionItems.findByMeetingIdOrderByCreateTimeAsc(meeting.getId()));
	}

	private boolean isMeetingMember(Meeting meeting, String username) {
		return username.equals(meeting.getOrganizerUsername())
				|| participants.findByMeetingIdAndUsername(meeting.getId(), username).isPresent();
	}

	private MeetingActionItem requireActionItem(String meetingId, String itemId) {
		return actionItems.findById(itemId)
				.filter(value -> meetingId.equals(value.getMeetingId()))
				.orElseThrow(() -> new IllegalArgumentException("会议行动项不存在"));
	}

	private Meeting requireOrganizer(String id, String username) {
		Meeting meeting = requireMeeting(id);
		if (!meeting.getOrganizerUsername().equals(username)) {
			throw new AccessDeniedException("只有会议组织者可以执行该操作");
		}
		return meeting;
	}

	private Meeting requireMeeting(String id) {
		return meetings.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("会议不存在"));
	}

	private MeetingRoom requireRoom(String id) {
		return rooms.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("会议室不存在"));
	}

	private AdminUser requireActiveUser(String username, String label) {
		if (blank(username)) {
			throw new IllegalArgumentException("请选择" + label);
		}
		AdminUser user = users.findByUsername(username.trim());
		if (user == null || !Integer.valueOf(1).equals(user.getStatus())
				|| Boolean.TRUE.equals(user.getAccountLocked())) {
			throw new IllegalArgumentException(label + "不存在或不可用");
		}
		return user;
	}

	private String upper(String value) {
		return blank(value) ? "" : value.trim().toUpperCase();
	}

	private String trim(String value) {
		return blank(value) ? null : value.trim();
	}

	private boolean blank(String value) {
		return value == null || value.isBlank();
	}

	private record ParticipantChange(
			List<String> addedUsernames,
			List<String> removedUsernames) {
	}
}
