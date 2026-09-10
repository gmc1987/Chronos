package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.AdministrativeClassRepository;
import com.chronos.education.scheduling.dao.EducationGradeRepository;
import com.chronos.education.scheduling.dao.MajorRepository;
import com.chronos.education.scheduling.model.AdministrativeClass;
import com.chronos.education.scheduling.model.EducationGrade;
import com.chronos.education.scheduling.model.EducationDataScope;
import com.chronos.education.scheduling.model.Major;
import com.chronos.education.scheduling.model.StudentProfile;
import com.chronos.education.scheduling.model.StudentProfileView;
import com.chronos.service.iService.IAuditLogService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 使用分页读取和流式工作簿导出学生档案，避免大批量导出耗尽堆内存。 */
@Service
public class StudentProfileExportService {
	private static final int BATCH_SIZE = 1_000;
	private static final int MAX_ROWS = 100_000;
	private final EducationGradeRepository grades;
	private final MajorRepository majors;
	private final AdministrativeClassRepository classes;
	private final IAuditLogService audit;
	private final AcademicDataService academicData;

	public StudentProfileExportService(
			EducationGradeRepository grades,
			MajorRepository majors,
			AdministrativeClassRepository classes,
			IAuditLogService audit,
			AcademicDataService academicData) {
		this.grades = grades;
		this.majors = majors;
		this.classes = classes;
		this.audit = audit;
		this.academicData = academicData;
	}

	@Transactional
	public byte[] export(
			String actor,
			boolean privacyVisible,
			EducationDataScope scope) {
		Page<StudentProfile> firstPage = academicData.students(scope, 0, BATCH_SIZE);
		long total = firstPage.getTotalElements();
		if (total > MAX_ROWS) {
			throw new IllegalStateException("学生档案超过 100000 条，请缩小范围后导出");
		}
		Map<String, String> gradeNames = grades.findAll().stream()
				.collect(Collectors.toMap(EducationGrade::getId, EducationGrade::getGradeName));
		Map<String, String> majorNames = majors.findAll().stream()
				.collect(Collectors.toMap(Major::getId, Major::getMajorName));
		Map<String, String> classNames = classes.findAll().stream()
				.collect(Collectors.toMap(AdministrativeClass::getId, AdministrativeClass::getClassName));

		try (SXSSFWorkbook workbook = new SXSSFWorkbook(100);
				ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			try {
				workbook.setCompressTempFiles(true);
				Sheet sheet = workbook.createSheet("学生档案");
				writeHeader(sheet, workbook);
				int rowNumber = 1;
				int pageNumber = 0;
				Page<StudentProfile> page = firstPage;
				do {
					if (pageNumber > 0) {
						page = academicData.students(scope, pageNumber, BATCH_SIZE);
					}
					for (StudentProfile student : page.getContent()) {
						writeStudent(
								sheet.createRow(rowNumber++),
								student,
								privacyVisible,
								gradeNames,
								majorNames,
								classNames);
					}
					pageNumber++;
				} while (page.hasNext());
				setColumnWidths(sheet);
				workbook.write(output);
				audit.log(
						actor,
						"EDUCATION_STUDENT_EXPORT",
						"count=" + total + ", privacyVisible=" + privacyVisible);
				return output.toByteArray();
			} finally {
				// SXSSF 会使用磁盘临时文件；无论导出成功与否都必须主动释放。
				workbook.dispose();
			}
		} catch (IOException exception) {
			throw new IllegalStateException("学生档案导出失败", exception);
		}
	}

	private void writeHeader(Sheet sheet, SXSSFWorkbook workbook) {
		String[] headers = {
				"学号", "姓名", "性别", "入学年份", "年级",
				"专业", "行政班", "学籍状态", "联系电话"
		};
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

	private void writeStudent(
			Row row,
			StudentProfile student,
			boolean privacyVisible,
			Map<String, String> gradeNames,
			Map<String, String> majorNames,
			Map<String, String> classNames) {
		StudentProfileView view = StudentProfileView.from(student, privacyVisible);
		String[] values = {
				value(view.studentNo()),
				value(view.studentName()),
				gender(view.gender()),
				view.gradeYear() == null ? "" : view.gradeYear().toString(),
				gradeNames.getOrDefault(view.gradeId(), ""),
				majorNames.getOrDefault(view.majorId(), ""),
				classNames.getOrDefault(view.administrativeClassId(), ""),
				status(view.enrollmentStatus()),
				value(view.phone())
		};
		for (int index = 0; index < values.length; index++) {
			row.createCell(index).setCellValue(values[index]);
		}
	}

	private void setColumnWidths(Sheet sheet) {
		int[] widths = { 18, 16, 10, 12, 18, 22, 22, 14, 20 };
		for (int index = 0; index < widths.length; index++) {
			sheet.setColumnWidth(index, widths[index] * 256);
		}
	}

	private String gender(String value) {
		return switch (value(value)) {
			case "MALE" -> "男";
			case "FEMALE" -> "女";
			default -> value(value);
		};
	}

	private String status(String value) {
		return switch (value(value)) {
			case "ACTIVE" -> "在读";
			case "SUSPENDED" -> "休学";
			case "GRADUATED" -> "毕业";
			case "TRANSFERRED" -> "转学";
			default -> value(value);
		};
	}

	private String value(String value) {
		return value == null ? "" : value;
	}
}
