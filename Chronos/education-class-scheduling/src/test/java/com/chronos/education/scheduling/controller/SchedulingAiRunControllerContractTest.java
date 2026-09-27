package com.chronos.education.scheduling.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.chronos.education.scheduling.service.EducationDataScopeService;
import com.chronos.education.scheduling.service.SchedulingAiRunService;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

class SchedulingAiRunControllerContractTest {
	@Test
	void exposesOnlyGovernedRunEndpointsAndJointPermissions() {
		SchedulingAiRunController controller = new SchedulingAiRunController(
				mock(SchedulingAiRunService.class),
				mock(EducationDataScopeService.class));
		var methods = Arrays.stream(SchedulingAiRunController.class.getDeclaredMethods())
				.filter(method -> method.isAnnotationPresent(GetMapping.class)
						|| method.isAnnotationPresent(PostMapping.class))
				.toList();
		var paths = methods.stream()
				.flatMap(method -> {
					GetMapping get = method.getAnnotation(GetMapping.class);
					PostMapping post = method.getAnnotation(PostMapping.class);
					return Arrays.stream(get == null ? post.value() : get.value());
				})
				.collect(Collectors.toSet());
		assertThat(paths).allMatch(path -> path.startsWith("/admin/education/scheduling/ai/runs"));
		assertThat(paths).noneMatch(path -> path.contains("/apply")
				|| path.contains("/publish")
				|| path.contains("/rollback"));
		assertThat(Arrays.stream(SchedulingAiRunController.class.getDeclaredMethods())
				.filter(method -> method.isAnnotationPresent(PostMapping.class))
				.map(method -> method.getAnnotation(PreAuthorize.class))
				.filter(annotation -> annotation != null)
				.map(PreAuthorize::value)
				.allMatch(value -> value.contains("education:ai:agent:use")
						&& value.contains("education:scheduling:manage")))
				.isTrue();
	}
}
