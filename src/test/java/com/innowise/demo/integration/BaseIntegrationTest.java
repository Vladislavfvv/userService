package com.innowise.demo.integration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

@SpringBootTest(properties = {
        "spring.cache.type=none"
})
public abstract class BaseIntegrationTest {
    private static final boolean USE_TESTCONTAINERS =
            !"false".equalsIgnoreCase(System.getenv().getOrDefault("USE_TESTCONTAINERS", "true"));

    static PostgreSQLContainer<?> postgres;
    static GenericContainer<?> redis;

    static {
        // Создаём и стартуем контейнеры только если USE_TESTCONTAINERS=true
        if (USE_TESTCONTAINERS) {
            postgres = new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("testdb")
                    .withUsername("test")
                    .withPassword("test")
                    .withStartupAttempts(3);
            postgres.start();

            redis = new GenericContainer<>("redis:7.2")
                    .withExposedPorts(6379)
                    .waitingFor(Wait.forListeningPort());
            redis.start();

            // Закрываем контейнеры при завершении JVM
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (redis != null) {
                    redis.stop();
                }
                if (postgres != null) {
                    postgres.stop();
                }
            }));
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (USE_TESTCONTAINERS) {
            try (Connection conn = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE SCHEMA IF NOT EXISTS userservice_data");
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }

            registry.add("spring.datasource.url",
                    () -> postgres.getJdbcUrl() + "?currentSchema=userservice_data");
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);

            registry.add("spring.liquibase.enabled", () -> "false");
            registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
            registry.add("spring.jpa.properties.hibernate.default_schema", () -> "userservice_data");

            // Hikari: более короткий lifecycle для тестов, чтобы не было WARN на shutdown
            registry.add("spring.datasource.hikari.max-lifetime", () -> "30000");
            registry.add("spring.datasource.hikari.idle-timeout", () -> "10000");
            registry.add("spring.datasource.hikari.minimum-idle", () -> "0");
            registry.add("spring.datasource.hikari.maximum-pool-size", () -> "5");

            // Снижаем уровень логов Hikari в тестах, чтобы скрыть шумные WARN на shutdown
            registry.add("logging.level.com.zaxxer.hikari", () -> "ERROR");

            // Redis (Testcontainers)
            registry.add("spring.data.redis.host", redis::getHost);
            registry.add("spring.data.redis.port", () -> String.valueOf(redis.getMappedPort(6379)));
        }
        // else: используем окружение CI (services postgres/redis), ничего не переопределяем
    }
}
