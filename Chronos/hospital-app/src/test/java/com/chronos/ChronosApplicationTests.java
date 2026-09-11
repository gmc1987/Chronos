package com.chronos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 完整应用上下文测试会执行数据库初始化，只允许连接显式的测试或验证库。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(
        named = "CHRONOS_DB_URL",
        matches = "jdbc:postgresql:.*/[^/?]*(test|verify)[^/?]*(\\?.*)?")
class ChronosApplicationTests {

	@Test
	void contextLoads() {
	}

}
