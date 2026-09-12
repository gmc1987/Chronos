package com.chronos.education.scheduling.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.multipart.MultipartFile;

import com.chronos.education.scheduling.dao.ClassroomRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.model.Classroom;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.ScheduleEntryCommand;
import com.chronos.education.scheduling.model.ScheduleEntryView;
import com.chronos.service.iService.IAuditLogService;

/** 课表 Excel 模板、全量校验导入和多维导出。 */
@Service
public class ScheduleExcelService {
	private static final int MAX_IMPORT_ROWS = 5_000;
	private static final String[] HEADERS = {
			"教学任务编码", "教室编码", "星期", "节次", "连堂节数",
			"周模式", "开始周", "结束周", "是否锁定"
	};
	private final ClassSchedulingService scheduling;
	private final CourseOfferingRepository offerings;
	private final ClassroomRepository classrooms;
	private final IAuditLogService audit;

	public ScheduleExcelService(
			ClassSchedulingService scheduling,
			CourseOfferingRepository offerings,
			ClassroomRepository classrooms,
			IAuditLogService audit) {
		this.scheduling = scheduling;
		this.offerings = offerings;
		this.classrooms = classrooms;
		this.audit = audit;
	}

	public byte[] template() {
		try (Workbook workbook = new XSSFWorkbook();
				ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			Sheet sheet = workbook.createSheet("课表导入");
			writeHeader(sheet, workbook);
			Row example = sheet.createRow(1);
			String[] values = { "OFFERING-001", "ROOM-101", "1", "1", "2", "ALL", "1", "20", "否" };
			for (int index = 0; index < values.length; index++) {
				example.createCell(index).setCellValue(values[index]);
			}
			Sheet guide = workbook.createSheet("填写说明");
			guide.createRow(0).createCell(0).setCellValue("星期填写 1-7，分别代表星期一至星期日；周模式填写 ALL、ODD 或 EVEN。请勿修改表头或使用公式。");
			setWidths(sheet);
			guide.setColumnWidth(0, 100 * 256);
			workbook.write(output);
			return output.toByteArray();
		} catch (IOException exception) {
			throw new IllegalStateException("课表导入模板生成失败", exception);
		}
	}

	@Transactional
	public Map<String, Object> importFile(
			String semesterCode,
			MultipartFile file,
			boolean dryRun,
			String actor) {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("请选择 XLSX 文件");
		}
		if (file.getSize() > 10L * 1024 * 1024) {
			throw new IllegalArgumentException("课表文件不能超过 10MB");
		}
		try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file.getBytes()))) {
			rejectHiddenSheets(workbook);
			Sheet sheet = workbook.getSheet("课表导入");
			if (sheet == null) {
				throw new IllegalArgumentException("缺少“课表导入”工作表");
			}
			if (sheet.getLastRowNum() > MAX_IMPORT_ROWS) {
				throw new IllegalArgumentException("单次最多导入 5000 行课表");
			}
			assertHeader(sheet.getRow(0));
			Map<String, CourseOffering> offeringByCode = offerings
					.findBySemesterCodeOrderByOfferingCode(semesterCode)
					.stream()
					.collect(Collectors.toMap(CourseOffering::getOfferingCode, item -> item));
			Map<String, Classroom> classroomByCode = classrooms.findAll().stream()
					.collect(Collectors.toMap(Classroom::getRoomCode, item -> item));
			List<String> errors = new ArrayList<>();
			int success = 0;
			for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
				Row row = sheet.getRow(rowIndex);
				if (row == null || blank(row)) {
					continue;
				}
				try {
					rejectFormula(row);
					CourseOffering offering = required(
							offeringByCode.get(text(row, 0)),
							"教学任务编码不存在");
					Classroom classroom = required(
							classroomByCode.get(text(row, 1)),
							"教室编码不存在");
					ScheduleEntryCommand command = new ScheduleEntryCommand(
							semesterCode,
							offering.getId(),
							classroom.getId(),
							integer(row, 2, "星期"),
							integer(row, 3, "节次"),
							integer(row, 4, "连堂节数"),
							text(row, 5).toUpperCase(),
							integer(row, 6, "开始周"),
							integer(row, 7, "结束周"),
							booleanValue(text(row, 8)),
							null);
					scheduling.saveEntry(null, command);
					success++;
				} catch (RuntimeException exception) {
					errors.add("第 " + (rowIndex + 1) + " 行：" + exception.getMessage());
				}
			}
			if (dryRun || !errors.isEmpty()) {
				TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
			}
			if (!dryRun && !errors.isEmpty()) {
				throw new IllegalArgumentException(String.join("；", errors));
			}
			if (!dryRun) {
				audit.log(actor, "EDUCATION_SCHEDULE_IMPORT", "semester=" + semesterCode + ", count=" + success);
			}
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("valid", errors.isEmpty());
			result.put("dryRun", dryRun);
			result.put("successCount", success);
			result.put("errors", errors);
			return result;
		} catch (IOException exception) {
			throw new IllegalArgumentException("课表 Excel 解析失败", exception);
		}
	}

	@Transactional
	public byte[] export(
			String semesterCode,
			String dimension,
			String targetId,
			String actor) {
		List<ScheduleEntryView> values = scheduling.schedule(semesterCode, dimension, targetId);
		try (SXSSFWorkbook workbook = new SXSSFWorkbook(100);
				ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			try {
				workbook.setCompressTempFiles(true);
				Sheet sheet = workbook.createSheet("课表");
				String[] headers = {
						"教学任务编码", "课程", "教学班", "教师", "教室",
						"星期", "节次", "连堂节数", "周模式", "开始周", "结束周", "状态"
				};
				writeHeader(sheet, workbook, headers);
				int rowIndex = 1;
				for (ScheduleEntryView value : values) {
					Row row = sheet.createRow(rowIndex++);
					Object[] cells = {
							value.offeringCode(), value.courseName(), value.teachingClassName(), value.teacherName(),
							value.classroomName(), value.dayOfWeek(), value.periodNo(), value.durationPeriods(),
							value.weekPattern(), value.startWeek(), value.endWeek(), value.status()
					};
					for (int index = 0; index < cells.length; index++) {
						row.createCell(index).setCellValue(String.valueOf(cells[index]));
					}
				}
				workbook.write(output);
				audit.log(actor, "EDUCATION_SCHEDULE_EXPORT", "semester=" + semesterCode + ", count=" + values.size());
				return output.toByteArray();
			} finally {
				workbook.dispose();
			}
		} catch (IOException exception) {
			throw new IllegalStateException("课表导出失败", exception);
		}
	}

	private void rejectHiddenSheets(Workbook workbook) {
		for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
			if (workbook.isSheetHidden(index) || workbook.isSheetVeryHidden(index)) {
				throw new IllegalArgumentException("课表文件不允许包含隐藏工作表");
			}
		}
	}

	private void assertHeader(Row row) {
		if (row == null) {
			throw new IllegalArgumentException("课表导入表头不能为空");
		}
		for (int index = 0; index < HEADERS.length; index++) {
			if (!HEADERS[index].equals(text(row, index))) {
				throw new IllegalArgumentException("第 " + (index + 1) + " 列表头应为“" + HEADERS[index] + "”");
			}
		}
	}

	private void rejectFormula(Row row) {
		for (Cell cell : row) {
			if (cell.getCellType() == CellType.FORMULA) {
				throw new IllegalArgumentException("不允许使用公式");
			}
		}
	}

	private boolean blank(Row row) {
		return row.getFirstCellNum() < 0 || text(row, 0).isBlank();
	}

	private String text(Row row, int index) {
		Cell cell = row.getCell(index);
		return cell == null ? "" : new DataFormatter().formatCellValue(cell).trim();
	}

	private Integer integer(Row row, int index, String label) {
		try {
			return Integer.valueOf(text(row, index));
		} catch (RuntimeException exception) {
			throw new IllegalArgumentException(label + "必须是整数");
		}
	}

	private Boolean booleanValue(String value) {
		return "是".equals(value) || "TRUE".equalsIgnoreCase(value) || "1".equals(value);
	}

	private <T> T required(T value, String message) {
		if (value == null) {
			throw new IllegalArgumentException(message);
		}
		return value;
	}

	private void writeHeader(Sheet sheet, Workbook workbook) {
		writeHeader(sheet, workbook, HEADERS);
	}

	private void writeHeader(Sheet sheet, Workbook workbook, String[] headers) {
		CellStyle style = workbook.createCellStyle();
		style.setFillForegroundColor(IndexedColors.TEAL.getIndex());
		style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		Font font = workbook.createFont();
		font.setBold(true);
		font.setColor(IndexedColors.WHITE.getIndex());
		style.setFont(font);
		Row row = sheet.createRow(0);
		for (int index = 0; index < headers.length; index++) {
			row.createCell(index).setCellValue(headers[index]);
			row.getCell(index).setCellStyle(style);
		}
	}

	private void setWidths(Sheet sheet) {
		int[] widths = { 22, 18, 10, 10, 14, 14, 12, 12, 14 };
		for (int index = 0; index < widths.length; index++) {
			sheet.setColumnWidth(index, widths[index] * 256);
		}
	}
}
