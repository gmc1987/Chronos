package com.chronos.education.operationagent.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
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
				.hasMessage("新生名册必须是 xlsx 文件");
	}
}
