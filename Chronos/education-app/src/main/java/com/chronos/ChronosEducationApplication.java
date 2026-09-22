package com.chronos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = {
		"com.chronos.model.pojo",
        "com.chronos.model",
        "com.chronos.message.model",
        "com.chronos.file.model",
		"com.chronos.industry.model",
		"com.chronos.education.scheduling.model",
		"com.chronos.education.grade.model",
		"com.chronos.education.meeting.model",
		"com.chronos.education.homeschool.model",
        "com.chronos.education.supervision.model",
        "com.chronos.integration.model",
        "com.chronos.knowledge.model",
        "com.chronos.ai.model"
})
@EnableJpaRepositories(basePackages = {
        "com.chronos.Idao",
        "com.chronos.message.Idao",
        "com.chronos.file.dao",
		"com.chronos.industry.dao",
		"com.chronos.education.scheduling.dao",
		"com.chronos.education.grade.dao",
		"com.chronos.education.meeting.dao",
		"com.chronos.education.homeschool.dao",
		"com.chronos.education.supervision.dao",
        "com.chronos.integration.dao",
        "com.chronos.knowledge.dao",
        "com.chronos.ai.dao"
})
public class ChronosEducationApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChronosEducationApplication.class, args);
    }
}
