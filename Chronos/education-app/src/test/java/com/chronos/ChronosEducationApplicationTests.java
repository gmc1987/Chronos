package com.chronos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 验证教育行业部署包能装配全部平台与教务模块。
 *
 * <p>该测试会执行 Flyway 并启动 Flowable，只允许显式连接名称中包含
 * {@code test} 或 {@code verify} 的隔离 PostgreSQL 数据库，防止普通
 * {@code mvn test} 意外迁移开发或生产实例。</p>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(
        named = "CHRONOS_DB_URL",
        matches = "jdbc:postgresql:.*/[^/?]*(test|verify)[^/?]*(\\?.*)?")
class ChronosEducationApplicationTests {

	@Test
	void contextLoads() {
	}
}
