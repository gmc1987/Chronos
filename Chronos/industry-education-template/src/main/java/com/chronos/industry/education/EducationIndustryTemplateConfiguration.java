package com.chronos.industry.education;

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
public class EducationIndustryTemplateConfiguration {

    @Bean
    IndustryTemplateProvider educationIndustryTemplate() {
        return () -> new IndustryMetadata(
                "EDUCATION",
                "教育行业",
                "1.0.0",
                new IndustryBranding(
                        "智慧校园教务协同平台",
                        "Chronos 教育",
                        "智慧校园",
                        "智慧校园教务协同平台",
                        "使用学校统一身份账号登录",
                        "学校/校区",
                        "院系",
                        "教职工"),
                List.of(
                        new OrganizationTypeDefinition("EDUCATION_GROUP", "教育集团", 10),
                        new OrganizationTypeDefinition("SCHOOL", "学校", 20),
                        new OrganizationTypeDefinition("CAMPUS", "校区", 30),
                        new OrganizationTypeDefinition("COLLEGE", "学院", 40),
                        new OrganizationTypeDefinition("DEPARTMENT", "系部", 50)),
                List.of(
                        new DirectoryOptionDefinition("ADMINISTRATIVE", "行政部门", 10),
                        new DirectoryOptionDefinition("ACADEMIC", "教学单位", 20),
                        new DirectoryOptionDefinition("RESEARCH", "科研机构", 30),
                        new DirectoryOptionDefinition("SUPPORT", "教辅单位", 40),
                        new DirectoryOptionDefinition("DEPARTMENT", "其他单位", 50)),
                List.of(
                        new DirectoryOptionDefinition("TEACHING", "教学", 10),
                        new DirectoryOptionDefinition("RESEARCH", "科研", 20),
                        new DirectoryOptionDefinition("ADMINISTRATIVE", "行政", 30),
                        new DirectoryOptionDefinition("STUDENT_AFFAIRS", "学生工作", 40),
                        new DirectoryOptionDefinition("LOGISTICS", "后勤", 50)),
                List.of(
                        new IndustryFeatureDefinition("education-academic", "教务基础数据", true),
                        new IndustryFeatureDefinition("education-teaching", "教学运行", true),
                        new IndustryFeatureDefinition("education-class-scheduling", "走班排课", true),
                        new IndustryFeatureDefinition("education-student", "学生管理", false)),
                List.of(
                        new IndustryNavigationDefinition(
                                "education-terms", "学年学期", "/admin/education/terms",
                                "education:scheduling:view", 20),
                        new IndustryNavigationDefinition(
                                "education-courses", "课程管理", "/admin/education/courses",
                                "education:scheduling:view", 21),
                        new IndustryNavigationDefinition(
                                "education-majors", "专业管理", "/admin/education/majors",
                                "education:scheduling:view", 22),
                        new IndustryNavigationDefinition(
                                "education-classes", "班级管理", "/admin/education/classes",
                                "education:scheduling:view", 23),
                        new IndustryNavigationDefinition(
                                "education-teachers", "教师中心", "/admin/education/teachers",
                                "education:scheduling:view", 24),
                        new IndustryNavigationDefinition(
                                "education-students", "学生中心", "/admin/education/students",
                                "education:scheduling:view", 25),
                        new IndustryNavigationDefinition(
                                "education-class-scheduling",
                                "走班排课",
                                "/admin/education/scheduling",
                                "education:scheduling:view",
                                30)));
    }
}
