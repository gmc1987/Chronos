package com.chronos.education.grade.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.chronos.education.grade.dto.GradeAnalysisDtos.AnalysisResponse;
import com.chronos.education.grade.dto.GradeAnalysisDtos.GroupMetric;
import com.chronos.education.grade.dto.GradeAnalysisDtos.Summary;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import com.chronos.service.iService.IAuditLogService;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GradeAnalysisExportServiceTest {
	@Mock
	private GradeAnalysisService analysis;
	@Mock
	private IAuditLogService audit;

	@InjectMocks
	private GradeAnalysisExportService service;

	@Test
	void exportsTheSameScopedAggregateReturnedByAnalysisService() throws Exception {
		GroupMetric metric = new GroupMetric(
			"class-1",
			"软件一班",
			null,
			30,
			new BigDecimal("82.50"),
			new BigDecimal("93.33"),
			new BigDecimal("26.67"));
		AnalysisResponse response = new AnalysisResponse(
			"CLASS",
			new Summary(30, new BigDecimal("82.50"), BigDecimal.ZERO, BigDecimal.ZERO,
				new BigDecimal("93.33"), new BigDecimal("26.67")),
			List.of(metric),
			List.of());
		when(analysis.analyze("CLASS", "2026-1", "class-1", null, "JAVA", "teacher"))
			.thenReturn(response);

		byte[] content = service.exportAnalysis(
			"CLASS", "2026-1", "class-1", null, "JAVA", "teacher");

		try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
			var sheet = workbook.getSheet("分析结果");
			assertThat(sheet).isNotNull();
			assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("软件一班");
			assertThat(sheet.getRow(1).getCell(4).getNumericCellValue()).isEqualTo(82.5D);
		}
		verify(audit).log(
			"teacher",
			"EDU_GRADE_ANALYSIS_EXPORT",
			"dimension=CLASS,groupCount=1");
	}
}
