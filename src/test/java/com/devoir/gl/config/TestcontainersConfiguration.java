package com.devoir.gl.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Configuration TestContainers pour les tests
 * Lance automatiquement un conteneur PostgreSQL Docker pour tous les tests
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer<?> postgresContainer() {
		return new PostgreSQLContainer<>("postgres:15")
				.withDatabaseName("gl_test")
				.withUsername("test_user")
				.withPassword("test_password");
	}
}
