package com.devoir.gl.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Configuration alternative pour tests avec PostgreSQL LOCAL
 * Utilise @Profile("test-local") au lieu de TestContainers
 * 
 * Utilisation:
 * mvn clean test -Dspring.profiles.active=test-local
 */
@TestConfiguration(proxyBeanMethods = false)
@Profile("test-local")
public class LocalPostgresTestConfig {

	@Bean
	DataSource dataSource() {
		HikariConfig config = new HikariConfig();
		config.setJdbcUrl("jdbc:postgresql://localhost:5432/gl_test");
		config.setUsername("agromav");
		config.setPassword("agromav");
		config.setMaximumPoolSize(5);
		config.setMinimumIdle(2);
		config.setIdleTimeout(30000);
		config.setAutoCommit(false);
		return new HikariDataSource(config);
	}
}
