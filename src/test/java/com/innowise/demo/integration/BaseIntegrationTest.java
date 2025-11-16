package com.innowise.demo.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import com.innowise.demo.client.AuthServiceClient;

@SpringBootTest(properties = {
        "spring.cache.type=none"
})
@SuppressWarnings({"resource", "removal"})
public abstract class BaseIntegrationTest {
    private static final boolean USE_TESTCONTAINERS =
            !"false".equalsIgnoreCase(System.getenv().getOrDefault("USE_TESTCONTAINERS", "true"));

    static PostgreSQLContainer<?> postgres;
    static GenericContainer<?> redis;

    @MockBean
    protected AuthServiceClient authServiceClient;

    @BeforeEach
    void authenticateAsAdmin() {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        "admin@tut.by",
                        "admin",
                        java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

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
            // Используем схему public по умолчанию (не создаем userservice_data)
            
            registry.add("spring.datasource.url",
                    () -> postgres.getJdbcUrl() + "?currentSchema=public");
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);

            registry.add("spring.liquibase.enabled", () -> "false");
            registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
            registry.add("spring.jpa.properties.hibernate.default_schema", () -> "public");

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
        } else {
            // CI: используем сервисы GitHub Actions (postgres/redis) и создаём схему вручную
            String pgHost = System.getenv().getOrDefault("POSTGRES_HOST", "localhost");
            String pgPort = System.getenv().getOrDefault("POSTGRES_PORT", "5432");
            String pgDb = System.getenv().getOrDefault("POSTGRES_DB", "us_db");
            String pgUser = System.getenv().getOrDefault("POSTGRES_USER", "postgres");
            String pgPass = System.getenv().getOrDefault("POSTGRES_PASSWORD", "postgres");

            String baseUrl = "jdbc:postgresql://" + pgHost + ":" + pgPort + "/" + pgDb;

            // Используем схему public по умолчанию (не создаем userservice_data)

            registry.add("spring.datasource.url", () -> baseUrl + "?currentSchema=public");
            registry.add("spring.datasource.username", () -> pgUser);
            registry.add("spring.datasource.password", () -> pgPass);

            registry.add("spring.liquibase.enabled", () -> "false");
            registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
            registry.add("spring.jpa.properties.hibernate.default_schema", () -> "public");

            // Hikari в CI
            registry.add("spring.datasource.hikari.max-lifetime", () -> "30000");
            registry.add("spring.datasource.hikari.idle-timeout", () -> "10000");
            registry.add("spring.datasource.hikari.minimum-idle", () -> "0");
            registry.add("spring.datasource.hikari.maximum-pool-size", () -> "5");

            // Redis (services)
            String redisHost = System.getenv().getOrDefault("REDIS_HOST", "localhost");
            String redisPort = System.getenv().getOrDefault("REDIS_PORT", "6379");
            registry.add("spring.data.redis.host", () -> redisHost);
            registry.add("spring.data.redis.port", () -> redisPort);
        }
    }
}
