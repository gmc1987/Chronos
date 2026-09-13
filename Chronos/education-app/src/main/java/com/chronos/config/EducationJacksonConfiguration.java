package com.chronos.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Supplies the shared mapper required by education services when the
 * application is assembled without Spring Boot's web JSON auto-configuration.
 */
@Configuration
public class EducationJacksonConfiguration {

	@Bean
	@ConditionalOnMissingBean(ObjectMapper.class)
	ObjectMapper objectMapper() {
		return new ObjectMapper().findAndRegisterModules();
	}
}
