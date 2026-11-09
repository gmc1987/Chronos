package com.chronos.education.operationagent.service;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.chronos.file.model.ManagedFileView;
import com.chronos.file.service.ManagedFileService;

@Service
public class OperationAgentManagedFileService {
	private final ManagedFileService files;
	private final OperationAgentManagedFileAccessPolicy accessPolicy;

	public OperationAgentManagedFileService(
			ManagedFileService files,
			OperationAgentManagedFileAccessPolicy accessPolicy) {
		this.files = files;
		this.accessPolicy = accessPolicy;
	}

	/** Uploads a persistent business file and returns a server-side classification. */
	public BusinessFileUpload uploadBusinessFile(MultipartFile file, Authentication authentication) {
		validateXlsx(file);
		BusinessFileAnalysis analysis;
		try {
			analysis = analyze(file.getOriginalFilename(), file.getBytes());
		} catch (java.io.IOException exception) {
			throw new IllegalArgumentException("无法读取业务文件", exception);
		}
		ManagedFileView stored = files.upload(
				file,
				OperationAgentManagedFileAccessPolicy.BUSINESS_ATTACHMENT,
				accessPolicy.newBusinessId(authentication.getName()),
				authentication);
		return new BusinessFileUpload(stored, analysis);
	}

	public BusinessFileAnalysis analyze(String managedFileId, Authentication authentication) {
		var content = files.read(managedFileId, authentication);
		return analyze(content.filename(), content.content());
	}

	/** Uploads a legacy admission roster owned by the current plan author. */
	public ManagedFileView uploadAdmissionRoster(MultipartFile file, Authentication authentication) {
		validateXlsx(file);
		return files.upload(
				file,
				OperationAgentManagedFileAccessPolicy.ADMISSION_ROSTER,
				accessPolicy.newBusinessId(authentication.getName()),
				authentication);
	}

	private void validateXlsx(MultipartFile file) {
		String filename = file == null ? null : file.getOriginalFilename();
		if (file == null || file.isEmpty() || filename == null
				|| !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
			throw new IllegalArgumentException("业务附件必须是 xlsx 文件");
		}
		if (file.getSize() > 10 * 1024 * 1024) {
			throw new IllegalArgumentException("业务附件不能超过 10MB");
		}
	}

	private BusinessFileAnalysis analyze(String filename, byte[] content) {
		try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
			var sheet = workbook.getSheet("导入数据");
			if (sheet == null) sheet = workbook.getSheetAt(0);
			Row header = sheet.getRow(0);
			if (header == null) throw new IllegalArgumentException("业务文件表头为空");
			DataFormatter formatter = new DataFormatter();
			Set<String> headers = new LinkedHashSet<>();
			for (var cell : header) {
				String value = formatter.formatCellValue(cell).trim();
				if (!value.isBlank()) headers.add(value);
			}
			int dataRows = 0;
			for (int index = 1; index <= sheet.getLastRowNum(); index++) {
				Row row = sheet.getRow(index);
				if (row == null) continue;
				boolean any = false;
				for (int cellIndex = 0; cellIndex < headers.size(); cellIndex++) {
					if (!formatter.formatCellValue(row.getCell(cellIndex)).trim().isBlank()) {
						any = true;
						break;
					}
				}
				if (any) dataRows++;
			}
			Classification classification = classify(headers);
			List<String> warnings = new ArrayList<>();
			if (classification == Classification.UNKNOWN) {
				warnings.add("无法根据表头唯一识别业务类型，执行前需要用户选择目标业务或改用系统模板");
			}
			return new BusinessFileAnalysis(filename, classification.code, classification.name,
					classification.toolCode, dataRows, List.copyOf(headers), warnings,
					classification != Classification.UNKNOWN);
		} catch (IllegalArgumentException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new IllegalArgumentException("无法解析 XLSX 业务文件：" + exception.getMessage(), exception);
		}
	}

	private Classification classify(Set<String> headers) {
		if (contains(headers, "来源批次*", "考生号*", "姓名*", "入学年份*"))
			return Classification.ADMISSION_ROSTER;
		if (contains(headers, "导入标识*", "姓名*", "机构编码", "部门编码"))
			return Classification.EMPLOYEE_DIRECTORY;
		if (contains(headers, "机构编码*", "部门编码*", "部门名称*"))
			return Classification.DEPARTMENT_DIRECTORY;
		if (contains(headers, "岗位编码*", "岗位名称*"))
			return Classification.POSITION_DIRECTORY;
		if (contains(headers, "职级编码*", "职级名称*"))
			return Classification.JOB_LEVEL_DIRECTORY;
		return Classification.UNKNOWN;
	}

	private boolean contains(Set<String> headers, String... expected) {
		return java.util.Arrays.stream(expected).allMatch(headers::contains);
	}

	private enum Classification {
		ADMISSION_ROSTER("ADMISSION_ROSTER", "新生录取名册", "admission.import.managed-file"),
		EMPLOYEE_DIRECTORY("EMPLOYEE_DIRECTORY", "教职工与任职数据", "iam.employee.import.managed-file"),
		DEPARTMENT_DIRECTORY("DEPARTMENT_DIRECTORY", "组织部门与负责人数据", "iam.department.import.managed-file"),
		POSITION_DIRECTORY("POSITION_DIRECTORY", "岗位数据", "iam.position.import.managed-file"),
		JOB_LEVEL_DIRECTORY("JOB_LEVEL_DIRECTORY", "职级数据", "iam.job-level.import.managed-file"),
		UNKNOWN("UNKNOWN", "待确认业务文件", null);

		private final String code;
		private final String name;
		private final String toolCode;

		Classification(String code, String name, String toolCode) {
			this.code = code;
			this.name = name;
			this.toolCode = toolCode;
		}
	}

	public record BusinessFileUpload(ManagedFileView file, BusinessFileAnalysis analysis) {}
	public record BusinessFileAnalysis(
			String filename,
			String businessType,
			String businessTypeName,
			String suggestedToolCode,
			int dataRows,
			List<String> headers,
			List<String> warnings,
			boolean recognized) {}
}
