package com.chronos.education.scheduling.service;

import static com.chronos.education.scheduling.service.ClassroomManagementService.required;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.chronos.education.scheduling.dao.ClassroomRepository;
import com.chronos.education.scheduling.model.Classroom;
import com.chronos.education.scheduling.service.ClassroomManagementService.Building;
import com.chronos.education.scheduling.service.ClassroomManagementService.Floor;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClassroomImportService {
	private static final List<String> HEADERS = List.of("教学楼编码*", "教学楼名称*", "楼层*", "房间号*",
			"教室编码*", "教室名称*", "容量*", "教室类型", "设备编码");
	private final ClassroomManagementService management;
	private final ClassroomRepository rooms;

	private record Position(String buildingCode, int floorNo) {}
	private record ImportRow(Position position, String buildingName, Classroom room) {}
	public record Result(int rowCount, int validCount, List<String> errors) {}

	public byte[] template() throws IOException {
		try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
			var sheet = workbook.createSheet("教室");
			var header = sheet.createRow(0);
			for (int index = 0; index < HEADERS.size(); index++) {
				header.createCell(index).setCellValue(HEADERS.get(index));
				sheet.setColumnWidth(index, 20 * 256);
			}
			workbook.createSheet("填写说明").createRow(0).createCell(0)
					.setCellValue("选定校区后导入。缺失楼栋和楼层自动创建，教室仅新增。教室编码全局唯一，房间号同楼层唯一。");
			workbook.write(output);
			return output.toByteArray();
		}
	}

	@Transactional
	public Result run(String campusId, MultipartFile file, boolean dryRun) throws IOException {
		if (file.isEmpty()) throw new IllegalArgumentException("请选择 Excel 文件");
		if (!dryRun) management.lockCatalog();
		var catalog = management.catalog(campusId);
		var buildings = new HashMap<String, Building>();
		var buildingCodesById = new HashMap<String, String>();
		var names = new HashMap<String, String>();
		for (var building : catalog.buildings()) {
			buildings.put(building.buildingCode(), building);
			buildingCodesById.put(building.id(), building.buildingCode());
			names.put(building.buildingCode(), building.buildingName());
		}
		var floors = new HashMap<Position, Floor>();
		var positionsById = new HashMap<String, Position>();
		for (var floor : catalog.floors()) {
			var position = new Position(buildingCodesById.get(floor.buildingId()), floor.floorNo());
			floors.put(position, floor);
			positionsById.put(floor.id(), position);
		}
		var numbers = new HashSet<Map.Entry<Position, String>>();
		for (var room : catalog.rooms()) {
			var position = positionsById.get(room.getFloorId());
			if (position != null) numbers.add(Map.entry(position, room.getRoomNumber()));
		}
		var codes = new HashSet<String>();
		var errors = new ArrayList<String>();
		var rows = new ArrayList<ImportRow>();
		var formatter = new DataFormatter();
		try (var input = file.getInputStream(); var workbook = new XSSFWorkbook(input)) {
			if (workbook.getNumberOfSheets() == 0) throw new IllegalArgumentException("文件没有工作表");
			var sheet = workbook.getSheetAt(0);
			if (sheet.getLastRowNum() > 5000) throw new IllegalArgumentException("单次最多导入 5000 行");
			for (int col = 0; col < HEADERS.size(); col++) {
				var header = sheet.getRow(0);
				if (header == null || !HEADERS.get(col).equals(formatter.formatCellValue(header.getCell(col)).strip())) {
					throw new IllegalArgumentException("表头不匹配，请使用教室导入模板");
				}
			}
			for (int index = 1; index <= sheet.getLastRowNum(); index++) {
				var row = sheet.getRow(index);
				if (row == null) continue;
				var values = new ArrayList<String>();
				for (int col = 0; col < HEADERS.size(); col++) values.add(formatter.formatCellValue(row.getCell(col)).strip());
				if (values.stream().allMatch(String::isBlank)) continue;
				try {
					var parsed = parse(values);
					var room = parsed.room();
					var position = parsed.position();
					var number = Map.entry(position, room.getRoomNumber());
					String knownName = names.get(position.buildingCode());
					if (knownName != null && !knownName.equals(parsed.buildingName())) {
						throw new IllegalArgumentException("相同楼栋编码对应了不同的名称");
					}
					if (codes.contains(room.getRoomCode()) || rooms.existsByRoomCodeAndIdNot(room.getRoomCode(), "")) {
						throw new IllegalArgumentException("教室编码已存在或重复：" + room.getRoomCode());
					}
					if (!numbers.add(number)) throw new IllegalArgumentException("同楼层房间号已存在或重复：" + room.getRoomNumber());
					codes.add(room.getRoomCode());
					names.put(position.buildingCode(), parsed.buildingName());
					rows.add(parsed);
				} catch (IllegalArgumentException exception) {
					errors.add("第 " + (index + 1) + " 行：" + exception.getMessage());
				}
			}
		}
		int count = rows.size() + errors.size();
		if (count == 0) errors.add("工作表没有可导入的数据");
		if (!dryRun && errors.isEmpty()) {
			for (var row : rows) {
				var building = buildings.computeIfAbsent(row.position().buildingCode(),
						code -> management.saveBuilding(campusId, new Building(null, code, row.buildingName())));
				var floor = floors.computeIfAbsent(row.position(),
						position -> management.saveFloor(campusId, new Floor(null, building.id(), position.floorNo())));
				row.room().setFloorId(floor.id());
				management.saveRoom(campusId, row.room());
			}
		}
		return new Result(count, rows.size(), errors);
	}

	private static ImportRow parse(List<String> values) {
		String buildingCode = required(values.get(0), "教学楼编码", 64);
		String buildingName = required(values.get(1), "教学楼名称", 128);
		int floor = integer(values.get(2), "楼层");
		var room = new Classroom();
		room.setRoomNumber(required(values.get(3), "房间号", 64));
		room.setRoomCode(required(values.get(4), "教室编码", 64));
		room.setRoomName(required(values.get(5), "教室名称", 128));
		int capacity = integer(values.get(6), "容量");
		if (capacity < 1) throw new IllegalArgumentException("容量必须大于 0");
		room.setCapacity(capacity);
		if (!values.get(7).isBlank()) room.setRoomType(required(values.get(7), "教室类型", 32));
		if (!values.get(8).isBlank()) room.setEquipmentCodes(required(values.get(8), "设备编码", 1000));
		return new ImportRow(new Position(buildingCode, floor), buildingName, room);
	}

	private static int integer(String value, String label) {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException(label + "必须填写整数");
		}
	}
}
