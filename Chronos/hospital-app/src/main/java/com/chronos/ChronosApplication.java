package com.chronos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = {
        "com.chronos.model",
        "com.chronos.message.model",
        "com.chronos.file.model",
        "com.chronos.industry.model",
        "com.chronos.knowledge.model",
        "com.chronos.ai.model"
})
@EnableJpaRepositories(basePackages = {
        "com.chronos.Idao",
        "com.chronos.message.Idao",
        "com.chronos.file.dao",
        "com.chronos.industry.dao",
        "com.chronos.knowledge.dao",
        "com.chronos.ai.dao"
})
public class ChronosApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChronosApplication.class, args);
    }

}
