package com.chronos.industry.hospital;

import com.chronos.industry.api.IndustryBranding;
import com.chronos.industry.api.DirectoryOptionDefinition;
import com.chronos.industry.api.IndustryFeatureDefinition;
import com.chronos.industry.api.IndustryMetadata;
import com.chronos.industry.api.IndustryNavigationDefinition;
import com.chronos.industry.api.IndustryTemplateProvider;
import com.chronos.industry.api.OrganizationTypeDefinition;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HospitalIndustryTemplateConfiguration {

    @Bean
    IndustryTemplateProvider hospitalIndustryTemplate() {
        return () -> new IndustryMetadata(
                "HOSPITAL",
                "医疗行业",
                "1.0.0",
                new IndustryBranding(
                        "医院智慧协同办公平台",
                        "Chronos 医疗",
                        "医院智慧办公",
                        "医院智慧协同办公平台",
                        "使用医院统一账号登录",
                        "医院/院区",
                        "科室",
                        "员工"),
                List.of(
                        new OrganizationTypeDefinition("MEDICAL_GROUP", "医疗集团", 10),
                        new OrganizationTypeDefinition("HOSPITAL", "医院", 20),
                        new OrganizationTypeDefinition("CAMPUS", "院区", 30)),
                List.of(
                        new DirectoryOptionDefinition("ADMINISTRATIVE", "行政部门", 10),
                        new DirectoryOptionDefinition("CLINICAL", "临床科室", 20),
                        new DirectoryOptionDefinition("MEDICAL_TECHNOLOGY", "医技科室", 30),
                        new DirectoryOptionDefinition("NURSING", "护理单元", 40),
                        new DirectoryOptionDefinition("DEPARTMENT", "其他部门", 50)),
                List.of(
                        new DirectoryOptionDefinition("MEDICAL", "医疗", 10),
                        new DirectoryOptionDefinition("NURSING", "护理", 20),
                        new DirectoryOptionDefinition("TECHNICAL", "医技", 30),
                        new DirectoryOptionDefinition("ADMINISTRATIVE", "行政", 40),
                        new DirectoryOptionDefinition("LOGISTICS", "后勤", 50)),
                List.of(
                        new IndustryFeatureDefinition("hospital-oa", "医院协同办公", true),
                        new IndustryFeatureDefinition("medical-knowledge", "医疗知识中心", true)),
                List.of(
                        new IndustryNavigationDefinition(
                                "hospital-oa",
                                "协同办公",
                                "/portal/apps",
                                "workflow:instance:view",
                                20)));
    }
}
