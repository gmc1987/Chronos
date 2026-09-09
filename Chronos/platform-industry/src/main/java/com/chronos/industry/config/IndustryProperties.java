package com.chronos.industry.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chronos.industry")
public class IndustryProperties {

    private String active = "HOSPITAL";

    public String getActive() {
        return active;
    }

    public void setActive(String active) {
        this.active = active;
    }
}
