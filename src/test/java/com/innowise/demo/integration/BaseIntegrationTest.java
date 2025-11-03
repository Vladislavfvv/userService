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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = {
        "spring.cache.type=none"
})
public abstract class BaseIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.2")
            .withExposedPorts(6379);

//    @Container
//    static GenericContainer<?> redis = new GenericContainer<>("redis:7.2")
//            .withExposedPorts(6379);

//    @DynamicPropertySource
//    static void configureProperties(DynamicPropertyRegistry registry) {
////        // Создаем схему userservice_data
////        try (Connection conn = DriverManager.getConnection(
////                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
////             Statement stmt = conn.createStatement()) {
////            stmt.execute("CREATE SCHEMA IF NOT EXISTS userservice_data");
////        } catch (SQLException e) {
////            throw new RuntimeException(e);
////        }
//
//        // Настройки Spring для Postgres
////        registry.add("spring.datasource.url",
////                () -> postgres.getJdbcUrl() + "?currentSchema=userservice_data");
//        registry.add("spring.datasource.url", postgres::getJdbcUrl);
//        registry.add("spring.datasource.username", postgres::getUsername);
//        registry.add("spring.datasource.password", postgres::getPassword);
//
//        registry.add("spring.liquibase.enabled", () -> "false");
//
//        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
//     //   registry.add("spring.liquibase.default-schema", () -> "userservice_data");
//      //  registry.add("spring.jpa.properties.hibernate.default_schema", () -> "userservice_data");
//

    /// /        registry.add("spring.data.redis.host", redis::getHost);
    /// /        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
//    }
//    @DynamicPropertySource
//    static void configureProperties(DynamicPropertyRegistry registry) {
//        // Создаем схему
//        try (Connection conn = DriverManager.getConnection(
//                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
//             Statement stmt = conn.createStatement()) {
//            stmt.execute("CREATE SCHEMA IF NOT EXISTS userservice_data");
//        } catch (SQLException e) {
//            throw new RuntimeException(e);
//        }
//
//        registry.add("spring.datasource.url",
//                () -> postgres.getJdbcUrl() + "?currentSchema=userservice_data");
//        registry.add("spring.datasource.username", postgres::getUsername);
//        registry.add("spring.datasource.password", postgres::getPassword);
//
//        registry.add("spring.liquibase.enabled", () -> "false");
//        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
//        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "userservice_data");
//    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
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
}
