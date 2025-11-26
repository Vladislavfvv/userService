package com.innowise.demo.integration;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.redis.core.RedisTemplate;
import com.innowise.demo.dto.CardInfoDto;
import com.innowise.demo.dto.UserDto;
import com.innowise.demo.exception.CardInfoNotFoundException;
import com.innowise.demo.mapper.CardInfoMapper;
import com.innowise.demo.mapper.UserMapper;
import com.innowise.demo.repository.CardInfoRepository;
import com.innowise.demo.repository.UserRepository;
import com.innowise.demo.service.CardInfoService;
import com.innowise.demo.service.UserService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;


/**
 * Интеграционные тесты для CardInfoService.
 * Тесты работают с реальной базой данных PostgreSQL через Testcontainers.
 * Используют реальные репозитории и сервисы без моков.
 * Тесты упорядочены с помощью @Order для контроля последовательности выполнения.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CardInfoServiceIT extends BaseIntegrationTest{

    // Автоматически внедряемые зависимости из Spring контекста
    @Autowired
    private CardInfoService cardInfoService; // Сервис для работы с картами

    @Autowired
    private UserService userService; // Сервис для работы с пользователями (нужен для создания пользователя)

    @Autowired
    private UserRepository userRepository; // Репозиторий для прямого доступа к таблице пользователей

    @Autowired
    private CardInfoRepository cardInfoRepository; // Репозиторий для прямого доступа к таблице карт

    @Autowired
    private CardInfoMapper cardInfoMapper; // Маппер для преобразования между CardInfo Entity и DTO

    @Autowired
    private UserMapper userMapper; // Маппер для преобразования между User Entity и DTO

    @Autowired
    private RedisTemplate<String, Object> redisTemplate; // Шаблон для работы с Redis (если используется)

    // Тестовые данные, создаваемые в setUp()
    private CardInfoDto cardDto;

    @BeforeEach
    void setUp() {
        // Очищаем базы данных перед каждым тестом
        // Это гарантирует, что каждый тест начинается с чистого состояния
        cardInfoRepository.deleteAll();
        userRepository.deleteAll();

        // Создаём тестового пользователя для использования в тестах карт
        // Карты должны принадлежать пользователю, поэтому сначала создаём пользователя
        UserDto userDto = new UserDto();
        userDto.setName("Integration");
        userDto.setSurname("CardTest");
        userDto.setEmail("carduser@example.com");
        userDto.setBirthDate(LocalDate.of(1995, 5, 5));

        // Сохраняем пользователя через сервис в реальную БД
        // В интеграционных тестах мы работаем с реальной базой данных через Testcontainers
        UserDto savedUser = userService.createUser(userDto);

        // Создаём тестовый DTO карты, привязанной к созданному пользователю
        cardDto = new CardInfoDto();
        cardDto.setNumber("5555 6666 7777 8888");
        cardDto.setHolder("Card User");
        cardDto.setExpirationDate(LocalDate.of(2030, 12, 31));
        cardDto.setUserId(savedUser.getId()); // Привязываем карту к созданному пользователю
    }

    @Test
    @Order(1)
    void save_ShouldPersistCard() {
        // given
        // cardDto создан в setUp() с данными для создания карты и привязан к пользователю

        //when
        // Вызываем тестируемый метод сохранения карты
        // В интеграционном тесте это реальный вызов сервиса с реальной базой данных
        CardInfoDto saved = cardInfoService.save(cardDto);

        // then
        assertNotNull(saved.getId()); // Проверка: что карте был присвоен ID (автогенерация)
        assertEquals(cardDto.getNumber(), saved.getNumber()); // Проверка: что номер карты совпадает
        assertEquals(1, cardInfoRepository.count()); // Проверка: что в базе данных ровно 1 карта
    }

    @Test
    @Order(2)
    void getCardInfoById_ShouldReturnCard() {
        // given
        // Сохраняем карту через сервис в реальную БД
        // В интеграционном тесте это реальное сохранение данных в базу данных
        CardInfoDto saved = cardInfoService.save(cardDto);

        //when
        // Вызываем тестируемый метод получения карты по ID
        // В интеграционном тесте это реальный запрос к базе данных
        CardInfoDto result = cardInfoService.getCardInfoById(saved.getId());

        // then
        assertNotNull(result); // Проверка: что результат не null
        assertEquals(saved.getNumber(), result.getNumber()); // Проверка: что номер карты совпадает с сохранённым
    }

    @Test
    @Order(3)
    void getCardInfoById_NotFound_ShouldThrow() {
        // given
        // База данных пуста (очищена в setUp()), карты с ID 999 не существует

        //when & then
        // Вызываем тестируемый метод с несуществующим ID и ожидаем выброс исключения
        // В интеграционном тесте это реальный запрос к базе данных, который вернёт пустой результат
        assertThrows(CardInfoNotFoundException.class, () -> cardInfoService.getCardInfoById(999L));
    }


    @Test
    @Order(4)
    void getAllCardInfos_ShouldReturnPagedList() {
        // given
        // Сохраняем карту через сервис в реальную БД
        cardInfoService.save(cardDto);

        //when
        // Вызываем тестируемый метод получения всех карт с пагинацией
        // В интеграционном тесте это реальный запрос к базе данных с пагинацией
        Page<CardInfoDto> page = cardInfoService.getAllCardInfos(0, 10);

        // then
        assertNotNull(page); // Проверка: что результат не null
        assertFalse(page.isEmpty()); // Проверка: что страница не пустая
        assertEquals(1, page.getTotalElements()); // Проверка: что всего элементов 1
        assertEquals(cardDto.getNumber(), page.getContent().get(0).getNumber()); // Проверка: что номер карты совпадает
    }

    @Test
    @Order(5)
    void getAllCardInfos_Empty_ShouldThrowException() {
        // given
        // База данных пуста (очищена в setUp()), карт нет

        //when & then
        // Вызываем тестируемый метод получения всех карт и ожидаем выброс исключения
        // В интеграционном тесте это реальный запрос к пустой базе данных
        assertThrows(CardInfoNotFoundException.class, () -> cardInfoService.getAllCardInfos(0, 5));
    }

    @Test
    @Order(6)
    void updateCardInfo_ShouldModifyExistingRecord() {
        // given
        // Сохраняем карту через сервис в реальную БД
        CardInfoDto saved = cardInfoService.save(cardDto);

        // Создаём DTO с данными для обновления карты
        CardInfoDto update = new CardInfoDto();
        update.setNumber("1111 2222 3333 4444"); // новый номер карты
        update.setHolder("Updated Holder"); // новое имя держателя
        update.setExpirationDate(LocalDate.of(2040, 1, 1)); // новая дата истечения
        update.setUserId(saved.getUserId()); // ID пользователя остаётся прежним

        //when
        // Вызываем тестируемый метод обновления карты
        // В интеграционном тесте это реальное обновление данных в базе данных
        CardInfoDto updated = cardInfoService.updateCardInfo(saved.getId(), update);

        // then
        assertEquals(update.getNumber(), updated.getNumber()); // Проверка: что номер карты обновился
        assertEquals(update.getHolder(), updated.getHolder()); // Проверка: что имя держателя обновилось
        assertEquals(update.getExpirationDate(), updated.getExpirationDate()); // Проверка: что дата истечения обновилась
    }

    @Test
    @Order(7)
    void updateCardInfo_NotFound_ShouldThrow() {
        // given
        // Создаём DTO с данными для обновления карты
        CardInfoDto update = new CardInfoDto();
        update.setNumber("0000 0000 0000 0000");
        update.setHolder("Nobody");
        update.setExpirationDate(LocalDate.now().plusYears(5));

        // База данных пуста (очищена в setUp()), карты с ID 999 не существует

        //when & then
        // Вызываем тестируемый метод обновления несуществующей карты и ожидаем выброс исключения
        // В интеграционном тесте это реальный запрос к базе данных, который вернёт пустой результат
        assertThrows(CardInfoNotFoundException.class,
                () -> cardInfoService.updateCardInfo(999L, update));
    }

    @Test
    @Order(8)
    void deleteCardInfo_ShouldRemoveEntity() {
        // given
        // Сохраняем карту через сервис в реальную БД
        CardInfoDto saved = cardInfoService.save(cardDto);
        Long id = saved.getId();

        //when
        // Вызываем тестируемый метод удаления карты
        // В интеграционном тесте это реальное удаление данных из базы данных
        cardInfoService.deleteCardInfo(id);

        // then
        // Проверка: что карта больше не существует в базе данных
        // Используем репозиторий напрямую для проверки, что запись действительно удалена
        assertFalse(cardInfoRepository.findById(id).isPresent());
    }

    @Test
    @Order(9)
    void deleteCardInfo_NotFound_ShouldThrow() {
        // given
        // База данных пуста (очищена в setUp()), карты с ID 999 не существует

        //when & then
        // Вызываем тестируемый метод удаления несуществующей карты и ожидаем выброс исключения
        // В интеграционном тесте это реальный запрос к базе данных, который вернёт пустой результат
        assertThrows(CardInfoNotFoundException.class,
                () -> cardInfoService.deleteCardInfo(999L));
    }
}
