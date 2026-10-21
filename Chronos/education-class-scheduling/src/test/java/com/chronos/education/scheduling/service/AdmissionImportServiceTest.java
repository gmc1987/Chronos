package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Optional;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import com.chronos.education.scheduling.dao.AdmissionCandidateRepository;
import com.chronos.education.scheduling.dao.AdministrativeClassRepository;
import com.chronos.education.scheduling.dao.MajorRepository;
import com.chronos.education.scheduling.model.AdministrativeClass;
import com.chronos.education.scheduling.model.Major;

@ExtendWith(MockitoExtension.class)
class AdmissionImportServiceTest {
	@Mock private AdmissionService admissions;
	@Mock private AdmissionCandidateRepository candidates;
	@Mock private MajorRepository majors;
	@Mock private AdministrativeClassRepository classes;
	@InjectMocks private AdmissionImportService service;

	@Test
	void generatedTemplateCanBeFilledAndDryRunWithoutTreatingInstructionsAsData() throws Exception {
		byte[] template = service.template();
		byte[] completed;
		try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(template));
				var output = new ByteArrayOutputStream()) {
			assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
			assertThat(workbook.getSheet("填写说明")).isNotNull();
			var row = workbook.getSheet("正式录取名册").getRow(1);
			String[] values = {"2026-GK-TEST", "264401019999", "LQ2026999999", "模板验收学生", "FEMALE",
					"2026", "HIGHER_EDUCATION", "SCALE-M01", "SCALE-HV-2026-M01-C1", "13800009999"};
			for (int index = 0; index < values.length; index++) row.getCell(index).setCellValue(values[index]);
			workbook.write(output);
			completed = output.toByteArray();
		}

		Major major = new Major(); major.setId("major-1");
		AdministrativeClass administrativeClass = new AdministrativeClass(); administrativeClass.setId("class-1");
		when(majors.findByMajorCode("SCALE-M01")).thenReturn(Optional.of(major));
		when(classes.findByClassCode("SCALE-HV-2026-M01-C1")).thenReturn(Optional.of(administrativeClass));
		when(candidates.findByAdmissionYearAndCandidateNo(2026, "264401019999")).thenReturn(Optional.empty());

		var file = new MockMultipartFile("file", "admissions.xlsx",
				"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", completed);
		var result = service.importFile(file, true);

		assertThat(result.totalCount()).isEqualTo(1);
		assertThat(result.successCount()).isEqualTo(1);
		assertThat(result.errors()).isEmpty();
	}
}
