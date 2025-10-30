package com.innowise.demo.integration;

import com.innowise.demo.dto.UserDto;
import com.innowise.demo.exception.UserNotFoundException;
import com.innowise.demo.mapper.UserMapper;
import com.innowise.demo.model.User;
import com.innowise.demo.repository.UserRepository;
import com.innowise.demo.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserServiceIT {

    // --- Контейнер PostgreSQL ---
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    // --- Контейнер Redis ---
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.2")
            .withExposedPorts(6379);

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private UserDto userDto;

    // --- Настройка схемы и свойств Spring ---
    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        // Создаем схему userservice_data в контейнере PostgreSQL
        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE SCHEMA IF NOT EXISTS userservice_data");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        // Указываем Spring использовать нашу схему
        registry.add("spring.datasource.url",
                () -> postgres.getJdbcUrl() + "?currentSchema=userservice_data"); // <- поменял здесь
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("spring.liquibase.default-schema", () -> "userservice_data"); // <- добавил
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "userservice_data"); // <- добавил

//        registry.add("spring.redis.host", () -> redis.getHost());
//        registry.add("spring.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        userDto = new UserDto();
        userDto.setName("Integration");
        userDto.setSurname("Test");
        userDto.setEmail("integration@example.com");
        userDto.setBirthDate(LocalDate.of(1995, 5, 5));
    }

    @Test
    @Order(1)
    void createUser_ShouldSaveUser() {
        UserDto saved = userService.createUser(userDto);

        assertNotNull(saved.getId());
        assertEquals("integration@example.com", saved.getEmail());
        assertEquals(1, userRepository.count());
    }

    @Test
    @Order(2)
    void findUserById_ShouldReturnUser() {
        User saved = userRepository.save(userMapper.toEntity(userDto));

        UserDto result = userService.findUserById(saved.getId());

        assertNotNull(result);
        assertEquals(saved.getEmail(), result.getEmail());
    }

    @Test
    @Order(3)
    void findUserById_NotFound_ShouldThrow() {
        assertThrows(UserNotFoundException.class, () -> userService.findUserById(999L));
    }

    @Test
    @Order(4)
    void getUserByEmailNative_ShouldReturnUser() {
        User saved = userRepository.save(userMapper.toEntity(userDto));

        UserDto result = userService.getUserByEmailNative(saved.getEmail());

        assertNotNull(result);
        assertEquals(saved.getEmail(), result.getEmail());
    }

    @Test
    @Order(5)
    void updateUser_ShouldModifyData() {
        User saved = userRepository.save(userMapper.toEntity(userDto));

        UserDto updateDto = new UserDto();
        updateDto.setName("Updated");
        updateDto.setSurname("User");
        updateDto.setEmail("updated@example.com");
        updateDto.setBirthDate(LocalDate.of(1990, 1, 1));

        UserDto updated = userService.updateUser(saved.getId(), updateDto);

        assertEquals("Updated", updated.getName());
        assertEquals("updated@example.com", updated.getEmail());
    }

    @Test
    @Order(6)
    void deleteUser_ShouldRemoveFromDatabase() {
        User saved = userRepository.save(userMapper.toEntity(userDto));

        userService.deleteUser(saved.getId());

        assertEquals(0, userRepository.count());
    }
}

