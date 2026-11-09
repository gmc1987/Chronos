package com.chronos.education.operationagent.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import com.chronos.file.service.ManagedFileService;

class OperationAgentManagedFileServiceTest {
	@Test
	void uploadsXlsxWithServerGeneratedOwnerBoundBusinessId() {
		ManagedFileService files = mock(ManagedFileService.class);
		OperationAgentManagedFileAccessPolicy policy = new OperationAgentManagedFileAccessPolicy();
		OperationAgentManagedFileService service = new OperationAgentManagedFileService(files, policy);
		Authentication authentication = mock(Authentication.class);
		when(authentication.getName()).thenReturn("admin");
		var upload = new MockMultipartFile("file", "admissions.xlsx",
				"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[] { 1 });

		service.uploadAdmissionRoster(upload, authentication);

		ArgumentCaptor<String> businessId = ArgumentCaptor.forClass(String.class);
		verify(files).upload(eq(upload),
				eq(OperationAgentManagedFileAccessPolicy.ADMISSION_ROSTER),
				businessId.capture(), eq(authentication));
		if (!policy.canWrite("admin", OperationAgentManagedFileAccessPolicy.ADMISSION_ROSTER,
				businessId.getValue())) {
			throw new AssertionError("Generated business id must be writable by its owner");
		}
	}

	@Test
	void rejectsNonXlsxBeforeCallingManagedFileStorage() {
		OperationAgentManagedFileService service = new OperationAgentManagedFileService(
				mock(ManagedFileService.class), new OperationAgentManagedFileAccessPolicy());
		Authentication authentication = mock(Authentication.class);
		var upload = new MockMultipartFile("file", "admissions.csv", "text/csv", new byte[] { 1 });

		assertThatThrownBy(() -> service.uploadAdmissionRoster(upload, authentication))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("业务附件必须是 xlsx 文件");
	}

	@Test
	void classifiesEmployeeDirectoryBeforeStoringGenericBusinessFile() throws Exception {
		ManagedFileService files = mock(ManagedFileService.class);
		OperationAgentManagedFileService service = new OperationAgentManagedFileService(
				files, new OperationAgentManagedFileAccessPolicy());
		Authentication authentication = mock(Authentication.class);
		when(authentication.getName()).thenReturn("admin");
		byte[] content;
		try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
			var sheet = workbook.createSheet("导入数据");
			var header = sheet.createRow(0);
			List.of("导入标识*", "姓名*", "机构编码", "部门编码").forEach(value ->
					header.createCell(header.getLastCellNum() < 0 ? 0 : header.getLastCellNum()).setCellValue(value));
			var row = sheet.createRow(1);
			row.createCell(0).setCellValue("EMP-1");
			row.createCell(1).setCellValue("张老师");
			workbook.write(output);
			content = output.toByteArray();
		}
		var upload = new MockMultipartFile("file", "employees.XLSX",
				"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);

		var result = service.uploadBusinessFile(upload, authentication);

		org.assertj.core.api.Assertions.assertThat(result.analysis().businessType())
				.isEqualTo("EMPLOYEE_DIRECTORY");
		org.assertj.core.api.Assertions.assertThat(result.analysis().suggestedToolCode())
				.isEqualTo("iam.employee.import.managed-file");
		verify(files).upload(eq(upload),
				eq(OperationAgentManagedFileAccessPolicy.BUSINESS_ATTACHMENT),
				org.mockito.ArgumentMatchers.anyString(), eq(authentication));
	}
}
