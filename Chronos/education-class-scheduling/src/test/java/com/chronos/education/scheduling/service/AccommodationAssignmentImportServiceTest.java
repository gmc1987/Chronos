package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.chronos.education.scheduling.service.AccommodationAssignmentImportService.CommitResult;
import com.chronos.education.scheduling.service.AccommodationAssignmentImportService.PreviewResult;
import com.chronos.education.scheduling.service.AccommodationAssignmentImportService.PreviewRow;
import com.fasterxml.jackson.databind.ObjectMapper;

class AccommodationAssignmentImportServiceTest {
	private JdbcTemplate jdbc;
	private AccommodationService accommodations;
	private ObjectMapper json;
	private AccommodationAssignmentImportService service;

	@BeforeEach
	void setUp() {
		jdbc = mock(JdbcTemplate.class);
		accommodations = mock(AccommodationService.class);
		json = new ObjectMapper().findAndRegisterModules();
		service = new AccommodationAssignmentImportService(jdbc, accommodations, json);
	}

	@Test
	void templateKeepsIdentifiersAsText() throws Exception {
		try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(service.template()))) {
			var sheet = workbook.getSheet("住宿人工分配");
			assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("学号");
			assertThat(sheet.getRow(1).getCell(0).getCellType()).isEqualTo(CellType.STRING);
			assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("0101");
			assertThat(workbook.getSheet("填写说明").getRow(0).getCell(0).getStringCellValue())
					.contains(AccommodationAssignmentImportService.TEMPLATE_VERSION);
		}
	}

	@Test
	void previewReportsDuplicateStudentAndBedRows() throws Exception {
		when(jdbc.queryForList(contains("select id, term_code"), eq("term-1"), eq("org-1")))
				.thenReturn(List.of(Map.of("id", "term-1", "term_code", "ORG:2026-1", "term_name", "第一学期", "status", "ACTIVE")));
		when(jdbc.queryForList(contains("from edu_student_profile student"), eq("0001"), eq("org-1")))
				.thenReturn(List.of(Map.of("id", "student-1", "student_no", "0001", "student_name", "张同学",
						"gender", "MALE", "enrollment_status", "ACTIVE", "organization_id", "org-1")));
		when(jdbc.queryForList(contains("where building.organization_id=?"), eq("org-1"), eq("A01"), eq("0101"), eq("01")))
				.thenReturn(List.of(Map.of("id", "bed-1", "bed_status", "AVAILABLE", "room_status", "ACTIVE",
						"building_status", "ACTIVE", "gender_policy", "MALE", "building_name", "一号楼",
						"organization_id", "org-1")));
		when(jdbc.queryForObject(contains("from edu_student_accommodation"), eq(Long.class), anyString(), anyString()))
				.thenReturn(0L);

		var result = service.preview(workbook(
				new String[] { "0001", "A01", "0101", "01", "" },
				new String[] { "0001", "A01", "0101", "01", "重复" }),
				"org-1", "term-1", AccommodationAssignmentImportService.TEMPLATE_VERSION, "operator");

		assertThat(result.committable()).isFalse();
		assertThat(result.errorCount()).isEqualTo(1);
		assertThat(result.rows().get(1).errors()).extracting("code")
				.contains("DUPLICATE_STUDENT", "DUPLICATE_BED");
	}

	@Test
	void repeatedCommitReturnsOriginalResultWithoutAssigningAgain() throws Exception {
		CommitResult original = new CommitResult("preview-1", "preview-1", "org-1", "term-1", "ORG:2026-1",
				"COMMITTED", 2, LocalDateTime.now(), List.of());
		when(jdbc.queryForList(contains("where operator_id = ? and idempotency_key = ?"), eq("operator"), eq("key-1")))
				.thenReturn(List.of(Map.of("id", "preview-1", "commit_result", json.writeValueAsString(original))));

		CommitResult repeated = service.commit("preview-1", "org-1", "term-1",
				AccommodationAssignmentImportService.TEMPLATE_VERSION, "key-1", "operator");

		assertThat(repeated).isEqualTo(original);
		verifyNoInteractions(accommodations);
	}

	@Test
	void commitRevalidatesAndRejectsBedOccupiedAfterPreview() throws Exception {
		LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(20);
		PreviewRow row = new PreviewRow(2, "0001", "张同学", "student-1", "A01", "一号楼",
				"0101", "01", "bed-1", "", "CREATE", List.of());
		PreviewResult preview = new PreviewResult("preview-1", "org-1", "term-1", "ORG:2026-1",
				AccommodationAssignmentImportService.TEMPLATE_VERSION, "digest-1", expiresAt,
				1, 1, 0, 0, 1, true, List.of(row));
		Map<String, Object> batch = Map.ofEntries(
				Map.entry("id", "preview-1"),
				Map.entry("organization_id", "org-1"),
				Map.entry("term_id", "term-1"),
				Map.entry("term_code", "ORG:2026-1"),
				Map.entry("operator_id", "operator"),
				Map.entry("file_digest", "digest-1"),
				Map.entry("template_version", AccommodationAssignmentImportService.TEMPLATE_VERSION),
				Map.entry("status", "PREVIEW_READY"),
				Map.entry("expires_at", Timestamp.valueOf(expiresAt)),
				Map.entry("rows_json", json.writeValueAsString(List.of(row))),
				Map.entry("preview_result", json.writeValueAsString(preview)));
		when(jdbc.queryForList(contains("where operator_id = ? and idempotency_key = ?"), eq("operator"), eq("key-1")))
				.thenReturn(List.of());
		when(jdbc.queryForList(contains("where id=? for update"), eq("preview-1"))).thenReturn(List.of(batch));
		when(jdbc.queryForList(contains("select id, term_code"), eq("term-1"), eq("org-1")))
				.thenReturn(List.of(Map.of("id", "term-1", "term_code", "ORG:2026-1", "term_name", "第一学期", "status", "ACTIVE")));
		when(jdbc.queryForList(contains("from edu_student_profile student"), eq("0001"), eq("org-1")))
				.thenReturn(List.of(Map.of("id", "student-1", "student_no", "0001", "student_name", "张同学",
						"gender", "MALE", "enrollment_status", "ACTIVE", "organization_id", "org-1")));
		when(jdbc.queryForList(contains("where building.organization_id=?"), eq("org-1"), eq("A01"), eq("0101"), eq("01")))
				.thenReturn(List.of(Map.of("id", "bed-1", "bed_status", "AVAILABLE", "room_status", "ACTIVE",
						"building_status", "ACTIVE", "gender_policy", "MALE", "building_name", "一号楼",
						"organization_id", "org-1")));
		when(jdbc.queryForObject(contains("student_id=?"), eq(Long.class), eq("ORG:2026-1"), eq("student-1")))
				.thenReturn(0L);
		when(jdbc.queryForObject(contains("bed_id=?"), eq(Long.class), eq("ORG:2026-1"), eq("bed-1")))
				.thenReturn(1L);

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.commit(
				"preview-1", "org-1", "term-1", AccommodationAssignmentImportService.TEMPLATE_VERSION,
				"key-1", "operator"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("状态已变化");
		verifyNoInteractions(accommodations);
	}

	private byte[] workbook(String[]... rows) throws Exception {
		try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
			var sheet = workbook.createSheet("住宿人工分配");
			String[] headers = { "学号", "楼栋编码", "房间号", "床位号", "备注" };
			var header = sheet.createRow(0);
			for (int index = 0; index < headers.length; index++)
				header.createCell(index, CellType.STRING).setCellValue(headers[index]);
			for (int rowIndex = 0; rowIndex < rows.length; rowIndex++) {
				var row = sheet.createRow(rowIndex + 1);
				for (int column = 0; column < rows[rowIndex].length; column++)
					row.createCell(column, CellType.STRING).setCellValue(rows[rowIndex][column]);
			}
			workbook.write(output);
			return output.toByteArray();
		}
	}
}
