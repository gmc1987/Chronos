package com.chronos.education.scheduling.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Map;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 宿舍资源批量导入。导入只新增资源和缺失床位，不删除已有床位，避免误操作破坏住宿关系。 */
@Service
public class AccommodationImportService {
	private static final String[] HEADERS = { "楼栋编码*", "楼栋名称*", "性别规则", "房间号*", "楼层*", "床位数*" };
	private final JdbcTemplate jdbc;

	public AccommodationImportService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

	public byte[] template() {
		try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
			var sheet = workbook.createSheet("宿舍资源");
			Row header = sheet.createRow(0);
			for (int i = 0; i < HEADERS.length; i++) header.createCell(i).setCellValue(HEADERS[i]);
			Row sample = sheet.createRow(1);
			String[] values = { "A栋", "一号宿舍楼", "MIXED", "101", "1", "6" };
			for (int i = 0; i < values.length; i++) sample.createCell(i).setCellValue(values[i]);
			workbook.createSheet("填写说明").createRow(0).createCell(0)
					.setCellValue("性别规则可填写 MIXED、MALE、FEMALE；床位数会自动补齐缺失床位，不会删除已有床位。");
			workbook.write(output);
			return output.toByteArray();
		} catch (IOException exception) {
			throw new IllegalStateException("生成宿舍资源模板失败", exception);
		}
	}

	@Transactional
	public ImportResult importFile(MultipartFile file, boolean dryRun) {
		if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择宿舍资源 Excel 文件");
		try {
			return importBytes(file.getBytes(), dryRun);
		} catch (IOException exception) {
			throw new IllegalArgumentException("无法读取宿舍资源 Excel 文件", exception);
		}
	}

	@Transactional
	public ImportResult importBytes(byte[] content, boolean dryRun) {
		List<ResourceRow> rows = new ArrayList<>();
		List<String> errors = new ArrayList<>();
		Set<String> keys = new HashSet<>();
		try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
			var sheet = workbook.getSheetAt(0);
			if (sheet.getLastRowNum() > 5000) throw new IllegalArgumentException("单次最多导入5000行宿舍资源");
			DataFormatter formatter = new DataFormatter();
			for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
				Row row = sheet.getRow(rowIndex);
				if (row == null || empty(row, formatter)) continue;
				try {
					ResourceRow value = parse(row, formatter);
					if (!keys.add(value.buildingCode + "|" + value.roomNo)) throw new IllegalArgumentException("楼栋和房间重复");
					rows.add(value);
				} catch (RuntimeException exception) {
					errors.add("第" + (rowIndex + 1) + "行：" + exception.getMessage());
				}
			}
		} catch (IOException exception) {
			throw new IllegalArgumentException("无法读取宿舍资源 Excel 文件", exception);
		}
		if (!errors.isEmpty()) return new ImportResult(rows.size() + errors.size(), 0, errors);
		if (!dryRun) rows.forEach(this::upsert);
		return new ImportResult(rows.size(), rows.size(), List.of());
	}

	/** 供智能体在用户确认后维护结构化宿舍资源；与 Excel 导入使用同一幂等规则。 */
	@Transactional
	public int maintainRows(List<?> values, boolean dryRun) {
		if (values == null || values.isEmpty()) throw new IllegalArgumentException("宿舍资源候选不能为空");
		List<ResourceRow> rows = new ArrayList<>();
		for (Object item : values) {
			if (!(item instanceof Map<?, ?> value)) throw new IllegalArgumentException("宿舍资源候选格式不正确");
			rows.add(new ResourceRow(required(text(value.get("buildingCode")), "楼栋编码"),
					required(text(value.get("buildingName")), "楼栋名称"),
					text(value.get("genderPolicy")).isBlank() ? "MIXED" : text(value.get("genderPolicy")),
					required(text(value.get("roomNo")), "房间号"), number(text(value.get("floorNo")), "楼层"),
					number(text(value.get("bedCount")), "床位数")));
		}
		if (!dryRun) rows.forEach(this::upsert);
		return rows.size();
	}

	private void upsert(ResourceRow row) {
		String buildingId = jdbc.query("select id from edu_dorm_building where building_code = ?", rs -> rs.next() ? rs.getString(1) : null, row.buildingCode);
		if (buildingId == null) {
			buildingId = UUID.randomUUID().toString();
			jdbc.update("insert into edu_dorm_building(id, building_code, building_name, gender_policy, status) values (?, ?, ?, ?, 'ACTIVE')",
					buildingId, row.buildingCode, row.buildingName, row.genderPolicy);
		}
		String finalBuildingId = buildingId;
		String roomId = jdbc.query("select id from edu_dorm_room where building_id = ? and room_no = ?", rs -> rs.next() ? rs.getString(1) : null,
				finalBuildingId, row.roomNo);
		if (roomId == null) {
			roomId = UUID.randomUUID().toString();
			jdbc.update("insert into edu_dorm_room(id, building_id, room_no, floor_no, capacity, status) values (?, ?, ?, ?, ?, 'ACTIVE')",
					roomId, finalBuildingId, row.roomNo, row.floorNo, row.bedCount);
		}
		Integer existing = jdbc.queryForObject("select count(*) from edu_dorm_bed where room_id = ?", Integer.class, roomId);
		Integer capacity = jdbc.queryForObject("select capacity from edu_dorm_room where id = ?", Integer.class, roomId);
		if (row.bedCount < existing) throw new IllegalArgumentException("床位数不能小于已有床位数");
		if (row.bedCount > capacity) jdbc.update("update edu_dorm_room set capacity = ? where id = ?", row.bedCount, roomId);
		for (int index = existing + 1; index <= row.bedCount; index++) {
			jdbc.update("insert into edu_dorm_bed(id, room_id, bed_no, status) values (?, ?, ?, 'AVAILABLE')",
					UUID.randomUUID().toString(), roomId, String.format("%02d", index));
		}
	}

	private ResourceRow parse(Row row, DataFormatter formatter) {
		String buildingCode = required(text(row, 0, formatter), "楼栋编码");
		String buildingName = required(text(row, 1, formatter), "楼栋名称");
		String genderPolicy = text(row, 2, formatter).isBlank() ? "MIXED" : text(row, 2, formatter);
		String roomNo = required(text(row, 3, formatter), "房间号");
		int floorNo = number(text(row, 4, formatter), "楼层");
		int bedCount = number(text(row, 5, formatter), "床位数");
		if (floorNo < 1 || bedCount < 1) throw new IllegalArgumentException("楼层和床位数必须大于0");
		return new ResourceRow(buildingCode, buildingName, genderPolicy, roomNo, floorNo, bedCount);
	}

	private boolean empty(Row row, DataFormatter formatter) { for (int i = 0; i < HEADERS.length; i++) if (!text(row, i, formatter).isBlank()) return false; return true; }
	private String text(Row row, int index, DataFormatter formatter) { return formatter.formatCellValue(row.getCell(index)).trim(); }
	private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
	private String required(String value, String label) { if (value.isBlank()) throw new IllegalArgumentException(label + "不能为空"); return value; }
	private int number(String value, String label) { try { return Integer.parseInt(required(value, label)); } catch (NumberFormatException exception) { throw new IllegalArgumentException(label + "必须是数字"); } }

	public record ResourceRow(String buildingCode, String buildingName, String genderPolicy, String roomNo, int floorNo, int bedCount) {}
	public record ImportResult(int totalCount, int successCount, List<String> errors) {}
}
