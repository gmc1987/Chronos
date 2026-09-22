package com.chronos.education.grade.service;

import com.chronos.education.grade.dto.GradeDtos.GradeItemCommand;
import com.chronos.education.grade.dto.GradeDtos.GradebookDetailResponse;
import com.chronos.education.grade.dto.GradeDtos.ItemsCommand;
import com.chronos.education.grade.dto.GradeDtos.StudentSnapshot;
import com.chronos.service.iService.IAuditLogService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 成绩册 Excel 导入导出：先全量校验，再一次性写入，防止半成功数据。 */
@Service
public class GradeExcelService {
	private static final int MAX_ROWS = 5_000;
	private final GradeCenterService gradeCenter;
	private final IAuditLogService audit;

	public GradeExcelService(
			GradeCenterService gradeCenter,
			IAuditLogService audit) {
		this.gradeCenter = gradeCenter;
		this.audit = audit;
	}

	public byte[] export(String gradebookId, String actor) {
		GradebookDetailResponse detail = gradeCenter.getGradebook(gradebookId, actor);
		try (Workbook workbook = new XSSFWorkbook();
				ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			Sheet sheet = workbook.createSheet("成绩录入");
			Row header = sheet.createRow(0);
			header.createCell(0).setCellValue("学号");
			header.createCell(1).setCellValue("姓名");
			for (int index = 0; index < detail.components().size(); index++) {
				header.createCell(index + 2).setCellValue(detail.components().get(index).code());
			}
			Map<String, BigDecimal> scoreByKey = new HashMap<>();
			detail.items().forEach(item -> scoreByKey.put(
				item.studentId() + ":" + item.componentId(), item.rawScore()));
			for (int rowIndex = 0; rowIndex < detail.students().size(); rowIndex++) {
				StudentSnapshot student = detail.students().get(rowIndex);
				Row row = sheet.createRow(rowIndex + 1);
				row.createCell(0).setCellValue(student.studentNo());
				row.createCell(1).setCellValue(student.studentName());
				for (int componentIndex = 0; componentIndex < detail.components().size(); componentIndex++) {
					var component = detail.components().get(componentIndex);
					BigDecimal score = scoreByKey.get(student.studentId() + ":" + component.id());
					if (score != null) {
						row.createCell(componentIndex + 2).setCellValue(score.doubleValue());
					}
				}
			}
			for (int index = 0; index < detail.components().size() + 2; index++) {
				sheet.autoSizeColumn(index);
			}
			workbook.write(output);
			// 只记录导出对象和数量，审计日志不保存任何学生成绩正文。
			audit.log(
				actor,
				"EDU_GRADEBOOK_EXPORT",
				"gradebookId=" + gradebookId + ",studentCount=" + detail.students().size());
			return output.toByteArray();
		} catch (Exception exception) {
			throw new IllegalStateException("成绩册导出失败", exception);
		}
	}

	@Transactional
	public Map<String, Object> importFile(
			String gradebookId,
			MultipartFile file,
			boolean dryRun,
			String actor) {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("请选择 XLSX 文件");
		}
		if (file.getSize() > 10L * 1024 * 1024) {
			throw new IllegalArgumentException("成绩文件不能超过 10MB");
		}
		GradebookDetailResponse detail = gradeCenter.getGradebook(gradebookId, actor);
		try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file.getBytes()))) {
			Sheet sheet = workbook.getSheet("成绩录入");
			if (sheet == null) {
				throw new IllegalArgumentException("缺少“成绩录入”工作表");
			}
			if (sheet.getLastRowNum() > MAX_ROWS) {
				throw new IllegalArgumentException("单次最多导入 5000 名学生");
			}
			assertHeader(sheet.getRow(0), detail);
			Map<String, StudentSnapshot> studentByNo = new HashMap<>();
			detail.students().forEach(student -> studentByNo.put(student.studentNo(), student));
			List<GradeItemCommand> commands = new ArrayList<>();
			List<String> errors = new ArrayList<>();
			DataFormatter formatter = new DataFormatter();
			for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
				Row row = sheet.getRow(rowIndex);
				if (row == null || formatter.formatCellValue(row.getCell(0)).isBlank()) {
					continue;
				}
				try {
					rejectFormula(row);
					String studentNo = formatter.formatCellValue(row.getCell(0)).trim();
					StudentSnapshot student = studentByNo.get(studentNo);
					if (student == null) {
						throw new IllegalArgumentException("学号不在成绩册快照中");
					}
					for (int index = 0; index < detail.components().size(); index++) {
						Cell cell = row.getCell(index + 2);
						if (cell == null || formatter.formatCellValue(cell).isBlank()) {
							continue;
						}
						BigDecimal score = new BigDecimal(formatter.formatCellValue(cell).trim());
						commands.add(new GradeItemCommand(
							detail.components().get(index).id(), student.studentId(), score, null, "Excel导入"));
					}
				} catch (RuntimeException exception) {
					errors.add("第 " + (rowIndex + 1) + " 行：" + exception.getMessage());
				}
			}
			if (!errors.isEmpty()) {
				return Map.of("valid", false, "imported", 0, "errors", errors);
			}
			if (!dryRun) {
				gradeCenter.saveItems(gradebookId, new ItemsCommand(commands, detail.rowVersion()), actor);
			}
			return Map.of("valid", true, "imported", commands.size(), "dryRun", dryRun, "errors", List.of());
		} catch (IllegalArgumentException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new IllegalArgumentException("成绩文件解析失败，请使用系统导出的模板", exception);
		}
	}

	private void assertHeader(Row header, GradebookDetailResponse detail) {
		DataFormatter formatter = new DataFormatter();
		if (header == null
				|| !"学号".equals(formatter.formatCellValue(header.getCell(0)).trim())
				|| !"姓名".equals(formatter.formatCellValue(header.getCell(1)).trim())) {
			throw new IllegalArgumentException("模板表头不正确");
		}
		for (int index = 0; index < detail.components().size(); index++) {
			String actual = formatter.formatCellValue(header.getCell(index + 2)).trim();
			if (!detail.components().get(index).code().equals(actual)) {
				throw new IllegalArgumentException("成绩项目列与当前考核方案不一致");
			}
		}
	}

	private void rejectFormula(Row row) {
		for (Cell cell : row) {
			if (cell.getCellType() == CellType.FORMULA) {
				throw new IllegalArgumentException("不允许使用公式单元格");
			}
		}
	}
}
