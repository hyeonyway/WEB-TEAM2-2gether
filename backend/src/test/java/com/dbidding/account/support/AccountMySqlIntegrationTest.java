package com.dbidding.account.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest(properties = {
	"statistic.scheduler.enabled=false",
	"auction.closing.scheduler.enabled=false",
	"auction.deadline.scheduler.enabled=false",
	"spring.sql.init.mode=never",
	"spring.jpa.hibernate.ddl-auto=validate"
})
public abstract class AccountMySqlIntegrationTest {

	protected static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
		.withDatabaseName("dbidding")
		.withInitScript("schema.sql");

	static {
		MYSQL.start();
	}

	@DynamicPropertySource
	static void registerMySqlProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
		registry.add("spring.datasource.username", MYSQL::getUsername);
		registry.add("spring.datasource.password", MYSQL::getPassword);
	}

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void cleanAccountData() {
		jdbcTemplate.update("DELETE FROM authentication");
		jdbcTemplate.update("DELETE FROM wallets");
		jdbcTemplate.update("DELETE FROM users");
	}
}
