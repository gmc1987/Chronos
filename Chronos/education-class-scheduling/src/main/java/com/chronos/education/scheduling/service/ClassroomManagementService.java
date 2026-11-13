package com.chronos.education.scheduling.service;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.education.scheduling.dao.ClassroomRepository;
import com.chronos.education.scheduling.model.Classroom;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClassroomManagementService {
	private final JdbcTemplate jdbc;
	private final ClassroomRepository rooms;
	private final ClassSchedulingService scheduling;
	private final EducationResourceTransactionLock locks;

	public record Building(String id, String buildingCode, String buildingName) {}
	public record Floor(String id, String buildingId, Integer floorNo) {}
	public record Catalog(List<Building> buildings, List<Floor> floors, List<Classroom> rooms, long total) {}

	@Transactional(readOnly = true)
	public Catalog catalog(String campusId) {
		return catalog(campusId, "", "", false, null, 10);
	}

	@Transactional(readOnly = true)
	public Catalog catalog(String campusId, String buildingId, String floorId, boolean unclassified,
			Integer page, int size) {
		var buildings = jdbc.query("""
				select id, building_code, building_name from edu_teaching_building
				where campus_id = ? order by building_code
				""", (row, index) -> new Building(row.getString(1), row.getString(2), row.getString(3)), campusId);
		var floors = jdbc.query("""
				select f.id, f.building_id, f.floor_no from edu_teaching_floor f
				join edu_teaching_building b on b.id = f.building_id
				where b.campus_id = ? order by f.floor_no
				""", (row, index) -> new Floor(row.getString(1), row.getString(2), row.getInt(3)), campusId);
		if (page == null) {
			var all = rooms.findByCampusIdOrderByRoomCode(campusId);
			return new Catalog(buildings, floors, all, all.size());
		}
		var result = rooms.findCatalogPage(campusId, buildingId, floorId, unclassified,
				org.springframework.data.domain.PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size))));
		return new Catalog(buildings, floors, result.getContent(), result.getTotalElements());
	}

	@Transactional
	public Building saveBuilding(String campusId, Building command) {
		lockCatalog();
		String code = required(command.buildingCode(), "楼栋编码", 64);
		String name = required(command.buildingName(), "楼栋名称", 128);
		String id = command.id() == null ? UUID.randomUUID().toString() : command.id();
		if (command.id() == null) {
			jdbc.update("insert into edu_teaching_building values (?, ?, ?, ?)", id, campusId, code, name);
		} else {
			requireChanged(jdbc.update("""
					update edu_teaching_building set building_code = ?, building_name = ?
					where id = ? and campus_id = ?
					""", code, name, id, campusId));
			jdbc.update("""
					update edu_classroom set building_name = ? where floor_id in
					(select id from edu_teaching_floor where building_id = ?)
					""", name, id);
		}
		return new Building(id, code, name);
	}

	public static final int MAX_FLOOR_NUMBER = 200;

	@Transactional
	public int createFloorsThrough(String campusId, String buildingId, Integer maxFloorNo) {
		if (maxFloorNo == null || maxFloorNo < 1 || maxFloorNo > MAX_FLOOR_NUMBER) {
			throw new IllegalArgumentException("最大楼层必须为 1 到 " + MAX_FLOOR_NUMBER + " 的整数");
		}
		lockCatalog();
		if (!Boolean.TRUE.equals(jdbc.queryForObject("""
				select exists(select 1 from edu_teaching_building where id = ? and campus_id = ?)
				""", Boolean.class, buildingId, campusId))) {
			throw new IllegalArgumentException("所选校区没有该教学楼");
		}
		return jdbc.update("""
				insert into edu_teaching_floor (id, building_id, floor_no)
				select gen_random_uuid()::text, ?, floor_no
				from generate_series(1, ?) as floors(floor_no)
				on conflict (building_id, floor_no) do nothing
				""", buildingId, maxFloorNo);
	}

	@Transactional
	public Floor saveFloor(String campusId, Floor command) {
		lockCatalog();
		if (command.floorNo() == null) throw new IllegalArgumentException("请填写楼层");
		String id = command.id() == null ? UUID.randomUUID().toString() : command.id();
		if (command.id() == null) {
			requireChanged(jdbc.update("""
					insert into edu_teaching_floor (id, building_id, floor_no)
					select ?, id, ? from edu_teaching_building where id = ? and campus_id = ?
					""", id, command.floorNo(), command.buildingId(), campusId));
		} else {
			requireChanged(jdbc.update("""
					update edu_teaching_floor set floor_no = ?
					where id = ? and building_id in (select id from edu_teaching_building where id = ? and campus_id = ?)
					""", command.floorNo(), id, command.buildingId(), campusId));
		}
		return new Floor(id, command.buildingId(), command.floorNo());
	}

	@Transactional
	public void deleteLocation(String campusId, boolean building, String id) {
		lockCatalog();
		if (building) {
			if (Boolean.TRUE.equals(jdbc.queryForObject(
					"select exists(select 1 from edu_teaching_floor where building_id = ?)", Boolean.class, id))) {
				throw new IllegalStateException("教学楼下还有楼层，请先移除楼层");
			}
			requireChanged(jdbc.update("delete from edu_teaching_building where id = ? and campus_id = ?", id, campusId));
		} else {
			if (Boolean.TRUE.equals(jdbc.queryForObject(
					"select exists(select 1 from edu_classroom where floor_id = ?)", Boolean.class, id))) {
				throw new IllegalStateException("楼层下还有教室，请先移动或删除教室");
			}
			requireChanged(jdbc.update("""
					delete from edu_teaching_floor where id = ? and building_id in
					(select id from edu_teaching_building where campus_id = ?)
					""", id, campusId));
		}
	}

	@Transactional
	public Classroom saveRoom(String campusId, Classroom command) {
		lockCatalog();
		String floor = required(command.getFloorId(), "楼层", 64);
		String number = required(command.getRoomNumber(), "房间号", 64);
		String code = required(command.getRoomCode(), "教室编码", 64);
		String name = required(command.getRoomName(), "教室名称", 128);
		var building = jdbc.queryForList("""
				select b.building_name from edu_teaching_floor f
				join edu_teaching_building b on b.id = f.building_id
				where f.id = ? and b.campus_id = ?
				""", String.class, floor, campusId);
		if (building.isEmpty()) throw new IllegalArgumentException("楼层不属于所选校区");
		String id = command.getId();
		if (rooms.existsByRoomCodeAndIdNot(code, id == null ? "" : id)) {
			throw new IllegalArgumentException("教室编码已存在：" + code);
		}
		if (rooms.existsByFloorIdAndRoomNumberAndIdNot(floor, number, id == null ? "" : id)) {
			throw new IllegalArgumentException("该楼层已有同号教室：" + number);
		}
		command.setCampusId(campusId);
		command.setRoomCode(code);
		command.setRoomName(name);
		command.setBuildingName(building.getFirst());
		var value = scheduling.saveClassroom(id, command);
		value.setFloorId(floor);
		value.setRoomNumber(number);
		value.setBuildingName(building.getFirst());
		return value;
	}

	public void lockCatalog() {
		locks.lockSemester("classroom-management");
	}

	static String required(String value, String label, int maxLength) {
		if (value == null || value.isBlank()) throw new IllegalArgumentException(label + "不能为空");
		String trimmed = value.strip();
		if (trimmed.length() > maxLength) throw new IllegalArgumentException(label + "过长");
		return trimmed;
	}

	private static void requireChanged(int count) {
		if (count != 1) throw new IllegalArgumentException("所选校区没有该位置");
	}
}
