package com.chronos.education.scheduling.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.chronos.education.scheduling.service.AccommodationService.AssignmentCommand;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class AccommodationAssignmentImportService {
	public static final String TEMPLATE_VERSION = "1.0";
	private static final int MAX_ROWS = 2000;
	private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
	private static final String[] HEADERS = { "学号", "楼栋编码", "房间号", "床位号", "备注" };

	private final JdbcTemplate jdbc;
	private final AccommodationService accommodations;
	private final ObjectMapper json;

	public AccommodationAssignmentImportService(JdbcTemplate jdbc, AccommodationService accommodations, ObjectMapper json) {
		this.jdbc = jdbc;
		this.accommodations = accommodations;
		this.json = json;
	}

	public byte[] template() {
		try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
			var sheet = workbook.createSheet("住宿人工分配");
			var textStyle = workbook.createCellStyle();
			textStyle.setDataFormat(workbook.createDataFormat().getFormat("@"));
			Row header = sheet.createRow(0);
			for (int index = 0; index < HEADERS.length; index++) {
				header.createCell(index, CellType.STRING).setCellValue(HEADERS[index]);
				sheet.setColumnWidth(index, index == 4 ? 32 * 256 : 18 * 256);
				if (index < 4) sheet.setDefaultColumnStyle(index, textStyle);
			}
			Row sample = sheet.createRow(1);
			String[] values = { "2026000001", "A01", "0101", "01", "新生集中入住" };
			for (int index = 0; index < values.length; index++) {
				var cell = sample.createCell(index, CellType.STRING);
				cell.setCellStyle(textStyle);
				cell.setCellValue(values[index]);
			}
			var guide = workbook.createSheet("填写说明");
			guide.createRow(0).createCell(0).setCellValue("模板版本：" + TEMPLATE_VERSION);
			guide.createRow(1).createCell(0).setCellValue("学号、楼栋编码、房间号、床位号为必填文本列，请勿改为数字或公式，以保留前导零。");
			guide.createRow(2).createCell(0).setCellValue("机构和学期以页面选择为准；导入只新增住宿分配，不用于换宿或覆盖已有分配。");
			workbook.write(output);
			return output.toByteArray();
		} catch (IOException exception) {
			throw new IllegalStateException("生成住宿人工分配模板失败", exception);
		}
	}

	@Transactional
	public PreviewResult preview(MultipartFile file, String organizationId, String termId, String templateVersion, String actor) {
		if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择住宿人工分配 Excel 文件");
		if (file.getSize() > MAX_FILE_SIZE) throw new IllegalArgumentException("导入文件不能超过10MB");
		if (!TEMPLATE_VERSION.equals(templateVersion)) throw new IllegalArgumentException("模板版本不受支持，请重新下载模板");
		try {
			return preview(file.getBytes(), organizationId, termId, templateVersion, actor);
		} catch (IOException exception) {
			throw new IllegalArgumentException("无法读取住宿人工分配 Excel 文件", exception);
		}
	}

	@Transactional
	public PreviewResult preview(byte[] content, String organizationId, String termId, String templateVersion, String actor) {
		String owner = required(actor, "操作人");
		String targetOrganization = required(organizationId, "机构");
		TermContext term = resolveTerm(termId, targetOrganization);
		List<ParsedRow> parsed = parse(content);
		List<PreviewRow> rows = validate(parsed, targetOrganization, term.termCode());
		long errorCount = rows.stream().filter(row -> !row.errors().isEmpty()).count();
		long duplicateCount = rows.stream()
				.filter(row -> row.errors().stream().anyMatch(error -> error.code().startsWith("DUPLICATE_")))
				.count();
		String previewId = UUID.randomUUID().toString();
		String digest = digest(content);
		LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(30);
		PreviewResult result = new PreviewResult(previewId, targetOrganization, term.id(), term.termCode(),
				templateVersion, digest, expiresAt, rows.size(), rows.size() - (int) errorCount, (int) errorCount,
				(int) duplicateCount, rows.size() - (int) errorCount, errorCount == 0, rows);
		jdbc.update("""
				insert into edu_accommodation_assignment_import_batch
				  (id, organization_id, term_id, term_code, operator_id, file_digest, template_version,
				   status, expires_at, rows_json, preview_result, created_at, updated_at)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), ?, ?)
				""", previewId, targetOrganization, term.id(), term.termCode(), owner, digest, templateVersion,
				errorCount == 0 ? "PREVIEW_READY" : "PREVIEW_FAILED", Timestamp.valueOf(expiresAt),
				write(rows), write(result), Timestamp.valueOf(LocalDateTime.now()), Timestamp.valueOf(LocalDateTime.now()));
		return result;
	}

	@Transactional
	public CommitResult commit(String previewId, String organizationId, String termId, String templateVersion,
			String idempotencyKey, String actor) {
		String owner = required(actor, "操作人");
		String key = required(idempotencyKey, "幂等键");
		if (key.length() > 128) throw new IllegalArgumentException("幂等键不能超过128个字符");
		List<Map<String, Object>> prior = jdbc.queryForList("""
				select id, commit_result from edu_accommodation_assignment_import_batch
				where operator_id = ? and idempotency_key = ?
				""", owner, key);
		if (!prior.isEmpty()) {
			if (!String.valueOf(prior.get(0).get("id")).equals(previewId))
				throw new IllegalArgumentException("该幂等键已用于其他预检批次");
			return read(String.valueOf(prior.get(0).get("commit_result")), CommitResult.class);
		}
		Map<String, Object> batch = batchForUpdate(previewId);
		requireBatchBinding(batch, owner, organizationId, termId, templateVersion);
		if ("COMMITTED".equals(batch.get("status")))
			return read(String.valueOf(batch.get("commit_result")), CommitResult.class);
		if (!"PREVIEW_READY".equals(batch.get("status"))) throw new IllegalStateException("预检未通过，不能提交");
		LocalDateTime expiresAt = ((Timestamp) batch.get("expires_at")).toLocalDateTime();
		if (!expiresAt.isAfter(LocalDateTime.now())) throw new IllegalStateException("预检已过期，请重新上传预检");
		if (String.valueOf(batch.get("file_digest")).isBlank()) throw new IllegalStateException("预检文件摘要无效");

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> stored = read(String.valueOf(batch.get("rows_json")), List.class);
		List<ParsedRow> parsed = stored.stream().map(this::storedRow).toList();
		List<PreviewRow> current = validate(parsed, organizationId, String.valueOf(batch.get("term_code")));
		List<PreviewRow> invalid = current.stream().filter(row -> !row.errors().isEmpty()).toList();
		if (!invalid.isEmpty())
			throw new IllegalStateException("预检后学生或床位状态已变化，请重新预检（首个冲突在第" + invalid.get(0).rowNumber() + "行）");

		List<CommittedRow> committed = new ArrayList<>();
		for (PreviewRow row : current) {
			Map<String, Object> assignment = accommodations.assign(
					new AssignmentCommand(String.valueOf(batch.get("term_code")), row.studentId(), row.bedId(), row.remark()),
					owner, organizationId);
			String assignmentId = String.valueOf(assignment.get("id"));
			jdbc.update("update edu_student_accommodation set import_batch_id=? where id=?", previewId, assignmentId);
			committed.add(new CommittedRow(row.rowNumber(), assignmentId, row.studentId(),
					row.studentNo(), row.studentName(), row.bedId(), row.buildingCode(), row.roomNo(), row.bedNo()));
		}
		CommitResult result = new CommitResult(previewId, previewId, organizationId, termId,
				String.valueOf(batch.get("term_code")), "COMMITTED", committed.size(), LocalDateTime.now(), committed);
		jdbc.update("""
				update edu_accommodation_assignment_import_batch
				set status='COMMITTED', idempotency_key=?, commit_result=cast(? as jsonb), committed_at=?, updated_at=?
				where id=?
				""", key, write(result), Timestamp.valueOf(result.committedAt()), Timestamp.valueOf(result.committedAt()), previewId);
		return result;
	}

	public BatchResult batch(String batchId, String actor) {
		Map<String, Object> value = batchRow(batchId, false);
		if (!required(actor, "操作人").equals(String.valueOf(value.get("operator_id"))))
			throw new IllegalArgumentException("导入批次不属于当前操作人");
		PreviewResult preview = read(String.valueOf(value.get("preview_result")), PreviewResult.class);
		CommitResult commit = value.get("commit_result") == null ? null
				: read(String.valueOf(value.get("commit_result")), CommitResult.class);
		return new BatchResult(String.valueOf(value.get("id")), String.valueOf(value.get("status")),
				String.valueOf(value.get("organization_id")), String.valueOf(value.get("term_id")),
				String.valueOf(value.get("operator_id")), String.valueOf(value.get("file_digest")),
				String.valueOf(value.get("template_version")), ((Timestamp) value.get("expires_at")).toLocalDateTime(),
				preview, commit);
	}

	public String batchOrganization(String batchId) {
		return String.valueOf(batchRow(batchId, false).get("organization_id"));
	}

	public String termOrganization(String termId) {
		try {
			return jdbc.queryForObject("select organization_id from edu_academic_term where id = ?", String.class,
					required(termId, "学期"));
		} catch (org.springframework.dao.EmptyResultDataAccessException exception) {
			throw new IllegalArgumentException("学期不存在");
		}
	}

	private List<ParsedRow> parse(byte[] content) {
		if (content == null || content.length == 0) throw new IllegalArgumentException("导入文件不能为空");
		List<ParsedRow> rows = new ArrayList<>();
		try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
			if (workbook.getNumberOfSheets() == 0) throw new IllegalArgumentException("导入文件缺少工作表");
			var sheet = workbook.getSheetAt(0);
			if (sheet.getLastRowNum() > MAX_ROWS) throw new IllegalArgumentException("单次最多导入" + MAX_ROWS + "行");
			DataFormatter formatter = new DataFormatter();
			validateHeader(sheet.getRow(0), formatter);
			for (int index = 1; index <= sheet.getLastRowNum(); index++) {
				Row row = sheet.getRow(index);
				if (row == null || empty(row, formatter)) continue;
				List<RowError> errors = new ArrayList<>();
				String[] values = new String[HEADERS.length];
				for (int column = 0; column < HEADERS.length; column++) {
					var cell = row.getCell(column);
					if (cell != null && cell.getCellType() == CellType.FORMULA) {
						errors.add(new RowError("FORMULA_NOT_ALLOWED", HEADERS[column] + "不能使用公式"));
						values[column] = "";
					} else {
						if (column < 4 && cell != null && cell.getCellType() != CellType.STRING
								&& cell.getCellType() != CellType.BLANK)
							errors.add(new RowError("TEXT_REQUIRED", HEADERS[column] + "必须设置为文本格式"));
						values[column] = cell == null ? "" : formatter.formatCellValue(cell).trim();
					}
				}
				for (int column = 0; column < 4; column++)
					if (values[column].isBlank()) errors.add(new RowError("REQUIRED", HEADERS[column] + "不能为空"));
				if (values[4].length() > 500) errors.add(new RowError("REMARK_TOO_LONG", "备注不能超过500个字符"));
				rows.add(new ParsedRow(index + 1, values[0], values[1], values[2], values[3], values[4], errors));
			}
		} catch (IOException exception) {
			throw new IllegalArgumentException("无法解析住宿人工分配 Excel 文件", exception);
		}
		if (rows.isEmpty()) throw new IllegalArgumentException("导入文件没有可处理的数据行");
		return rows;
	}

	private void validateHeader(Row header, DataFormatter formatter) {
		if (header == null) throw new IllegalArgumentException("模板表头缺失");
		for (int index = 0; index < HEADERS.length; index++) {
			String actual = formatter.formatCellValue(header.getCell(index)).trim();
			if (!HEADERS[index].equals(actual))
				throw new IllegalArgumentException("模板表头不匹配，第" + (index + 1) + "列应为“" + HEADERS[index] + "”");
		}
	}

	private List<PreviewRow> validate(List<ParsedRow> parsed, String organizationId, String termCode) {
		Map<String, Integer> studentsInBatch = new HashMap<>();
		Map<String, Integer> bedsInBatch = new HashMap<>();
		List<PreviewRow> result = new ArrayList<>();
		for (ParsedRow source : parsed) {
			List<RowError> errors = new ArrayList<>(source.errors());
			Integer previousStudent = studentsInBatch.putIfAbsent(source.studentNo(), source.rowNumber());
			if (!source.studentNo().isBlank() && previousStudent != null)
				errors.add(new RowError("DUPLICATE_STUDENT", "学号与第" + previousStudent + "行重复"));
			String bedKey = source.buildingCode() + "|" + source.roomNo() + "|" + source.bedNo();
			Integer previousBed = bedsInBatch.putIfAbsent(bedKey, source.rowNumber());
			if (!source.buildingCode().isBlank() && !source.roomNo().isBlank() && !source.bedNo().isBlank() && previousBed != null)
				errors.add(new RowError("DUPLICATE_BED", "床位与第" + previousBed + "行重复"));

			Map<String, Object> student = findStudent(source.studentNo(), organizationId, errors);
			Map<String, Object> bed = findBed(source, organizationId, errors);
			String studentId = text(student, "id");
			String bedId = text(bed, "id");
			if (!studentId.isBlank()) {
				if (!"ACTIVE".equalsIgnoreCase(text(student, "enrollment_status")))
					errors.add(new RowError("STUDENT_NOT_ELIGIBLE", "学生不具备住宿资格"));
				Long assigned = jdbc.queryForObject("""
						select count(*) from edu_student_accommodation
						where semester_code=? and student_id=? and status='ACTIVE'
						""", Long.class, termCode, studentId);
				if (assigned != null && assigned > 0)
					errors.add(new RowError("STUDENT_ALREADY_ASSIGNED", "学生在所选学期已有有效住宿分配"));
			}
			if (!bedId.isBlank()) {
				if (!"AVAILABLE".equalsIgnoreCase(text(bed, "bed_status"))
						|| !"ACTIVE".equalsIgnoreCase(text(bed, "room_status"))
						|| !"ACTIVE".equalsIgnoreCase(text(bed, "building_status")))
					errors.add(new RowError("BED_UNAVAILABLE", "楼栋、房间或床位未启用"));
				Long occupied = jdbc.queryForObject("""
						select count(*) from edu_student_accommodation
						where semester_code=? and bed_id=? and status='ACTIVE'
						""", Long.class, termCode, bedId);
				if (occupied != null && occupied > 0)
					errors.add(new RowError("BED_OCCUPIED", "床位在所选学期已被占用"));
			}
			if (!studentId.isBlank() && !bedId.isBlank()
					&& !genderAllowed(text(student, "gender"), text(bed, "gender_policy")))
				errors.add(new RowError("GENDER_POLICY_MISMATCH", "学生性别不符合楼栋性别规则"));
			result.add(new PreviewRow(source.rowNumber(), source.studentNo(), text(student, "student_name"),
					studentId, source.buildingCode(), text(bed, "building_name"), source.roomNo(), source.bedNo(),
					bedId, source.remark(), errors.isEmpty() ? "CREATE" : "ERROR", List.copyOf(errors)));
		}
		return result;
	}

	private Map<String, Object> findStudent(String studentNo, String organizationId, List<RowError> errors) {
		if (studentNo.isBlank()) return Map.of();
		List<Map<String, Object>> matches = jdbc.queryForList("""
				select student.id, student.student_no, student.student_name, student.gender,
				       student.enrollment_status, class.campus_id as organization_id
				from edu_student_profile student
				join edu_administrative_class class on class.id=student.administrative_class_id
				where student.student_no=? and class.campus_id=?
				""", studentNo, organizationId);
		if (matches.size() == 1) return matches.get(0);
		if (matches.size() > 1) errors.add(new RowError("AMBIGUOUS_STUDENT", "学号在所选机构匹配到多名学生"));
		else {
			Long elsewhere = jdbc.queryForObject("""
					select count(*) from edu_student_profile student
					join edu_administrative_class class on class.id=student.administrative_class_id
					where student.student_no=? and class.campus_id<>?
					""", Long.class, studentNo, organizationId);
			errors.add(new RowError(elsewhere != null && elsewhere > 0 ? "CROSS_ORGANIZATION_STUDENT" : "STUDENT_NOT_FOUND",
					elsewhere != null && elsewhere > 0 ? "学生不属于所选机构" : "学号未匹配到学生"));
		}
		return Map.of();
	}

	private Map<String, Object> findBed(ParsedRow source, String organizationId, List<RowError> errors) {
		if (source.buildingCode().isBlank() || source.roomNo().isBlank() || source.bedNo().isBlank()) return Map.of();
		List<Map<String, Object>> matches = jdbc.queryForList("""
				select bed.id, bed.status as bed_status, room.status as room_status,
				       building.status as building_status, building.gender_policy,
				       building.building_name, building.organization_id
				from edu_dorm_bed bed
				join edu_dorm_room room on room.id=bed.room_id
				join edu_dorm_building building on building.id=room.building_id
				where building.organization_id=? and building.building_code=? and room.room_no=? and bed.bed_no=?
				""", organizationId, source.buildingCode(), source.roomNo(), source.bedNo());
		if (matches.size() == 1) return matches.get(0);
		if (matches.size() > 1) errors.add(new RowError("AMBIGUOUS_BED", "床位在所选机构匹配到多条记录"));
		else {
			Long elsewhere = jdbc.queryForObject("""
					select count(*) from edu_dorm_bed bed
					join edu_dorm_room room on room.id=bed.room_id
					join edu_dorm_building building on building.id=room.building_id
					where building.organization_id<>? and building.building_code=? and room.room_no=? and bed.bed_no=?
					""", Long.class, organizationId, source.buildingCode(), source.roomNo(), source.bedNo());
			errors.add(new RowError(elsewhere != null && elsewhere > 0 ? "CROSS_ORGANIZATION_BED" : "BED_NOT_FOUND",
					elsewhere != null && elsewhere > 0 ? "床位不属于所选机构" : "楼栋、房间或床位未匹配"));
		}
		return Map.of();
	}

	private TermContext resolveTerm(String termId, String organizationId) {
		List<Map<String, Object>> values = jdbc.queryForList("""
				select id, term_code, term_name, status from edu_academic_term
				where id=? and organization_id=?
				""", required(termId, "学期"), organizationId);
		if (values.isEmpty()) throw new IllegalArgumentException("学期不存在或不属于所选机构");
		Map<String, Object> value = values.get(0);
		if (!"ACTIVE".equalsIgnoreCase(String.valueOf(value.get("status"))))
			throw new IllegalArgumentException("所选学期未启用");
		return new TermContext(String.valueOf(value.get("id")), String.valueOf(value.get("term_code")));
	}

	private void requireBatchBinding(Map<String, Object> batch, String actor, String organizationId, String termId,
			String templateVersion) {
		if (!actor.equals(String.valueOf(batch.get("operator_id")))) throw new IllegalArgumentException("预检不属于当前操作人");
		if (!required(organizationId, "机构").equals(String.valueOf(batch.get("organization_id"))))
			throw new IllegalArgumentException("机构已切换，请重新预检");
		if (!required(termId, "学期").equals(String.valueOf(batch.get("term_id"))))
			throw new IllegalArgumentException("学期已切换，请重新预检");
		if (!TEMPLATE_VERSION.equals(templateVersion)
				|| !templateVersion.equals(String.valueOf(batch.get("template_version"))))
			throw new IllegalArgumentException("模板版本不一致，请重新预检");
		PreviewResult preview = read(String.valueOf(batch.get("preview_result")), PreviewResult.class);
		if (!String.valueOf(batch.get("file_digest")).equals(preview.fileDigest())
				|| !organizationId.equals(preview.organizationId()) || !termId.equals(preview.termId())
				|| !templateVersion.equals(preview.templateVersion()))
			throw new IllegalStateException("预检批次绑定信息不一致，请重新预检");
		resolveTerm(termId, organizationId);
	}

	private Map<String, Object> batchForUpdate(String id) { return batchRow(id, true); }
	private Map<String, Object> batchRow(String id, boolean lock) {
		List<Map<String, Object>> values = jdbc.queryForList(
				"select * from edu_accommodation_assignment_import_batch where id=?" + (lock ? " for update" : ""),
				required(id, "预检批次"));
		if (values.isEmpty()) throw new IllegalArgumentException("预检批次不存在");
		return new LinkedHashMap<>(values.get(0));
	}

	private ParsedRow storedRow(Map<String, Object> value) {
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> rawErrors = (List<Map<String, Object>>) value.getOrDefault("errors", List.of());
		List<RowError> errors = rawErrors.stream()
				.map(error -> new RowError(String.valueOf(error.get("code")), String.valueOf(error.get("message"))))
				.filter(error -> "FORMULA_NOT_ALLOWED".equals(error.code()) || "TEXT_REQUIRED".equals(error.code())
						|| "REQUIRED".equals(error.code()) || "REMARK_TOO_LONG".equals(error.code()))
				.toList();
		return new ParsedRow(number(value.get("rowNumber")), string(value.get("studentNo")),
				string(value.get("buildingCode")), string(value.get("roomNo")), string(value.get("bedNo")),
				string(value.get("remark")), errors);
	}

	private boolean genderAllowed(String gender, String policy) {
		String expected = policy == null ? "MIXED" : policy.trim().toUpperCase();
		if (expected.isBlank() || "MIXED".equals(expected)) return true;
		String actual = gender == null ? "" : gender.trim().toUpperCase();
		boolean male = "MALE".equals(actual) || "M".equals(actual) || "男".equals(actual);
		boolean female = "FEMALE".equals(actual) || "F".equals(actual) || "女".equals(actual);
		return ("MALE".equals(expected) && male) || ("FEMALE".equals(expected) && female);
	}

	private boolean empty(Row row, DataFormatter formatter) {
		for (int index = 0; index < HEADERS.length; index++)
			if (!formatter.formatCellValue(row.getCell(index)).trim().isBlank()) return false;
		return true;
	}

	private String digest(byte[] content) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
		} catch (Exception exception) {
			throw new IllegalStateException("计算文件摘要失败", exception);
		}
	}

	private String write(Object value) {
		try {
			return json.writeValueAsString(value);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("序列化导入批次失败", exception);
		}
	}

	private <T> T read(String value, Class<T> type) {
		try {
			return json.readValue(value, type);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("读取导入批次失败", exception);
		}
	}

	private String text(Map<String, Object> value, String key) {
		Object item = value.get(key);
		return item == null ? "" : String.valueOf(item);
	}
	private String string(Object value) { return value == null ? "" : String.valueOf(value); }
	private int number(Object value) { return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value)); }
	private String required(String value, String label) {
		if (value == null || value.isBlank()) throw new IllegalArgumentException(label + "不能为空");
		return value.trim();
	}

	private record TermContext(String id, String termCode) {}
	private record ParsedRow(int rowNumber, String studentNo, String buildingCode, String roomNo, String bedNo,
			String remark, List<RowError> errors) {}
	public record RowError(String code, String message) {}
	public record PreviewRow(int rowNumber, String studentNo, String studentName, String studentId,
			String buildingCode, String buildingName, String roomNo, String bedNo, String bedId, String remark,
			String action, List<RowError> errors) {}
	public record PreviewResult(String previewId, String organizationId, String termId, String termCode,
			String templateVersion, String fileDigest, LocalDateTime expiresAt, int totalCount, int validCount,
			int errorCount, int duplicateCount, int createCount, boolean committable, List<PreviewRow> rows) {}
	public record CommittedRow(int rowNumber, String assignmentId, String studentId, String studentNo,
			String studentName, String bedId, String buildingCode, String roomNo, String bedNo) {}
	public record CommitResult(String batchId, String previewId, String organizationId, String termId,
			String termCode, String status, int successCount, LocalDateTime committedAt, List<CommittedRow> rows) {}
	public record BatchResult(String batchId, String status, String organizationId, String termId, String operatorId,
			String fileDigest, String templateVersion, LocalDateTime expiresAt, PreviewResult preview,
			CommitResult commit) {}
}
