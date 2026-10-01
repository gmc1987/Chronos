package com.chronos.education.grade.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.education.grade.dto.GradeAnalysisDtos.AnalysisFilters;
import com.chronos.education.grade.dto.GradeAnalysisDtos.AnalysisResponse;
import com.chronos.education.grade.dto.GradeAnalysisDtos.KnowledgeAnalysisResponse;
import com.chronos.education.grade.dto.GradeAnalysisDtos.TrendPoint;
import com.chronos.education.grade.service.GradeAnalysisService;
import com.chronos.education.grade.service.GradeAnalysisExportService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 成绩中心独立分析入口；权限按菜单维度隔离。 */
@RestController
@RequestMapping("/admin/education/grade-analysis")
public class GradeAnalysisController {
	private final GradeAnalysisService service;
	private final GradeAnalysisExportService exportService;

	public GradeAnalysisController(
			GradeAnalysisService service,
			GradeAnalysisExportService exportService) {
		this.service = service;
		this.exportService = exportService;
	}

	@GetMapping("/filters")
	@PreAuthorize("@iamAuthorization.any(authentication, 'education:score:class-analysis:view', "
			+ "'education:score:grade-analysis:view', 'education:score:subject-analysis:view', "
			+ "'education:score:trend-analysis:view', 'education:score:knowledge-analysis:view')")
	public ResultData<AnalysisFilters> filters(Authentication authentication) {
		return ok(service.filters(authentication.getName()));
	}

	@GetMapping("/class")
	@PreAuthorize("hasAuthority('education:score:class-analysis:view')")
	public ResultData<AnalysisResponse> classAnalysis(
			@RequestParam(required = false) String semesterCode,
			@RequestParam(required = false) String classId,
			@RequestParam(required = false) String courseCode,
			Authentication authentication) {
		return ok(service.analyze("CLASS", semesterCode, classId, null, courseCode, authentication.getName()));
	}

	@GetMapping("/grade")
	@PreAuthorize("hasAuthority('education:score:grade-analysis:view')")
	public ResultData<AnalysisResponse> gradeAnalysis(
			@RequestParam(required = false) String semesterCode,
			@RequestParam(required = false) String gradeId,
			@RequestParam(required = false) String courseCode,
			Authentication authentication) {
		return ok(service.analyze("GRADE", semesterCode, null, gradeId, courseCode, authentication.getName()));
	}

	@GetMapping("/subject")
	@PreAuthorize("hasAuthority('education:score:subject-analysis:view')")
	public ResultData<AnalysisResponse> subjectAnalysis(
			@RequestParam(required = false) String semesterCode,
			@RequestParam(required = false) String classId,
			@RequestParam(required = false) String gradeId,
			@RequestParam(required = false) String courseCode,
			Authentication authentication) {
		return ok(service.analyze("SUBJECT", semesterCode, classId, gradeId, courseCode, authentication.getName()));
	}

	@GetMapping("/trend")
	@PreAuthorize("hasAuthority('education:score:trend-analysis:view')")
	public ResultData<List<TrendPoint>> trend(
			@RequestParam(required = false) String classId,
			@RequestParam(required = false) String gradeId,
			@RequestParam(required = false) String courseCode,
			Authentication authentication) {
		return ok(service.trend(classId, gradeId, courseCode, authentication.getName()));
	}

	@GetMapping("/knowledge")
	@PreAuthorize("hasAuthority('education:score:knowledge-analysis:view')")
	public ResultData<KnowledgeAnalysisResponse> knowledge(
			@RequestParam(required = false) String semesterCode,
			@RequestParam(required = false) String classId,
			@RequestParam(required = false) String gradeId,
			@RequestParam(required = false) String courseCode,
			Authentication authentication) {
		return ok(service.knowledge(
				semesterCode,
				classId,
				gradeId,
				courseCode,
				authentication.getName()));
	}

	@GetMapping("/class/export")
	@PreAuthorize("hasAuthority('education:score:class-analysis:export')")
	public ResponseEntity<byte[]> exportClass(
			@RequestParam(required = false) String semesterCode,
			@RequestParam(required = false) String classId,
			@RequestParam(required = false) String courseCode,
			Authentication authentication) {
		return workbook(
			"班级成绩分析.xlsx",
			exportService.exportAnalysis(
				"CLASS", semesterCode, classId, null, courseCode, authentication.getName()));
	}

	@GetMapping("/grade/export")
	@PreAuthorize("hasAuthority('education:score:grade-analysis:export')")
	public ResponseEntity<byte[]> exportGrade(
			@RequestParam(required = false) String semesterCode,
			@RequestParam(required = false) String gradeId,
			@RequestParam(required = false) String courseCode,
			Authentication authentication) {
		return workbook(
			"年级成绩分析.xlsx",
			exportService.exportAnalysis(
				"GRADE", semesterCode, null, gradeId, courseCode, authentication.getName()));
	}

	@GetMapping("/subject/export")
	@PreAuthorize("hasAuthority('education:score:subject-analysis:export')")
	public ResponseEntity<byte[]> exportSubject(
			@RequestParam(required = false) String semesterCode,
			@RequestParam(required = false) String classId,
			@RequestParam(required = false) String gradeId,
			@RequestParam(required = false) String courseCode,
			Authentication authentication) {
		return workbook(
			"学科成绩分析.xlsx",
			exportService.exportAnalysis(
				"SUBJECT", semesterCode, classId, gradeId, courseCode, authentication.getName()));
	}

	@GetMapping("/trend/export")
	@PreAuthorize("hasAuthority('education:score:trend-analysis:export')")
	public ResponseEntity<byte[]> exportTrend(
			@RequestParam(required = false) String classId,
			@RequestParam(required = false) String gradeId,
			@RequestParam(required = false) String courseCode,
			Authentication authentication) {
		return workbook(
			"成绩趋势分析.xlsx",
			exportService.exportTrend(classId, gradeId, courseCode, authentication.getName()));
	}

	@GetMapping("/knowledge/export")
	@PreAuthorize("hasAuthority('education:score:knowledge-analysis:export')")
	public ResponseEntity<byte[]> exportKnowledge(
			@RequestParam(required = false) String semesterCode,
			@RequestParam(required = false) String classId,
			@RequestParam(required = false) String gradeId,
			@RequestParam(required = false) String courseCode,
			Authentication authentication) {
		return workbook(
			"知识点成绩分析.xlsx",
			exportService.exportKnowledge(
				semesterCode, classId, gradeId, courseCode, authentication.getName()));
	}

	private ResponseEntity<byte[]> workbook(String filename, byte[] content) {
		String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
			.contentType(MediaType.parseMediaType(
				"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
			.body(content);
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("ok").data(data).build();
	}
}
