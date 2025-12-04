package com.innowise.demo.integration;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import com.innowise.demo.dto.UpdateUserDto;
import com.innowise.demo.dto.UserDto;
import com.innowise.demo.exception.UserNotFoundException;
import com.innowise.demo.mapper.UserMapper;
import com.innowise.demo.model.User;
import com.innowise.demo.repository.UserRepository;
import com.innowise.demo.service.UserService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Интеграционные тесты для UserService.
 * Тесты работают с реальной базой данных PostgreSQL через Testcontainers.
 * Используют реальные репозитории и сервисы без моков.
 * Тесты упорядочены с помощью @Order для контроля последовательности выполнения.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserServiceIT extends BaseIntegrationTest{

    // Автоматически внедряемые зависимости из Spring контекста
    @Autowired
    private UserService userService; // Сервис для работы с пользователями

    @Autowired
    private UserRepository userRepository; // Репозиторий для прямого доступа к БД

    @Autowired
    private UserMapper userMapper; // Маппер для преобразования между Entity и DTO

    @Autowired
    private RedisTemplate<String, Object> redisTemplate; // Шаблон для работы с Redis (если используется)

    // Тестовые данные, создаваемые в setUp()
    private UserDto userDto;

    @BeforeEach
    void setUp() {
        // Очищаем базу данных перед каждым тестом
        // Это гарантирует, что каждый тест начинается с чистого состояния
        userRepository.deleteAll();

        // Создаём тестовый DTO для использования в интеграционных тестах
        // В интеграционных тестах мы работаем с реальной базой данных через Testcontainers
        userDto = new UserDto();
        userDto.setFirstName("Integration");
        userDto.setLastName("Test");
        userDto.setEmail("integration@example.com");
        userDto.setBirthDate(LocalDate.of(1995, 5, 5));
    }

    @Test
    @Order(1)
    void createUser_ShouldSaveUser() {
        // given
        // userDto создан в setUp() с данными для создания пользователя

        //when
        // Вызываем тестируемый метод создания пользователя
        // В интеграционном тесте это реальный вызов сервиса с реальной базой данных
        UserDto saved = userService.createUser(userDto);

        // then
        assertNotNull(saved.getId()); // Проверка: что пользователю был присвоен ID (автогенерация)
        assertEquals("integration@example.com", saved.getEmail()); // Проверка: что email совпадает
        assertEquals(1, userRepository.count()); // Проверка: что в базе данных ровно 1 пользователь
    }

    @Test
    @Order(2)
    void findUserById_ShouldReturnUser() {
        // given
        // Сохраняем пользователя напрямую в репозиторий для подготовки данных
        // Преобразуем DTO в сущность через маппер и сохраняем в реальную БД
        User saved = userRepository.save(userMapper.toEntity(userDto));

        //when
        // Вызываем тестируемый метод получения пользователя по ID
        // В интеграционном тесте это реальный запрос к базе данных
        UserDto result = userService.findUserById(saved.getId());

        // then
        assertNotNull(result); // Проверка: что результат не null
        assertEquals(saved.getEmail(), result.getEmail()); // Проверка: что email совпадает с сохранённым
    }

    @Test
    @Order(3)
    void findUserById_NotFound_ShouldThrow() {
        // given
        // База данных пуста (очищена в setUp()), пользователя с ID 999 не существует

        //when & then
        // Вызываем тестируемый метод с несуществующим ID и ожидаем выброс исключения
        // В интеграционном тесте это реальный запрос к базе данных, который вернёт пустой результат
        assertThrows(UserNotFoundException.class, () -> userService.findUserById(999L));
    }

    @Test
    @Order(4)
    void getUserByEmailNative_ShouldReturnUser() {
        // given
        // Сохраняем пользователя напрямую в репозиторий для подготовки данных
        User saved = userRepository.save(userMapper.toEntity(userDto));

        //when
        // Вызываем тестируемый метод получения пользователя по email через нативный SQL запрос
        // В интеграционном тесте это реальный запрос к базе данных через нативный SQL
        UserDto result = userService.getUserByEmailNative(saved.getEmail());

        // then
        assertNotNull(result); // Проверка: что результат не null
        assertEquals(saved.getEmail(), result.getEmail()); // Проверка: что email совпадает с сохранённым
    }

    @Test
    @Order(5)
    void updateCurrentUser_ShouldModifyData() {
        // given
        // Сохраняем пользователя напрямую в репозиторий для подготовки данных
        User saved = userRepository.save(userMapper.toEntity(userDto));

        // Создаём DTO с данными для обновления пользователя
        UpdateUserDto updateDto = new UpdateUserDto();
        updateDto.setFirstName("Updated"); // новое имя
        updateDto.setLastName("User"); // новая фамилия
        updateDto.setBirthDate(LocalDate.of(1990, 1, 1)); // новая дата рождения

        //when
        // Вызываем тестируемый метод обновления текущего пользователя
        // Используем email сохранённого пользователя (ID берется из токена)
        // В интеграционном тесте это реальное обновление данных в базе данных
        UserDto updated = userService.updateCurrentUser(saved.getEmail(), updateDto);

        // then
        assertEquals("Updated", updated.getFirstName()); // Проверка: что имя обновилось
        assertEquals(saved.getEmail(), updated.getEmail()); // Проверка: что email не изменился (берётся из токена)
    }

    @Test
    @Order(6)
    void updateUserByAdmin_ShouldModifyData() {
        // given
        // Сохраняем пользователя напрямую в репозиторий для подготовки данных
        User saved = userRepository.save(userMapper.toEntity(userDto));

        // Создаём DTO с данными для обновления пользователя
        UpdateUserDto updateDto = new UpdateUserDto();
        updateDto.setFirstName("AdminUpdated"); // новое имя
        updateDto.setLastName("AdminUser"); // новая фамилия
        updateDto.setBirthDate(LocalDate.of(1985, 6, 15)); // новая дата рождения

        //when
        // Вызываем тестируемый метод обновления пользователя админом
        // Админ может обновить любого пользователя по ID
        // В интеграционном тесте это реальное обновление данных в базе данных
        UserDto updated = userService.updateUserByAdmin(saved.getId(), updateDto, "admin@example.com");

        // then
        assertEquals("AdminUpdated", updated.getFirstName()); // Проверка: что имя обновилось
        assertEquals(saved.getEmail(), updated.getEmail()); // Проверка: что email не изменился
    }

    @Test
    @Order(7)
    void deleteUser_ShouldRemoveFromDatabase() {
        // given
        // Сохраняем пользователя напрямую в репозиторий для подготовки данных
        User saved = userRepository.save(userMapper.toEntity(userDto));

        //when
        // Вызываем тестируемый метод удаления пользователя
        // В интеграционном тесте это реальное удаление данных из базы данных
        userService.deleteUser(saved.getId());

        // then
        // Проверка: что в базе данных 0 пользователей (пользователь был удалён)
        assertEquals(0, userRepository.count());
    }
}

