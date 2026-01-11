package com.innowise.demo.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innowise.demo.dto.CardInfoDto;
import com.innowise.demo.dto.UserDto;
import com.innowise.demo.exception.CardInfoNotFoundException;
import com.innowise.demo.service.CardInfoService;
import com.innowise.demo.service.UserService;
import com.innowise.demo.util.SecurityUtils;

import static java.util.List.of;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CardInfoController.class, excludeAutoConfiguration = {
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
        org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
class CardInfoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CardInfoService cardInfoService;

    @MockitoBean
    private UserService userService;

    private CardInfoDto cardInfoDto;

    @BeforeEach
    void setUp() {
        cardInfoDto = new CardInfoDto();
        cardInfoDto.setId(1L);
        cardInfoDto.setNumber("1234567890123456"); // 16 цифр без пробелов
        cardInfoDto.setHolder("Roma Romanovich");
        cardInfoDto.setExpirationDate(LocalDate.of(2030, 12, 31));
        cardInfoDto.setUserId(1L);
    }

    /**
     * Вспомогательный метод для создания мок Authentication с JWT токеном.
     * Используется в тестах для имитации аутентифицированного пользователя.
     *
     * @param email email пользователя (будет добавлен в claim "sub")
     * @param role  роль пользователя (USER или ADMIN)
     * @return JwtAuthenticationToken с настроенным JWT токеном
     */
    private JwtAuthenticationToken createMockAuthentication(String email, String role) {
        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "HS256")
                .claim("sub", email)
                .claim("role", role)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        return new JwtAuthenticationToken(
                jwt,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role))
        );
    }

    @Test
    @DisplayName("POST /api/v1/cards - успешное создание карты")
    void addCardInfo_ShouldReturnCreatedCard() throws Exception {
        // given
        // Создаём DTO для запроса — данные, которые клиент типа отправляет на сервер
        CardInfoDto requestDto = new CardInfoDto();
        requestDto.setNumber("1234567890123456"); // 16 цифр без пробелов
        requestDto.setHolder("Roma Romanovich");
        requestDto.setExpirationDate(LocalDate.of(2030, 12, 31));
        requestDto.setUserId(1L); // ID пользователя, которому принадлежит карта

        // Создаём DTO, которое будет возвращать сервис после сохранения карты
        CardInfoDto createdDto = new CardInfoDto();
        createdDto.setId(1L);
        createdDto.setNumber("1234567890123456");
        createdDto.setHolder("Roma Romanovich");
        createdDto.setExpirationDate(LocalDate.of(2030, 12, 31));
        createdDto.setUserId(1L);

        //создаём тестового юзера, которого будет возвращать мок
        UserDto userDto = new UserDto();
        userDto.setId(1L);
        userDto.setEmail("test@example.com");

        //when
        //Когда кто-то вызовет userService.findUserById(1L), верни этот объект userDto, т.е. мокирование поведения метода сервиса
        when(userService.findUserById(1L)).thenReturn(userDto);
        when(cardInfoService.save(any(CardInfoDto.class))).thenReturn(createdDto);

        //Внутри этого блока try ниже — все вызовы SecurityUtils будут мокнутыми(т.к. SecurityUtils  -статический класс)
        //А за пределами блока — опять настоящие
        try (MockedStatic<SecurityUtils> mockedSecurityUtils = mockStatic(SecurityUtils.class)) {
            JwtAuthenticationToken authentication = createMockAuthentication("test@example.com", "USER");//Этот объект — как будто Spring Security уже залогинил пользователя с email "test@example.com" и ролью "USER"
            mockedSecurityUtils.when(() -> SecurityUtils.hasAccess(any(), eq("test@example.com")))//Когда метод SecurityUtils.hasAccess(...) вызвается с любым первым аргументом и "test@example.com" во втором — возвращай true
                    .thenReturn(true);

            // then
            mockMvc.perform(post("/api/v1/cards")//Это говорит Spring MVC: представь, что клиент отправил JWT-токен, и вот информация о пользователе
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestDto))
                            .principal(authentication))// И внутри контроллера: будет твой JwtAuthenticationToken
                    .andExpect(status().isCreated()) // Проверка: что контроллер вернул 201 Created
                    .andExpect(jsonPath("$.id").value(1L))// Проверка: JSON в ответе совпадает с созданной карточкой
                    .andExpect(jsonPath("$.number").value("1234567890123456"))
                    .andExpect(jsonPath("$.holder").value("Roma Romanovich"))
                    .andExpect(jsonPath("$.userId").value(1L));
        }
    }

    @Test
    @DisplayName("POST /api/v1/cards - валидация: пустой номер карты")
    void addCardInfo_ShouldReturnBadRequest_WhenNumberIsBlank() throws Exception {
        // given
        // Создаём DTO с невалидными данными — пустой номер карты
        // Это проверяет, что Spring Validation (@NotBlank) работает корректно
        CardInfoDto invalidDto = new CardInfoDto();
        invalidDto.setNumber(""); // пустой номер — это нарушает валидацию @NotBlank
        invalidDto.setHolder("Roma Romanovich");
        invalidDto.setUserId(1L);

        //when
        // Внутри этого блока try — все вызовы SecurityUtils будут мокнутыми
        try (MockedStatic<SecurityUtils> mockedSecurityUtils = mockStatic(SecurityUtils.class)) {
            // Создаём мок аутентификации — как будто пользователь уже залогинен
            JwtAuthenticationToken authentication = createMockAuthentication("test@example.com", "USER");

            // then
            // Валидация произойдет до проверки доступа — Spring вернёт 400 Bad Request
            // ещё до того, как контроллер попытается проверить права доступа
            mockMvc.perform(post("/api/v1/cards")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidDto))
                            .principal(authentication))
                    .andExpect(status().isBadRequest()); // Проверка: что Spring Validation отклонил запрос
        }
    }

    @Test
    @DisplayName("GET /api/v1/cards/1 - успешное получение карты по ID")
    void getCardInfoById_ShouldReturnCard() throws Exception {
        // given
        // Создаём тестового пользователя, которого будет возвращать мок
        UserDto userDto = new UserDto();
        userDto.setId(1L);
        userDto.setEmail("test@example.com");

        //when
        // Когда кто-то вызовет cardInfoService.getCardInfoById(1L), верни cardInfoDto
        // (это объект, созданный в setUp() с данными карты)
        when(cardInfoService.getCardInfoById(1L)).thenReturn(cardInfoDto);
        // Когда кто-то вызовет userService.findUserById(1L), верни userDto
        when(userService.findUserById(1L)).thenReturn(userDto);

        // Внутри этого блока try — все вызовы SecurityUtils будут мокнутыми
        try (MockedStatic<SecurityUtils> mockedSecurityUtils = mockStatic(SecurityUtils.class)) {
            // Создаём мок аутентификации — как будто пользователь уже залогинен
            JwtAuthenticationToken authentication = createMockAuthentication("test@example.com", "USER");
            // Когда метод SecurityUtils.hasAccess(...) вызвается с любым первым аргументом
            // и "test@example.com" во втором — возвращай true (разрешаем доступ)
            mockedSecurityUtils.when(() -> SecurityUtils.hasAccess(any(), eq("test@example.com")))
                    .thenReturn(true);

            // then
            // Выполняем GET запрос на /api/v1/cards/1 с мокнутой аутентификацией
            mockMvc.perform(get("/api/v1/cards/1")
                            .principal(authentication))
                    .andExpect(status().isOk()) // Проверка: что контроллер вернул 200 OK
                    .andExpect(jsonPath("$.id").value(1L)) // Проверка: JSON содержит правильный ID
                    .andExpect(jsonPath("$.number").value("1234567890123456")) // Проверка: номер карты совпадает
                    .andExpect(jsonPath("$.holder").value("Roma Romanovich")); // Проверка: имя держателя совпадает
        }
    }

    @Test
    @DisplayName("GET /api/v1/cards/999 - карта не найдена")
    void getCardInfoById_ShouldReturnNotFound_WhenCardNotFound() throws Exception {
        // given & when
        // Когда кто-то вызовет cardInfoService.getCardInfoById(999L), выбрось исключение
        // Это имитирует ситуацию, когда карты с таким ID не существует в базе данных
        when(cardInfoService.getCardInfoById(999L))
                .thenThrow(new CardInfoNotFoundException("Card with id 999 not found!"));

        //when
        // Внутри этого блока try — все вызовы SecurityUtils будут мокнутыми
        try (MockedStatic<SecurityUtils> mockedSecurityUtils = mockStatic(SecurityUtils.class)) {
            // Создаём мок аутентификации — как будто пользователь уже залогинен
            JwtAuthenticationToken authentication = createMockAuthentication("test@example.com", "USER");

            // then
            // Выполняем GET запрос на несуществующую карту
            mockMvc.perform(get("/api/v1/cards/999")
                            .principal(authentication))
                    .andExpect(status().isNotFound()); // Проверка: что контроллер вернул 404 Not Found
        }
    }

    @Test
    @DisplayName("GET /api/v1/cards - успешное получение списка карт с пагинацией")
    void getAllCardInfos_ShouldReturnPagedResponse() throws Exception {
        // given
        // Создаём объект Page с одной картой внутри (cardInfoDto из setUp())
        // PageImpl — это реализация интерфейса Page от Spring Data
        Page<CardInfoDto> page = new PageImpl<>(of(cardInfoDto));

        //when
        // Когда кто-то вызовет cardInfoService.getAllCardInfos(0, 10), верни этот объект page
        // (страница 0, размер страницы 10)
        when(cardInfoService.getAllCardInfos(0, 10)).thenReturn(page);

        // Внутри этого блока try — все вызовы SecurityUtils будут мокнутыми
        try (MockedStatic<SecurityUtils> mockedSecurityUtils = mockStatic(SecurityUtils.class)) {
            // Создаём мок аутентификации для администратора — только ADMIN может видеть все карты
            JwtAuthenticationToken authentication = createMockAuthentication("admin@tut.by", "ADMIN");
            // Когда метод SecurityUtils.isAdmin(authentication) вызвается — возвращай true
            // Это разрешает доступ администратору к списку всех карт
            mockedSecurityUtils.when(() -> SecurityUtils.isAdmin(authentication))
                    .thenReturn(true);

            // then
            // Выполняем GET запрос на /api/v1/cards с параметрами пагинации
            mockMvc.perform(get("/api/v1/cards")
                            .param("page", "0") // Параметр: номер страницы (начинается с 0)
                            .param("size", "10") // Параметр: размер страницы (количество элементов)
                            .principal(authentication))
                    .andExpect(status().isOk()) // Проверка: что контроллер вернул 200 OK
                    .andExpect(jsonPath("$.content").isArray()) // Проверка: что content — это массив
                    .andExpect(jsonPath("$.content.length()").value(1)) // Проверка: что в массиве 1 элемент
                    .andExpect(jsonPath("$.totalElements").value(1L)); // Проверка: что всего элементов 1
        }
    }

    @Test
    @DisplayName("GET /api/v1/cards - дефолтные параметры пагинации")
    void getAllCardInfos_ShouldUseDefaultPagination() throws Exception {
        // given
        // Создаём объект Page с одной картой внутри
        Page<CardInfoDto> page = new PageImpl<>(of(cardInfoDto));

        //when
        // Когда кто-то вызовет cardInfoService.getAllCardInfos(0, 10), верни этот объект page
        // Ожидаем, что контроллер использует дефолтные значения: page=0, size=10
        when(cardInfoService.getAllCardInfos(0, 10)).thenReturn(page);

        // Внутри этого блока try — все вызовы SecurityUtils будут мокнутыми
        try (MockedStatic<SecurityUtils> mockedSecurityUtils = mockStatic(SecurityUtils.class)) {
            // Создаём мок аутентификации для администратора
            JwtAuthenticationToken authentication = createMockAuthentication("admin@tut.by", "ADMIN");
            // Когда метод SecurityUtils.isAdmin(authentication) вызвается — возвращай true
            mockedSecurityUtils.when(() -> SecurityUtils.isAdmin(authentication))
                    .thenReturn(true);

            // then
            // Выполняем GET запрос БЕЗ параметров пагинации — контроллер должен использовать дефолтные
            mockMvc.perform(get("/api/v1/cards")
                            .principal(authentication))
                    .andExpect(status().isOk()) // Проверка: что контроллер вернул 200 OK
                    .andExpect(jsonPath("$.content").isArray()); // Проверка: что content — это массив
        }
    }

    @Test
    @DisplayName("PUT /api/v1/cards/1 - успешное обновление карты")
    void updateCardInfo_ShouldReturnUpdatedCard() throws Exception {
        // given
        // Создаём DTO для запроса — данные, которые клиент отправляет для обновления карты
        CardInfoDto updateDto = new CardInfoDto();
        updateDto.setNumber("9999888877776666"); // 16 цифр без пробелов — новый номер карты
        updateDto.setHolder("Updated Holder"); // Новое имя держателя карты
        updateDto.setExpirationDate(LocalDate.of(2035, 6, 30)); // Новая дата истечения
        updateDto.setUserId(1L); // ID пользователя, которому принадлежит карта

        // Создаём DTO, которое будет возвращать сервис после обновления карты
        CardInfoDto updatedDto = new CardInfoDto();
        updatedDto.setId(1L); // ID карты остаётся прежним
        updatedDto.setNumber("9999888877776666");
        updatedDto.setHolder("Updated Holder");
        updatedDto.setExpirationDate(LocalDate.of(2035, 6, 30));
        updatedDto.setUserId(1L);

        // Создаём тестового пользователя, которого будет возвращать мок
        UserDto userDto = new UserDto();
        userDto.setId(1L);
        userDto.setEmail("test@example.com");

        //when
        // Когда кто-то вызовет cardInfoService.getCardInfoById(1L), верни cardInfoDto
        // (это нужно для проверки прав доступа — контроллер сначала получает карту)
        when(cardInfoService.getCardInfoById(1L)).thenReturn(cardInfoDto);
        // Когда кто-то вызовет userService.findUserById(1L), верни userDto
        when(userService.findUserById(1L)).thenReturn(userDto);
        // Когда кто-то вызовет cardInfoService.updateCardInfo(1L, любой CardInfoDto), верни updatedDto
        when(cardInfoService.updateCardInfo(eq(1L), any(CardInfoDto.class))).thenReturn(updatedDto);

        // Внутри этого блока try — все вызовы SecurityUtils будут мокнутыми
        try (MockedStatic<SecurityUtils> mockedSecurityUtils = mockStatic(SecurityUtils.class)) {
            // Создаём мок аутентификации — как будто пользователь уже залогинен
            JwtAuthenticationToken authentication = createMockAuthentication("test@example.com", "USER");
            // Когда метод SecurityUtils.hasAccess(...) вызвается с любым первым аргументом
            // и "test@example.com" во втором — возвращай true (разрешаем доступ)
            mockedSecurityUtils.when(() -> SecurityUtils.hasAccess(any(), eq("test@example.com")))
                    .thenReturn(true);

            // then
            // Выполняем PUT запрос на /api/v1/cards/1 с данными для обновления
            mockMvc.perform(put("/api/v1/cards/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateDto))
                            .principal(authentication))
                    .andExpect(status().isOk()) // Проверка: что контроллер вернул 200 OK
                    .andExpect(jsonPath("$.id").value(1L)) // Проверка: ID карты остался прежним
                    .andExpect(jsonPath("$.number").value("9999888877776666")) // Проверка: номер обновился
                    .andExpect(jsonPath("$.holder").value("Updated Holder")); // Проверка: имя держателя обновилось
        }
    }

    @Test
    @DisplayName("PUT /api/v1/cards/999 - карта не найдена для обновления")
    void updateCardInfo_ShouldReturnNotFound_WhenCardNotFound() throws Exception {
        // given
        // Создаём DTO для запроса — данные, которые клиент отправляет для обновления карты
        CardInfoDto updateDto = new CardInfoDto();
        updateDto.setNumber("9999888877776666"); // 16 цифр без пробелов
        updateDto.setHolder("Updated Holder");
        updateDto.setExpirationDate(LocalDate.of(2035, 6, 30));
        updateDto.setUserId(1L);

        //when
        // Когда кто-то вызовет cardInfoService.getCardInfoById(999L), выбрось исключение
        // Это имитирует ситуацию, когда карты с таким ID не существует в базе данных
        // Контроллер сначала получает карту для проверки прав доступа, поэтому исключение выбросится здесь
        when(cardInfoService.getCardInfoById(999L))
                .thenThrow(new CardInfoNotFoundException("Card with id 999 not found!"));

        // Внутри этого блока try — все вызовы SecurityUtils будут мокнутыми
        try (MockedStatic<SecurityUtils> mockedSecurityUtils = mockStatic(SecurityUtils.class)) {
            // Создаём мок аутентификации — как будто пользователь уже залогинен
            JwtAuthenticationToken authentication = createMockAuthentication("test@example.com", "USER");

            // then
            // Выполняем PUT запрос на несуществующую карту
            mockMvc.perform(put("/api/v1/cards/999")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateDto))
                            .principal(authentication))
                    .andExpect(status().isNotFound()); // Проверка: что контроллер вернул 404 Not Found
        }
    }

    @Test
    @DisplayName("PUT /api/v1/cards/1 - валидация: пустой holder")
    void updateCardInfo_ShouldReturnBadRequest_WhenHolderIsBlank() throws Exception {
        // given
        // Создаём DTO с невалидными данными — пустое имя держателя карты
        // Это проверяет, что Spring Validation (@NotBlank) работает корректно
        CardInfoDto invalidDto = new CardInfoDto();
        invalidDto.setNumber("1234567890123456"); // 16 цифр без пробелов — номер валидный
        invalidDto.setHolder(""); // пустой holder — это нарушает валидацию @NotBlank
        invalidDto.setExpirationDate(LocalDate.of(2030, 12, 31));

        //when
        // Внутри этого блока try — все вызовы SecurityUtils будут мокнутыми
        try (MockedStatic<SecurityUtils> mockedSecurityUtils = mockStatic(SecurityUtils.class)) {
            // Создаём мок аутентификации — как будто пользователь уже залогинен
            JwtAuthenticationToken authentication = createMockAuthentication("test@example.com", "USER");

            // then
            // Валидация произойдет до проверки доступа — Spring вернёт 400 Bad Request
            // ещё до того, как контроллер попытается проверить права доступа
            mockMvc.perform(put("/api/v1/cards/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidDto))
                            .principal(authentication))
                    .andExpect(status().isBadRequest()); // Проверка: что Spring Validation отклонил запрос
        }
    }

    @Test
    @DisplayName("DELETE /api/v1/cards/1 - успешное удаление карты")
    void deleteCardInfo_ShouldReturnNoContent() throws Exception {
        // given
        // Создаём тестового пользователя, которого будет возвращать мок
        UserDto userDto = new UserDto();
        userDto.setId(1L);
        userDto.setEmail("test@example.com");

        //when
        // Когда кто-то вызовет cardInfoService.getCardInfoById(1L), верни cardInfoDto
        // (это нужно для проверки прав доступа — контроллер сначала получает карту)
        when(cardInfoService.getCardInfoById(1L)).thenReturn(cardInfoDto);
        // Когда кто-то вызовет userService.findUserById(1L), верни userDto
        when(userService.findUserById(1L)).thenReturn(userDto);

        // Внутри этого блока try — все вызовы SecurityUtils будут мокнутыми
        try (MockedStatic<SecurityUtils> mockedSecurityUtils = mockStatic(SecurityUtils.class)) {
            // Создаём мок аутентификации — как будто администратор уже залогинен
            JwtAuthenticationToken authentication = createMockAuthentication("admin@example.com", "ADMIN");
            // Когда метод SecurityUtils.isAdmin(...) вызвается — возвращай true (пользователь является администратором)
            mockedSecurityUtils.when(() -> SecurityUtils.isAdmin(authentication))
                    .thenReturn(true);

            // then
            // Выполняем DELETE запрос на /api/v1/cards/1
            mockMvc.perform(delete("/api/v1/cards/1")
                            .principal(authentication))
                    .andExpect(status().isNoContent()); // Проверка: что контроллер вернул 204 No Content
            // (стандартный HTTP статус для успешного удаления без тела ответа)
        }
    }

    @Test
    @DisplayName("DELETE /api/v1/cards/999 - карта не найдена для удаления")
    void deleteCardInfo_ShouldReturnNotFound_WhenCardNotFound() throws Exception {
        // given & when
        // Когда кто-то вызовет cardInfoService.getCardInfoById(999L), выбрось исключение
        // Это имитирует ситуацию, когда карты с таким ID не существует в базе данных
        // Контроллер сначала получает карту для проверки прав доступа, поэтому исключение выбросится здесь
        when(cardInfoService.getCardInfoById(999L))
                .thenThrow(new CardInfoNotFoundException("Card with id 999 not found!"));

        // Внутри этого блока try — все вызовы SecurityUtils будут мокнутыми
        try (MockedStatic<SecurityUtils> mockedSecurityUtils = mockStatic(SecurityUtils.class)) {
            // Создаём мок аутентификации — как будто администратор уже залогинен
            JwtAuthenticationToken authentication = createMockAuthentication("admin@example.com", "ADMIN");
            // Когда метод SecurityUtils.isAdmin(...) вызвается — возвращай true (пользователь является администратором)
            mockedSecurityUtils.when(() -> SecurityUtils.isAdmin(authentication))
                    .thenReturn(true);

            // then
            // Выполняем DELETE запрос на несуществующую карту
            mockMvc.perform(delete("/api/v1/cards/999")
                            .principal(authentication))
                    .andExpect(status().isNotFound()); // Проверка: что контроллер вернул 404 Not Found
        }
    }
}

