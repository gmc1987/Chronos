package com.chronos.education.scheduling.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.service.iService.IAuditLogService;

/**
 * 宿舍、房间、床位和住宿分配的人工业务入口。
 * Agent 只消费这里提供的只读候选数据和受控分配方法，不能直接写住宿表。
 */
@Service
public class AccommodationService {
	private final JdbcTemplate jdbc;
	private final IAuditLogService auditLogs;

	public AccommodationService(JdbcTemplate jdbc, IAuditLogService auditLogs) {
		this.jdbc = jdbc;
		this.auditLogs = auditLogs;
	}

	public List<Map<String, Object>> buildings() {
		return jdbc.queryForList("""
				select building.id, building.building_code as "buildingCode", building.building_name as "buildingName",
				       building.gender_policy as "genderPolicy", building.status,
				       count(distinct room.id) as "roomCount",
				       count(bed.id) as "bedCount",
				       count(bed.id) filter (where bed.status = 'AVAILABLE') as "availableBedCount"
				from edu_dorm_building building
				left join edu_dorm_room room on room.building_id = building.id
				left join edu_dorm_bed bed on bed.room_id = room.id
				group by building.id
				order by building.building_code
				""");
	}

	public List<Map<String, Object>> rooms(String buildingId) {
		return jdbc.queryForList("""
				select room.id, room.building_id as "buildingId", room.room_no as "roomNo", room.floor_no as "floorNo", room.capacity, room.status,
				       count(bed.id) as "bedCount",
				       count(bed.id) filter (where bed.status = 'AVAILABLE') as "availableBedCount"
				from edu_dorm_room room
				left join edu_dorm_bed bed on bed.room_id = room.id
				where room.building_id = ?
				group by room.id
				order by room.floor_no, room.room_no
				""", buildingId);
	}

	public List<Map<String, Object>> beds(String semesterCode, boolean availableOnly) {
		String availability = availableOnly ? " and bed.status = 'AVAILABLE' and assignment.id is null" : "";
		return jdbc.queryForList("""
				select bed.id, bed.bed_no as "bedNo", bed.status, room.id as "roomId", room.room_no as "roomNo",
				       building.id as "buildingId", building.building_name as "buildingName",
				       assignment.student_id as "studentId", student.student_name as "studentName", assignment.semester_code as "semesterCode"
				from edu_dorm_bed bed
				join edu_dorm_room room on room.id = bed.room_id
				join edu_dorm_building building on building.id = room.building_id
				left join edu_student_accommodation assignment
				  on assignment.bed_id = bed.id and assignment.semester_code = ? and assignment.status = 'ACTIVE'
				left join edu_student_profile student on student.id = assignment.student_id
				where 1 = 1
				""" + availability + " order by building.building_code, room.floor_no, room.room_no, bed.bed_no", semesterCode);
	}

	public List<Map<String, Object>> assignments(String semesterCode) {
		return jdbc.queryForList("""
				select assignment.id, assignment.semester_code as "semesterCode", assignment.student_id as "studentId", student.student_no as "studentNo",
				       student.student_name as "studentName", assignment.bed_id as "bedId", bed.bed_no as "bedNo", room.room_no as "roomNo",
				       building.building_name as "buildingName", assignment.status, assignment.assigned_at as "assignedAt", assignment.assigned_by as "assignedBy"
				from edu_student_accommodation assignment
				join edu_student_profile student on student.id = assignment.student_id
				join edu_dorm_bed bed on bed.id = assignment.bed_id
				join edu_dorm_room room on room.id = bed.room_id
				join edu_dorm_building building on building.id = room.building_id
				where assignment.semester_code = ? and assignment.status = 'ACTIVE'
				order by student.student_no
				""", semesterCode);
	}

	public List<Map<String, Object>> studentOptions(String keyword) {
		String value = keyword == null ? "" : keyword.trim();
		return jdbc.queryForList("""
				select id, student_no as "studentNo", student_name as "studentName", grade_year as "gradeYear", enrollment_status as "enrollmentStatus"
				from edu_student_profile
				where enrollment_status = 'ACTIVE'
				  and (? = '' or student_no ilike ? or student_name ilike ?)
				order by student_no limit 50
				""", value, "%" + value + "%", "%" + value + "%");
	}

	@Transactional
	public Map<String, Object> createBuilding(BuildingCommand command) {
		String id = id();
		jdbc.update("insert into edu_dorm_building(id, building_code, building_name, gender_policy, status) values (?, ?, ?, ?, ?)",
				id, required(command.buildingCode(), "楼栋编码"), required(command.buildingName(), "楼栋名称"),
				blankToDefault(command.genderPolicy(), "MIXED"), blankToDefault(command.status(), "ACTIVE"));
		auditLogs.log("ACCOMMODATION_BUILDING_CREATE", "DORM_BUILDING", id);
		return jdbc.queryForMap("select * from edu_dorm_building where id = ?", id);
	}

	@Transactional
	public Map<String, Object> createRoom(String buildingId, RoomCommand command) {
		ensureExists("select count(*) from edu_dorm_building where id = ?", buildingId, "宿舍楼不存在");
		String id = id();
		jdbc.update("insert into edu_dorm_room(id, building_id, room_no, floor_no, capacity, status) values (?, ?, ?, ?, ?, ?)",
				id, buildingId, required(command.roomNo(), "房间号"), positive(command.floorNo(), "楼层"), positive(command.capacity(), "床位数"),
				blankToDefault(command.status(), "ACTIVE"));
		for (int index = 1; index <= command.capacity(); index++) {
			jdbc.update("insert into edu_dorm_bed(id, room_id, bed_no, status) values (?, ?, ?, 'AVAILABLE')",
					id(), id, String.format("%02d", index));
		}
		auditLogs.log("ACCOMMODATION_ROOM_CREATE", "DORM_ROOM", id);
		return jdbc.queryForMap("select * from edu_dorm_room where id = ?", id);
	}

	@Transactional
	public Map<String, Object> updateRoom(String roomId, RoomCommand command) {
		Map<String, Object> room;
		try {
			room = jdbc.queryForMap("select capacity, (select count(*) from edu_dorm_bed where room_id = ?) as bed_count from edu_dorm_room where id = ?", roomId, roomId);
		} catch (org.springframework.dao.EmptyResultDataAccessException exception) {
			throw new IllegalArgumentException("宿舍房间不存在");
		}
		int capacity = positive(command.capacity(), "床位数");
		int existingBeds = ((Number) room.get("bed_count")).intValue();
		if (capacity < existingBeds) throw new IllegalArgumentException("床位数不能小于已有床位数（" + existingBeds + "）");
		jdbc.update("update edu_dorm_room set room_no=?, floor_no=?, capacity=?, status=? where id=?",
				required(command.roomNo(), "房间号"), positive(command.floorNo(), "楼层"), capacity,
				blankToDefault(command.status(), "ACTIVE"), roomId);
		for (int index = existingBeds + 1; index <= capacity; index++) {
			jdbc.update("insert into edu_dorm_bed(id, room_id, bed_no, status) values (?, ?, ?, 'AVAILABLE')",
					id(), roomId, String.format("%02d", index));
		}
		auditLogs.log("ACCOMMODATION_ROOM_UPDATE", "DORM_ROOM", roomId);
		return jdbc.queryForMap("select * from edu_dorm_room where id = ?", roomId);
	}

	@Transactional
	public void deleteRoom(String roomId, String actor) {
		ensureExists("select count(*) from edu_dorm_room where id = ?", roomId, "宿舍房间不存在");
		if (jdbc.queryForObject("select count(*) from edu_student_accommodation assignment join edu_dorm_bed bed on bed.id=assignment.bed_id where bed.room_id=?", Long.class, roomId) > 0)
			throw new IllegalStateException("该房间已有住宿分配，不能删除");
		jdbc.update("delete from edu_dorm_bed where room_id=?", roomId);
		jdbc.update("delete from edu_dorm_room where id=?", roomId);
		auditLogs.log("ACCOMMODATION_ROOM_DELETE", "DORM_ROOM", roomId);
	}

	@Transactional
	public Map<String, Object> createBed(String roomId, BedCommand command) {
		Map<String, Object> room;
		try {
			room = jdbc.queryForMap("select capacity, (select count(*) from edu_dorm_bed where room_id = ?) as bed_count from edu_dorm_room where id = ?", roomId, roomId);
		} catch (org.springframework.dao.EmptyResultDataAccessException exception) {
			throw new IllegalArgumentException("宿舍房间不存在");
		}
		if (((Number) room.get("bed_count")).intValue() >= ((Number) room.get("capacity")).intValue()) throw new IllegalArgumentException("该房间床位数已达到容量，请先调整房间容量");
		String id = id();
		jdbc.update("insert into edu_dorm_bed(id, room_id, bed_no, status) values (?, ?, ?, ?)",
				id, roomId, required(command.bedNo(), "床位号"), blankToDefault(command.status(), "AVAILABLE"));
		auditLogs.log("ACCOMMODATION_BED_CREATE", "DORM_BED", id);
		return jdbc.queryForMap("select * from edu_dorm_bed where id = ?", id);
	}

	@Transactional
	public Map<String, Object> assign(AssignmentCommand command, String actor) {
		String semester = required(command.semesterCode(), "学期编码");
		String studentId = required(command.studentId(), "学生");
		String bedId = required(command.bedId(), "床位");
		ensureExists("select count(*) from edu_student_profile where id = ? and enrollment_status = 'ACTIVE'", studentId, "学生不存在或未启用");
		ensureExists("select count(*) from edu_dorm_bed where id = ? and status = 'AVAILABLE'", bedId, "床位不存在或不可用");
		if (jdbc.queryForObject("select count(*) from edu_student_accommodation where semester_code = ? and student_id = ? and status = 'ACTIVE'", Long.class, semester, studentId) > 0)
			throw new IllegalStateException("该学生本学期已有住宿分配");
		if (jdbc.queryForObject("select count(*) from edu_student_accommodation where semester_code = ? and bed_id = ? and status = 'ACTIVE'", Long.class, semester, bedId) > 0)
			throw new IllegalStateException("该床位本学期已被占用");
		String id = id();
		jdbc.update("insert into edu_student_accommodation(id, semester_code, student_id, bed_id, status, assigned_at, assigned_by) values (?, ?, ?, ?, 'ACTIVE', ?, ?)",
				id, semester, studentId, bedId, LocalDateTime.now(), actor);
		auditLogs.log("ACCOMMODATION_ASSIGN", "STUDENT_ACCOMMODATION", id);
		return jdbc.queryForMap("select * from edu_student_accommodation where id = ?", id);
	}

	@Transactional
	public void cancelAssignment(String id, String actor) {
		int updated = jdbc.update("update edu_student_accommodation set status='CANCELLED' where id=? and status='ACTIVE'", id);
		if (updated == 0) {
			Long existing = jdbc.queryForObject(
					"select count(*) from edu_student_accommodation where id=?",
					Long.class,
					id);
			if (existing == null || existing == 0) throw new IllegalArgumentException("住宿分配不存在");
			// DELETE 语义保持幂等：重复点击或网络重试不应把已完成的撤销报告为失败。
			return;
		}
		auditLogs.log("ACCOMMODATION_ASSIGN_CANCEL", "STUDENT_ACCOMMODATION", id);
	}

	private void ensureExists(String sql, String value, String message) {
		if (jdbc.queryForObject(sql, Long.class, value) == 0) throw new IllegalArgumentException(message);
	}

	private String id() { return UUID.randomUUID().toString(); }
	private String required(String value, String label) { if (value == null || value.isBlank()) throw new IllegalArgumentException(label + "不能为空"); return value.trim(); }
	private int positive(Integer value, String label) { if (value == null || value < 1) throw new IllegalArgumentException(label + "必须大于0"); return value; }
	private String blankToDefault(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }

	public record BuildingCommand(String buildingCode, String buildingName, String genderPolicy, String status) {}
	public record RoomCommand(String roomNo, Integer floorNo, Integer capacity, String status) {}
	public record BedCommand(String bedNo, String status) {}
	public record AssignmentCommand(String semesterCode, String studentId, String bedId) {}
}
