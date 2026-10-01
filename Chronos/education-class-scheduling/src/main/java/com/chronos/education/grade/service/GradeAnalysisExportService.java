package com.chronos.education.grade.service;

import com.chronos.education.grade.dto.GradeAnalysisDtos.AnalysisResponse;
import com.chronos.education.grade.dto.GradeAnalysisDtos.GroupMetric;
import com.chronos.education.grade.dto.GradeAnalysisDtos.KnowledgeAnalysisResponse;
import com.chronos.education.grade.dto.GradeAnalysisDtos.TrendPoint;
import com.chronos.service.iService.IAuditLogService;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * 成绩分析导出只复用 {@link GradeAnalysisService} 的脱敏聚合结果。
 * 不在导出层重新查询成绩明细，以免绕过教育数据范围或产生统计口径差异。
 */
@Service
public class GradeAnalysisExportService {
	private final GradeAnalysisService analysis;
	private final IAuditLogService audit;

	public GradeAnalysisExportService(
			GradeAnalysisService analysis,
			IAuditLogService audit) {
		this.analysis = analysis;
		this.audit = audit;
	}

	public byte[] exportAnalysis(
			String dimension,
			String semesterCode,
			String classId,
			String gradeId,
			String courseCode,
			String actor) {
		AnalysisResponse response = analysis.analyze(
			dimension,
			semesterCode,
			classId,
			gradeId,
			courseCode,
			actor);
		byte[] content = writeMetrics(response.groups());
		audit.log(
			actor,
			"EDU_GRADE_ANALYSIS_EXPORT",
			"dimension=" + dimension + ",groupCount=" + response.groups().size());
		return content;
	}

	public byte[] exportTrend(
			String classId,
			String gradeId,
			String courseCode,
			String actor) {
		List<TrendPoint> rows = analysis.trend(classId, gradeId, courseCode, actor);
		byte[] content = writeWorkbook(workbook -> {
			Sheet sheet = workbook.createSheet("趋势分析");
			writeHeader(sheet, "学期", "人数", "平均分", "及格率(%)");
			for (int index = 0; index < rows.size(); index++) {
				TrendPoint value = rows.get(index);
				Row row = sheet.createRow(index + 1);
				row.createCell(0).setCellValue(value.semesterCode());
				row.createCell(1).setCellValue(value.studentCount());
				setNumber(row, 2, value.averageScore());
				setNumber(row, 3, value.passRate());
			}
			autoSize(sheet, 4);
		});
		audit.log(actor, "EDU_GRADE_ANALYSIS_EXPORT", "dimension=TREND,groupCount=" + rows.size());
		return content;
	}

	public byte[] exportKnowledge(
			String semesterCode,
			String classId,
			String gradeId,
			String courseCode,
			String actor) {
		KnowledgeAnalysisResponse response = analysis.knowledge(
			semesterCode,
			classId,
			gradeId,
			courseCode,
			actor);
		byte[] content = writeMetrics(response.groups());
		audit.log(
			actor,
			"EDU_GRADE_ANALYSIS_EXPORT",
			"dimension=KNOWLEDGE,groupCount=" + response.groups().size());
		return content;
	}

	private byte[] writeMetrics(List<GroupMetric> values) {
		return writeWorkbook(workbook -> {
			Sheet sheet = workbook.createSheet("分析结果");
			writeHeader(sheet, "编码", "名称", "上级", "人数", "平均分/掌握度", "及格率/达标率(%)", "优秀率(%)");
			for (int index = 0; index < values.size(); index++) {
				GroupMetric value = values.get(index);
				Row row = sheet.createRow(index + 1);
				row.createCell(0).setCellValue(value.key());
				row.createCell(1).setCellValue(value.name());
				row.createCell(2).setCellValue(value.parentName() == null ? "" : value.parentName());
				row.createCell(3).setCellValue(value.studentCount());
				setNumber(row, 4, value.averageScore());
				setNumber(row, 5, value.passRate());
				setNumber(row, 6, value.excellentRate());
			}
			autoSize(sheet, 7);
		});
	}

	private byte[] writeWorkbook(WorkbookWriter writer) {
		try (Workbook workbook = new XSSFWorkbook();
				ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			writer.write(workbook);
			workbook.write(output);
			return output.toByteArray();
		} catch (Exception exception) {
			throw new IllegalStateException("成绩分析导出失败", exception);
		}
	}

	private void writeHeader(Sheet sheet, String... values) {
		Row row = sheet.createRow(0);
		for (int index = 0; index < values.length; index++) {
			row.createCell(index).setCellValue(values[index]);
		}
	}

	private void setNumber(Row row, int column, Number value) {
		if (value != null) {
			row.createCell(column).setCellValue(value.doubleValue());
		}
	}

	private void autoSize(Sheet sheet, int columns) {
		for (int index = 0; index < columns; index++) {
			sheet.autoSizeColumn(index);
		}
	}

	@FunctionalInterface
	private interface WorkbookWriter {
		void write(Workbook workbook);
	}
}
