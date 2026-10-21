package com.chronos.education.scheduling.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.chronos.education.scheduling.dao.AdmissionCandidateRepository;
import com.chronos.education.scheduling.dao.AdministrativeClassRepository;
import com.chronos.education.scheduling.dao.MajorRepository;
import com.chronos.education.scheduling.model.AdmissionCandidate;
import com.chronos.education.scheduling.model.dto.AdmissionDtos.ImportResult;

/** 录取名册 Excel 导入；先完整校验再整批写入，任何错误都不会留下半批数据。 */
@Service
public class AdmissionImportService {
	private static final int MAX_ROWS = 5000;
	private static final String[] HEADERS = {"来源批次*", "考生号*", "录取号", "姓名*", "性别", "入学年份*", "培养层次*", "专业代码", "行政班代码", "联系电话"};
	private final AdmissionService admissions;
	private final AdmissionCandidateRepository candidates;
	private final MajorRepository majors;
	private final AdministrativeClassRepository classes;

	public AdmissionImportService(AdmissionService admissions, AdmissionCandidateRepository candidates,
			MajorRepository majors, AdministrativeClassRepository classes) {
		this.admissions = admissions; this.candidates = candidates; this.majors = majors; this.classes = classes;
	}

	public byte[] template() {
		try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
			var sheet = workbook.createSheet("正式录取名册");
			Row header = sheet.createRow(0);
			for (int index = 0; index < HEADERS.length; index++) header.createCell(index).setCellValue(HEADERS[index]);
			Row sample = sheet.createRow(1);
			String[] values = {"2026-GK-01", "264401010001", "LQ2026000001", "示例学生", "MALE", "2026", "HIGHER_EDUCATION", "专业代码", "行政班代码", "13800000000"};
			for (int index = 0; index < values.length; index++) { sample.createCell(index, CellType.STRING).setCellValue(values[index]); sheet.setColumnWidth(index, index == 3 ? 16 * 256 : 22 * 256); }
			// 填写说明放在独立工作表，避免导入器将说明文字误识别为名册数据。
			var instructions = workbook.createSheet("填写说明");
			instructions.createRow(0).createCell(0).setCellValue("培养层次：UNDERGRADUATE 本科、VOCATIONAL_UNDERGRADUATE 职业本科、HIGHER_EDUCATION 高职/专科。");
			instructions.createRow(1).createCell(0).setCellValue("考生号请按文本格式填写，示例行导入前请删除或替换。");
			instructions.setColumnWidth(0, 96 * 256);
			workbook.write(output); return output.toByteArray();
		} catch (IOException exception) { throw new IllegalStateException("生成录取名册模板失败", exception); }
	}

	@Transactional
	public ImportResult importFile(MultipartFile file, boolean dryRun) {
		if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择录取名册文件");
		List<AdmissionCandidate> values = new ArrayList<>(); List<String> errors = new ArrayList<>(); Set<String> fileKeys = new HashSet<>();
		try (var workbook = new XSSFWorkbook(file.getInputStream())) {
			var sheet = workbook.getSheetAt(0); DataFormatter formatter = new DataFormatter();
			if (sheet.getLastRowNum() > MAX_ROWS) throw new IllegalArgumentException("单次最多导入" + MAX_ROWS + "名新生");
			for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
				Row row = sheet.getRow(rowIndex); if (row == null || empty(row, formatter)) continue;
				try {
					AdmissionCandidate value = parse(row, formatter);
					String key = value.getAdmissionYear() + "|" + value.getCandidateNo();
					if (!fileKeys.add(key)) throw new IllegalArgumentException("文件中考生号重复");
					if (candidates.findByAdmissionYearAndCandidateNo(value.getAdmissionYear(), value.getCandidateNo()).isPresent())
						throw new IllegalArgumentException("系统中已存在同一入学年份和考生号");
					values.add(value);
				} catch (RuntimeException exception) { errors.add("第" + (rowIndex + 1) + "行：" + exception.getMessage()); }
			}
		} catch (IOException exception) { throw new IllegalArgumentException("无法读取 Excel 文件", exception); }
		if (!errors.isEmpty()) return new ImportResult(values.size() + errors.size(), 0, errors);
		if (!dryRun) values.forEach(admissions::save);
		return new ImportResult(values.size(), values.size(), List.of());
	}

	private AdmissionCandidate parse(Row row, DataFormatter formatter) {
		AdmissionCandidate value = new AdmissionCandidate();
		value.setSourceBatchNo(required(text(row, 0, formatter), "来源批次")); value.setSourceType("PROVINCIAL_IMPORT");
		value.setCandidateNo(required(text(row, 1, formatter), "考生号")); value.setAdmissionNo(text(row, 2, formatter));
		value.setStudentName(required(text(row, 3, formatter), "姓名")); value.setGender(text(row, 4, formatter));
		try { value.setAdmissionYear(Integer.valueOf(required(text(row, 5, formatter), "入学年份"))); }
		catch (NumberFormatException exception) { throw new IllegalArgumentException("入学年份必须是4位数字"); }
		value.setEducationLevel(required(text(row, 6, formatter), "培养层次"));
		String majorCode = text(row, 7, formatter); if (!majorCode.isBlank()) value.setAdmittedMajorId(majors.findByMajorCode(majorCode)
				.orElseThrow(() -> new IllegalArgumentException("专业代码不存在：" + majorCode)).getId());
		String classCode = text(row, 8, formatter); if (!classCode.isBlank()) value.setPlannedClassId(classes.findByClassCode(classCode)
				.orElseThrow(() -> new IllegalArgumentException("行政班代码不存在：" + classCode)).getId());
		value.setPhone(text(row, 9, formatter)); return value;
	}
	private boolean empty(Row row, DataFormatter formatter) { for (int i = 0; i < HEADERS.length; i++) if (!text(row, i, formatter).isBlank()) return false; return true; }
	private String text(Row row, int index, DataFormatter formatter) { return formatter.formatCellValue(row.getCell(index)).trim(); }
	private String required(String value, String label) { if (value.isBlank()) throw new IllegalArgumentException(label + "不能为空"); return value; }
}
