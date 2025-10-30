package com.innowise.demo.integration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;

import com.innowise.demo.dto.CardInfoDto;
import com.innowise.demo.dto.UserDto;
import com.innowise.demo.exception.CardInfoNotFoundException;
import com.innowise.demo.mapper.CardInfoMapper;
import com.innowise.demo.mapper.UserMapper;
import com.innowise.demo.repository.CardInfoRepository;
import com.innowise.demo.repository.UserRepository;
import com.innowise.demo.service.CardInfoService;
import com.innowise.demo.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = {
        "spring.cache.type=none"
})//временно отключить кэширование (для интеграционных тестов, где кэш не тестируется) это заставит Spring использовать NoOpCacheManager
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CardInfoServiceIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.2")
            .withExposedPorts(6379);

    @Autowired
    private CardInfoService cardInfoService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CardInfoRepository cardInfoRepository;

    @Autowired
    private CardInfoMapper cardInfoMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

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

    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
}

    private UserDto userDto;
    private CardInfoDto cardDto;

    @BeforeEach
    void setUp() {
        cardInfoRepository.deleteAll();
        userRepository.deleteAll();

        userDto = new UserDto();
        userDto.setName("Integration");
        userDto.setSurname("CardTest");
        userDto.setEmail("carduser@example.com");
        userDto.setBirthDate(LocalDate.of(1995, 5, 5));

        UserDto savedUser = userService.createUser(userDto);

        cardDto = new CardInfoDto();
        cardDto.setNumber("5555 6666 7777 8888");
        cardDto.setHolder("Card User");
        cardDto.setExpirationDate(LocalDate.of(2030, 12, 31));
        cardDto.setUserId(savedUser.getId());
    }

    @Test
    @Order(1)
    void save_ShouldPersistCard() {
        // when
        CardInfoDto saved = cardInfoService.save(cardDto);

        // then
        assertNotNull(saved.getId());
        assertEquals(cardDto.getNumber(), saved.getNumber());
        assertEquals(1, cardInfoRepository.count());
    }

    @Test
    @Order(2)
    void getCardInfoById_ShouldReturnCard() {
        // given
        CardInfoDto saved = cardInfoService.save(cardDto);

        // when
        CardInfoDto result = cardInfoService.getCardInfoById(saved.getId());

        // then
        assertNotNull(result);
        assertEquals(saved.getNumber(), result.getNumber());
    }

    @Test
    @Order(3)
    void getCardInfoById_NotFound_ShouldThrow() {
        // when & then
        assertThrows(CardInfoNotFoundException.class, () -> cardInfoService.getCardInfoById(999L));
    }


    @Test
    @Order(4)
    void getAllCardInfos_ShouldReturnPagedList() {
        // given
        cardInfoService.save(cardDto);

        // when
        Page<CardInfoDto> page = cardInfoService.getAllCardInfos(0, 10);

        // then
        assertNotNull(page);
        assertFalse(page.isEmpty());
        assertEquals(1, page.getTotalElements());
        assertEquals(cardDto.getNumber(), page.getContent().get(0).getNumber());
    }

    @Test
    @Order(5)
    void getAllCardInfos_Empty_ShouldThrowException() {
        // when & then
        assertThrows(CardInfoNotFoundException.class, () -> cardInfoService.getAllCardInfos(0, 5));
    }

    @Test
    @Order(6)
    void updateCardInfo_ShouldModifyExistingRecord() {
        // given
        CardInfoDto saved = cardInfoService.save(cardDto);
        CardInfoDto update = new CardInfoDto();
        update.setNumber("1111 2222 3333 4444");
        update.setHolder("Updated Holder");
        update.setExpirationDate(LocalDate.of(2040, 1, 1));
        update.setUserId(saved.getUserId());

        // when
        CardInfoDto updated = cardInfoService.updateCardInfo(saved.getId(), update);

        // then
        assertEquals(update.getNumber(), updated.getNumber());
        assertEquals(update.getHolder(), updated.getHolder());
        assertEquals(update.getExpirationDate(), updated.getExpirationDate());
    }

    @Test
    @Order(7)
    void updateCardInfo_NotFound_ShouldThrow() {
        CardInfoDto update = new CardInfoDto();
        update.setNumber("0000 0000 0000 0000");
        update.setHolder("Nobody");
        update.setExpirationDate(LocalDate.now().plusYears(5));

        assertThrows(CardInfoNotFoundException.class,
                () -> cardInfoService.updateCardInfo(999L, update));
    }

    @Test
    @Order(8)
    void deleteCardInfo_ShouldRemoveEntity() {
        // given
        CardInfoDto saved = cardInfoService.save(cardDto);
        Long id = saved.getId();

        // when
        cardInfoService.deleteCardInfo(id);

        // then
        assertFalse(cardInfoRepository.findById(id).isPresent());
    }

    @Test
    @Order(9)
    void deleteCardInfo_NotFound_ShouldThrow() {
        // when & then
        assertThrows(CardInfoNotFoundException.class,
                () -> cardInfoService.deleteCardInfo(999L));
    }
}
