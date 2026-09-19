package com.chronos.education.homeschool.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chronos.education.grade.model.CourseGrade;
import com.chronos.education.homeschool.service.HomeSchoolService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.junit.jupiter.api.Test;

class HomeSchoolControllerHttpTest {
	private final HomeSchoolService service = mock(HomeSchoolService.class);
	private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new HomeSchoolController(service))
			.defaultRequest(get("/").principal(
					new UsernamePasswordAuthenticationToken("parent@example.test", "n/a")))
			.build();

	@Test
	void familyGradesUsesAuthenticatedAccountAndReturnsPublishedGradeShape() throws Exception {
		CourseGrade grade = new CourseGrade();
		grade.setId("grade-1");
		grade.setStudentId("student-1");
		grade.setTotalScore(new BigDecimal("88.00"));
		grade.setPassed(true);
		grade.setVersionNo(2);
		when(service.familyGrades("parent@example.test")).thenReturn(List.of(grade));

		mockMvc.perform(get("/portal/education/family/grades"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].id").value("grade-1"))
				.andExpect(jsonPath("$.data[0].studentId").value("student-1"))
				.andExpect(jsonPath("$.data[0].totalScore").value(88.0));

		verify(service).familyGrades("parent@example.test");
	}
}
