package com.innowise.demo.controller;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innowise.demo.dto.CreateUserFromTokenRequest;
import com.innowise.demo.dto.PagedUserResponse;
import com.innowise.demo.dto.UpdateUserDto;
import com.innowise.demo.dto.UserDto;
import com.innowise.demo.exception.UserAlreadyExistsException;
import com.innowise.demo.exception.UserNotFoundException;
import com.innowise.demo.service.UserService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import com.innowise.demo.util.SecurityUtils;
import org.mockito.MockedStatic;
import java.time.Instant;
import java.util.Collections;

import static org.mockito.Mockito.mockStatic;

@WebMvcTest(controllers = UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private UserDto userDto;

    @BeforeEach
    void setUp() {
        userDto = new UserDto();
        userDto.setId(1L);
        userDto.setFirstName("Test");
        userDto.setLastName("User");
        userDto.setEmail("test@example.com");
        userDto.setBirthDate(LocalDate.of(1990, 1, 1));

        // Настраиваем мок JwtDecoder для возврата валидного JWT токена по умолчанию
        // Это позволяет тестам работать без необходимости настройки Security для каждого теста
        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "HS256")
                .claim("sub", "test@example.com")
                .claim("role", "USER")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        when(jwtDecoder.decode(anyString())).thenReturn(jwt);
    }

    /**
     * Вспомогательный метод для создания мок Authentication с JWT токеном.
     * Используется в тестах для имитации аутентифицированного пользователя.
     *
     * @param email email пользователя (будет добавлен в claim "sub")
     * @param role роль пользователя (USER или ADMIN)
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
    @DisplayName("POST /api/v1/users - успешное создание пользователя")
    void createUser_ShouldReturnCreatedUser() throws Exception {
        // given
        // Создаём DTO для запроса — данные, которые клиент отправляет на сервер
        UserDto requestDto = new UserDto();
        requestDto.setFirstName("Test");
        requestDto.setLastName("User");
        requestDto.setEmail("test@example.com");
        requestDto.setBirthDate(LocalDate.of(1990, 1, 1));

        //when
        // Когда кто-то вызовет userService.createUser(любой UserDto), верни userDto
        // (это объект, созданный в setUp() с данными пользователя)
        when(userService.createUser(any(UserDto.class))).thenReturn(userDto);

        // then
        // Выполняем POST запрос на /api/v1/users с данными для создания пользователя
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk()) // Проверка: что контроллер вернул 200 OK
                .andExpect(jsonPath("$.id").value(1L)) // Проверка: JSON содержит правильный ID
                .andExpect(jsonPath("$.firstName").value("Test")) // Проверка: имя совпадает
                .andExpect(jsonPath("$.email").value("test@example.com")); // Проверка: email совпадает
    }

    @Test
    @DisplayName("POST /api/v1/users - валидация: пустое имя")
    void createUser_ShouldReturnBadRequest_WhenNameIsBlank() throws Exception {
        // given
        // Создаём DTO с невалидными данными — пустое имя
        // Это проверяет, что Spring Validation (@NotBlank) работает корректно
        UserDto invalidDto = new UserDto();
        invalidDto.setFirstName(""); // пустое имя — это нарушает валидацию @NotBlank
        invalidDto.setLastName("User");
        invalidDto.setEmail("test@example.com");
        invalidDto.setBirthDate(LocalDate.of(1990, 1, 1));

        //when
        // Валидация произойдет до вызова сервиса — Spring вернёт 400 Bad Request
        // ещё до того, как контроллер попытается создать пользователя

        // then
        // Выполняем POST запрос с невалидными данными
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDto)))
                .andExpect(status().isBadRequest()); // Проверка: что Spring Validation отклонил запрос
    }

    @Test
    @DisplayName("POST /api/v1/users - пользователь уже существует")
    void createUser_ShouldReturnConflict_WhenUserAlreadyExists() throws Exception {
        // given
        // Создаём DTO для запроса — данные, которые клиент отправляет на сервер
        UserDto requestDto = new UserDto();
        requestDto.setFirstName("Test");
        requestDto.setLastName("User");
        requestDto.setEmail("test@example.com");
        requestDto.setBirthDate(LocalDate.of(1990, 1, 1));

        //when
        // Когда кто-то вызовет userService.createUser(любой UserDto), выбрось исключение
        // Это имитирует ситуацию, когда пользователь с таким email уже существует в базе данных
        when(userService.createUser(any(UserDto.class)))
                .thenThrow(new UserAlreadyExistsException("User with email test@example.com already exists"));

        // then
        // Выполняем POST запрос на создание пользователя, который уже существует
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isConflict()); // Проверка: что контроллер вернул 409 Conflict
    }

    @Test
    @DisplayName("GET /api/v1/users/id?id=1 - успешное получение пользователя по ID")
    void getUserById_ShouldReturnUser() throws Exception {
        // given
        // Когда кто-то вызовет userService.findUserById(1L), верни userDto
        // (это объект, созданный в setUp() с данными пользователя)
        when(userService.findUserById(1L)).thenReturn(userDto);

        //when
        // Внутри этого блока try — все вызовы SecurityUtils будут мокнутыми
        try (MockedStatic<SecurityUtils> mockedSecurityUtils = mockStatic(SecurityUtils.class)) {
            // Создаём мок аутентификации — как будто пользователь уже залогинен
            JwtAuthenticationToken authentication = createMockAuthentication("test@example.com", "USER");
            // Когда метод SecurityUtils.hasAccess(...) вызвается с любым первым аргументом
            // и "test@example.com" во втором — возвращай true (разрешаем доступ)
            mockedSecurityUtils.when(() -> SecurityUtils.hasAccess(any(), eq("test@example.com")))
                    .thenReturn(true);

            // then
            // Выполняем GET запрос на /api/v1/users/id с параметром id=1 и мокнутой аутентификацией
            mockMvc.perform(get("/api/v1/users/id")
                            .param("id", "1")
                            .principal(authentication))
                    .andExpect(status().isOk()) // Проверка: что контроллер вернул 200 OK
                    .andExpect(jsonPath("$.id").value(1L)) // Проверка: JSON содержит правильный ID
                    .andExpect(jsonPath("$.firstName").value("Test")) // Проверка: имя совпадает
                    .andExpect(jsonPath("$.email").value("test@example.com")); // Проверка: email совпадает
        }
    }

    @Test
    @DisplayName("GET /api/v1/users/id?id=999 - пользователь не найден")
    void getUserById_ShouldReturnNotFound_WhenUserNotFound() throws Exception {
        // given & when
        // Когда кто-то вызовет userService.findUserById(999L), выбрось исключение
        // Это имитирует ситуацию, когда пользователя с таким ID не существует в базе данных
        when(userService.findUserById(999L))
                .thenThrow(new UserNotFoundException("User with id 999 not found!"));

        // Внутри этого блока try — все вызовы SecurityUtils будут мокнутыми
        try (MockedStatic<SecurityUtils> mockedSecurityUtils = mockStatic(SecurityUtils.class)) {
            // Создаём мок аутентификации — как будто пользователь уже залогинен
            JwtAuthenticationToken authentication = createMockAuthentication("test@example.com", "USER");
            // Когда метод SecurityUtils.hasAccess(...) вызвается с любыми аргументами — возвращай true
            mockedSecurityUtils.when(() -> SecurityUtils.hasAccess(any(), any()))
                    .thenReturn(true);

            // then
            // Выполняем GET запрос на несуществующего пользователя
            mockMvc.perform(get("/api/v1/users/id")
                            .param("id", "999")
                            .principal(authentication))
                    .andExpect(status().isNotFound()); // Проверка: что контроллер вернул 404 Not Found
        }
    }

    @Test
    @DisplayName("GET /api/v1/users - успешное получение списка пользователей с пагинацией")
    void getUsers_ShouldReturnPagedResponse() throws Exception {
        // given
        // Создаём объект PagedUserResponse с одним пользователем внутри (userDto из setUp())
        PagedUserResponse pagedResponse = new PagedUserResponse(
                List.of(userDto),
                0,
                5,
                1L,
                1
        );

        //when
        // Когда кто-то вызовет userService.findAllUsers(0, 5), верни этот объект pagedResponse
        // (страница 0, размер страницы 5)
        when(userService.findAllUsers(0, 5)).thenReturn(pagedResponse);

        // then
        // Выполняем GET запрос на /api/v1/users с параметрами пагинации
        // Security отключен в тестах (@AutoConfigureMockMvc(addFilters = false)),
        // поэтому проверка доступа на уровне Spring Security не выполняется
        mockMvc.perform(get("/api/v1/users")
                        .param("page", "0") // Параметр: номер страницы (начинается с 0)
                        .param("size", "5")) // Параметр: размер страницы (количество элементов)
                .andExpect(status().isOk()) // Проверка: что контроллер вернул 200 OK
                .andExpect(jsonPath("$.content").isArray()) // Проверка: что content — это массив
                .andExpect(jsonPath("$.content.length()").value(1)) // Проверка: что в массиве 1 элемент
                .andExpect(jsonPath("$.totalElements").value(1L)) // Проверка: что всего элементов 1
                .andExpect(jsonPath("$.totalPages").value(1)); // Проверка: что всего страниц 1
    }

    @Test
    @DisplayName("GET /api/v1/users - дефолтные параметры пагинации")
    void getUsers_ShouldUseDefaultPagination() throws Exception {
        // given
        // Создаём объект PagedUserResponse с одним пользователем внутри
        PagedUserResponse pagedResponse = new PagedUserResponse(
                List.of(userDto),
                0,
                5,
                1L,
                1
        );

        //when
        // Когда кто-то вызовет userService.findAllUsers(0, 5), верни этот объект pagedResponse
        // Ожидаем, что контроллер использует дефолтные значения: page=0, size=5
        when(userService.findAllUsers(0, 5)).thenReturn(pagedResponse);

        // then
        // Выполняем GET запрос БЕЗ параметров пагинации — контроллер должен использовать дефолтные
        // Security отключен в тестах, поэтому проверка доступа на уровне Spring Security не выполняется
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk()) // Проверка: что контроллер вернул 200 OK
                .andExpect(jsonPath("$.page").value(0)) // Проверка: что используется страница 0
                .andExpect(jsonPath("$.size").value(5)); // Проверка: что размер страницы 5
    }

    @Test
    @DisplayName("GET /api/v1/users/self - успешное получение своих данных")
    void getSelfUser_ShouldReturnUser() throws Exception {
        // given
        String email = "test@example.com";
        // Когда кто-то вызовет userService.getUserByEmail(email), верни userDto
        // (это объект, созданный в setUp() с данными пользователя)
        when(userService.getUserByEmail(email)).thenReturn(userDto);

        //when
        // Внутри этого блока try — все вызовы SecurityUtils будут мокнутыми
        try (MockedStatic<SecurityUtils> mockedSecurityUtils = mockStatic(SecurityUtils.class)) {
            // Создаём мок аутентификации — как будто пользователь уже залогинен
            JwtAuthenticationToken authentication = createMockAuthentication(email, "USER");
            // Когда метод SecurityUtils.getEmailFromToken(authentication) вызвается — возвращай email
            // Это извлекает email из JWT токена для получения данных текущего пользователя
            mockedSecurityUtils.when(() -> SecurityUtils.getEmailFromToken(authentication))
                    .thenReturn(email);

            // then
            // Выполняем GET запрос на /api/v1/users/self с мокнутой аутентификацией
            mockMvc.perform(get("/api/v1/users/self")
                            .principal(authentication))
                    .andExpect(status().isOk()) // Проверка: что контроллер вернул 200 OK
                    .andExpect(jsonPath("$.id").value(1L)) // Проверка: JSON содержит правильный ID
                    .andExpect(jsonPath("$.email").value("test@example.com")) // Проверка: email совпадает
                    .andExpect(jsonPath("$.firstName").value("Test")); // Проверка: имя совпадает
        }
    }

    @Test
    @DisplayName("GET /api/v1/users/self - пользователь не найден")
    void getSelfUser_ShouldReturnNotFound_WhenUserNotFound() throws Exception {
        // given & when
        String email = "notfound@example.com";
        // Когда кто-то вызовет userService.getUserByEmail(email), выбрось исключение
        // Это имитирует ситуацию, когда пользователя с таким email не существует в базе данных
        when(userService.getUserByEmail(email))
                .thenThrow(new UserNotFoundException("User with email notfound@example.com not found!"));

        // Внутри этого блока try — все вызовы SecurityUtils будут мокнутыми
        try (MockedStatic<SecurityUtils> mockedSecurityUtils = mockStatic(SecurityUtils.class)) {
            // Создаём мок аутентификации — как будто пользователь уже залогинен
            JwtAuthenticationToken authentication = createMockAuthentication(email, "USER");
            // Когда метод SecurityUtils.getEmailFromToken(authentication) вызвается — возвращай email
            mockedSecurityUtils.when(() -> SecurityUtils.getEmailFromToken(authentication))
                    .thenReturn(email);

            // then
            // Выполняем GET запрос на /api/v1/users/self для несуществующего пользователя
            mockMvc.perform(get("/api/v1/users/self")
                            .principal(authentication))
                    .andExpect(status().isNotFound()); // Проверка: что контроллер вернул 404 Not Found
        }
    }

    @Test
    @DisplayName("GET /api/v1/users/self - токен без email")
    void getSelfUser_ShouldReturnForbidden_WhenTokenHasNoEmail() throws Exception {
        // given
        //when
        // Внутри этого блока try — все вызовы SecurityUtils будут мокнутыми
        try (MockedStatic<SecurityUtils> mockedSecurityUtils = mockStatic(SecurityUtils.class)) {
            // Создаём токен без email (без claim "sub") — это невалидный токен для этого endpoint
            Jwt jwt = Jwt.withTokenValue("mock-token")
                    .header("alg", "HS256")
                    .claim("role", "USER")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();

            JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                    jwt,
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
            );

            // Когда метод SecurityUtils.getEmailFromToken(authentication) вызвается — выбрось исключение
            // Это имитирует ситуацию, когда в токене отсутствует claim "sub" с email
            mockedSecurityUtils.when(() -> SecurityUtils.getEmailFromToken(authentication))
                    .thenThrow(new IllegalStateException("Email (sub claim) not found in JWT token"));

            // then
            // Выполняем GET запрос на /api/v1/users/self с токеном без email
            mockMvc.perform(get("/api/v1/users/self")
                            .principal(authentication))
                    .andExpect(status().isForbidden()) // Проверка: что контроллер вернул 403 Forbidden
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED")) // Проверка: код ошибки ACCESS_DENIED
                    .andExpect(jsonPath("$.message").value("Access denied: You can only update your own information.")); // Проверка: сообщение об ошибке
        }
    }

    @Test
    @DisplayName("POST /api/v1/users/createUser - успешное создание пользователя из токена")
    void createUserFromToken_ShouldReturnCreatedUser() throws Exception {
        // given
        // Создаём DTO для запроса — данные, которые клиент отправляет на сервер
        CreateUserFromTokenRequest requestDto = new CreateUserFromTokenRequest();
        requestDto.setFirstName("Test");
        requestDto.setLastName("User");
        requestDto.setBirthDate(LocalDate.of(1990, 1, 1));

        String email = "test@example.com";

        //when
        // Когда кто-то вызовет userService.createUserFromToken(email, requestDto), верни userDto
        // (это объект, созданный в setUp() с данными пользователя)
        when(userService.createUserFromToken(eq(email), any(CreateUserFromTokenRequest.class))).thenReturn(userDto);

        // Внутри этого блока try — все вызовы SecurityUtils будут мокнутыми
        try (MockedStatic<SecurityUtils> mockedSecurityUtils = mockStatic(SecurityUtils.class)) {
            // Создаём мок аутентификации — как будто пользователь уже залогинен
            JwtAuthenticationToken authentication = createMockAuthentication(email, "USER");
            // Когда метод SecurityUtils.getEmailFromToken(authentication) вызвается — возвращай email
            // Это извлекает email из JWT токена для создания пользователя
            mockedSecurityUtils.when(() -> SecurityUtils.getEmailFromToken(authentication))
                    .thenReturn(email);

            // then
            // Выполняем POST запрос на /api/v1/users/createUser с данными для создания пользователя
            mockMvc.perform(post("/api/v1/users/createUser")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestDto))
                            .principal(authentication))
                    .andExpect(status().isOk()) // Проверка: что контроллер вернул 200 OK
                    .andExpect(jsonPath("$.id").value(1L)) // Проверка: JSON содержит правильный ID
                    .andExpect(jsonPath("$.firstName").value("Test")) // Проверка: имя совпадает
                    .andExpect(jsonPath("$.email").value("test@example.com")); // Проверка: email совпадает
        }
    }

    @Test
    @DisplayName("POST /api/v1/users/createUser - валидация: пустое имя")
    void createUserFromToken_ShouldReturnBadRequest_WhenNameIsBlank() throws Exception {
        // given
        // Создаём DTO с невалидными данными — пустое имя
        // Это проверяет, что Spring Validation (@NotBlank) работает корректно
        CreateUserFromTokenRequest invalidDto = new CreateUserFromTokenRequest();
        invalidDto.setFirstName(""); // пустое имя — это нарушает валидацию @NotBlank
        invalidDto.setLastName("User");
        invalidDto.setBirthDate(LocalDate.of(1990, 1, 1));

        //when
        // Валидация произойдет до вызова сервиса — Spring вернёт 400 Bad Request
        // ещё до того, как контроллер попытается создать пользователя

        // then
        // Выполняем POST запрос с невалидными данными
        mockMvc.perform(post("/api/v1/users/createUser")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDto)))
                .andExpect(status().isBadRequest()); // Проверка: что Spring Validation отклонил запрос
    }

    @Test
    @DisplayName("POST /api/v1/users/createUser - пользователь уже существует")
    void createUserFromToken_ShouldReturnConflict_WhenUserAlreadyExists() throws Exception {
        // given
        // Создаём DTO для запроса — данные, которые клиент отправляет на сервер
        CreateUserFromTokenRequest requestDto = new CreateUserFromTokenRequest();
        requestDto.setFirstName("Test");
        requestDto.setLastName("User");
        requestDto.setBirthDate(LocalDate.of(1990, 1, 1));

        String email = "test@example.com";

        //when
        // Когда кто-то вызовет userService.createUserFromToken(email, requestDto), выбрось исключение
        // Это имитирует ситуацию, когда пользователь с таким email уже существует в базе данных
        when(userService.createUserFromToken(eq(email), any(CreateUserFromTokenRequest.class)))
                .thenThrow(new UserAlreadyExistsException("User with email test@example.com already exists"));

        // Внутри этого блока try — все вызовы SecurityUtils будут мокнутыми
        try (MockedStatic<SecurityUtils> mockedSecurityUtils = mockStatic(SecurityUtils.class)) {
            // Создаём мок аутентификации — как будто пользователь уже залогинен
            JwtAuthenticationToken authentication = createMockAuthentication(email, "USER");
            // Когда метод SecurityUtils.getEmailFromToken(authentication) вызвается — возвращай email
            mockedSecurityUtils.when(() -> SecurityUtils.getEmailFromToken(authentication))
                    .thenReturn(email);

            // then
            // Выполняем POST запрос на создание пользователя, который уже существует
            mockMvc.perform(post("/api/v1/users/createUser")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestDto))
                            .principal(authentication))
                    .andExpect(status().isConflict()); // Проверка: что контроллер вернул 409 Conflict
        }
    }

    @Test
    @DisplayName("POST /api/v1/users/createUser - токен без email")
    void createUserFromToken_ShouldReturnForbidden_WhenTokenHasNoEmail() throws Exception {
        // given
        // Создаём DTO для запроса
        CreateUserFromTokenRequest requestDto = new CreateUserFromTokenRequest();
        requestDto.setFirstName("Test");
        requestDto.setLastName("User");
        requestDto.setBirthDate(LocalDate.of(1990, 1, 1));

        //when
        // Внутри этого блока try — все вызовы SecurityUtils будут мокнутыми
        try (MockedStatic<SecurityUtils> mockedSecurityUtils = mockStatic(SecurityUtils.class)) {
            // Создаём токен без email (без claim "sub") — это невалидный токен для этого endpoint
            Jwt jwt = Jwt.withTokenValue("mock-token")
                    .header("alg", "HS256")
                    .claim("role", "USER")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();

            JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                    jwt,
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
            );

            // Когда метод SecurityUtils.getEmailFromToken(authentication) вызвается — выбрось исключение
            // Это имитирует ситуацию, когда в токене отсутствует claim "sub" с email
            mockedSecurityUtils.when(() -> SecurityUtils.getEmailFromToken(authentication))
                    .thenThrow(new IllegalStateException("Email (sub claim) not found in JWT token"));

            // then
            // Выполняем POST запрос на /api/v1/users/createUser с токеном без email
            mockMvc.perform(post("/api/v1/users/createUser")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestDto))
                            .principal(authentication))
                    .andExpect(status().isForbidden()) // Проверка: что контроллер вернул 403 Forbidden
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED")) // Проверка: код ошибки ACCESS_DENIED
                    .andExpect(jsonPath("$.message").value("Access denied: You can only update your own information.")); // Проверка: сообщение об ошибке
        }
    }

    @Test
    @DisplayName("GET /api/v1/users/email?email=test@example.com - успешное получение по email")
    void getUserByEmail_ShouldReturnUser() throws Exception {
        // given
        // Когда кто-то вызовет userService.getUserByEmail("test@example.com"), верни userDto
        // (это объект, созданный в setUp() с данными пользователя)
        when(userService.getUserByEmail("test@example.com")).thenReturn(userDto);

        //when
        // Внутри этого блока try — все вызовы SecurityUtils будут мокнутыми
        try (MockedStatic<SecurityUtils> mockedSecurityUtils = mockStatic(SecurityUtils.class)) {
            // Создаём мок аутентификации — как будто пользователь уже залогинен
            JwtAuthenticationToken authentication = createMockAuthentication("test@example.com", "USER");
            // Когда метод SecurityUtils.hasAccess(...) вызвается с любым первым аргументом
            // и "test@example.com" во втором — возвращай true (разрешаем доступ)
            mockedSecurityUtils.when(() -> SecurityUtils.hasAccess(any(), eq("test@example.com")))
                    .thenReturn(true);

            // then
            // Выполняем GET запрос на /api/v1/users/email с параметром email и мокнутой аутентификацией
            mockMvc.perform(get("/api/v1/users/email")
                            .param("email", "test@example.com")
                            .principal(authentication))
                    .andExpect(status().isOk()) // Проверка: что контроллер вернул 200 OK
                    .andExpect(jsonPath("$.email").value("test@example.com")); // Проверка: email совпадает
        }
    }

    @Test
    @DisplayName("GET /api/v1/users/email - пользователь не найден по email")
    void getUserByEmail_ShouldReturnNotFound_WhenUserNotFound() throws Exception {
        // given & when
        // Когда кто-то вызовет userService.getUserByEmail("notfound@example.com"), выбрось исключение
        // Это имитирует ситуацию, когда пользователя с таким email не существует в базе данных
        when(userService.getUserByEmail("notfound@example.com"))
                .thenThrow(new UserNotFoundException("User with email notfound@example.com not found!"));

        // Внутри этого блока try — все вызовы SecurityUtils будут мокнутыми
        try (MockedStatic<SecurityUtils> mockedSecurityUtils = mockStatic(SecurityUtils.class)) {
            // Создаём мок аутентификации — как будто пользователь уже залогинен
            JwtAuthenticationToken authentication = createMockAuthentication("test@example.com", "USER");
            // Когда метод SecurityUtils.hasAccess(...) вызвается с любыми аргументами — возвращай true
            mockedSecurityUtils.when(() -> SecurityUtils.hasAccess(any(), any()))
                    .thenReturn(true);

            // then
            // Выполняем GET запрос на несуществующего пользователя по email
            mockMvc.perform(get("/api/v1/users/email")
                            .param("email", "notfound@example.com")
                            .principal(authentication))
                    .andExpect(status().isNotFound()); // Проверка: что контроллер вернул 404 Not Found
        }
    }

    @Test
    @DisplayName("PUT /api/v1/users/1 - успешное обновление пользователя")
    void updateUser_ShouldReturnUpdatedUser() throws Exception {
        // given
        // Создаём DTO для запроса — данные, которые клиент отправляет для обновления пользователя
        UpdateUserDto updateDto = new UpdateUserDto();
        updateDto.setFirstName("Updated");
        updateDto.setLastName("User");
        updateDto.setBirthDate(LocalDate.of(1995, 5, 5));

        // Создаём DTO, которое будет возвращать сервис после обновления пользователя
        UserDto updatedDto = new UserDto();
        updatedDto.setId(1L); // ID пользователя остаётся прежним
        updatedDto.setFirstName("Updated");
        updatedDto.setLastName("User");
        updatedDto.setEmail("updated@example.com");
        updatedDto.setBirthDate(LocalDate.of(1995, 5, 5));

        //when
        // Когда кто-то вызовет userService.updateUser(1L, любой UpdateUserDto, любой String), верни updatedDto
        when(userService.updateUser(eq(1L), any(UpdateUserDto.class), any(String.class))).thenReturn(updatedDto);

        // Внутри этого блока try — все вызовы SecurityUtils будут мокнутыми
        try (MockedStatic<SecurityUtils> mockedSecurityUtils = mockStatic(SecurityUtils.class)) {
            String email = "updated@example.com";
            // Создаём мок аутентификации — как будто пользователь уже залогинен
            JwtAuthenticationToken authentication = createMockAuthentication(email, "USER");
            // Когда метод SecurityUtils.getEmailFromToken(authentication) вызвается — возвращай email
            // Это извлекает email из JWT токена для проверки прав доступа
            mockedSecurityUtils.when(() -> SecurityUtils.getEmailFromToken(authentication))
                    .thenReturn(email);

            // then
            // Выполняем PUT запрос на /api/v1/users/1 с данными для обновления
            mockMvc.perform(put("/api/v1/users/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateDto)) //превращение Java-объекта updateDto в JSON-строку, чтобы сервер получил данные для обновления пользователя
                            .principal(authentication))//задаёт информацию о правах текущего аутентифицированного пользователя для запроса
                    .andExpect(status().isOk()) // Проверка: что контроллер вернул 200 OK
                    .andExpect(jsonPath("$.id").value(1L)) // Проверка: ID пользователя остался прежним
                    .andExpect(jsonPath("$.firstName").value("Updated")) // Проверка: имя обновилось
                    .andExpect(jsonPath("$.email").value("updated@example.com")); // Проверка: email совпадает
        }
    }

    @Test
    @DisplayName("PUT /api/v1/users/999 - пользователь не найден для обновления")
    void updateUser_ShouldReturnNotFound_WhenUserNotFound() throws Exception {
        // given
        // Создаём DTO для запроса — данные, которые клиент отправляет для обновления пользователя
        UpdateUserDto updateDto = new UpdateUserDto();
        updateDto.setFirstName("Updated");
        updateDto.setLastName("User");
        updateDto.setBirthDate(LocalDate.of(1990, 1, 1));

        //when
        // Когда кто-то вызовет userService.updateUser(999L, любой UpdateUserDto, любой String), выбрось исключение
        // Это имитирует ситуацию, когда пользователя с таким ID не существует в базе данных
        when(userService.updateUser(eq(999L), any(UpdateUserDto.class), any(String.class)))
                .thenThrow(new UserNotFoundException("User with id 999 not found!"));

        // Внутри этого блока try — все вызовы SecurityUtils будут мокнутыми
        try (MockedStatic<SecurityUtils> mockedSecurityUtils = mockStatic(SecurityUtils.class)) {
            String email = "test@example.com";
            // Создаём мок аутентификации — как будто пользователь уже залогинен
            JwtAuthenticationToken authentication = createMockAuthentication(email, "USER");
            // Когда метод SecurityUtils.getEmailFromToken(authentication) вызвается — возвращай email
            mockedSecurityUtils.when(() -> SecurityUtils.getEmailFromToken(authentication))
                    .thenReturn(email);

            // then
            // Выполняем PUT запрос на несуществующего пользователя
            mockMvc.perform(put("/api/v1/users/999")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateDto))
                            .principal(authentication))
                    .andExpect(status().isNotFound()); // Проверка: что контроллер вернул 404 Not Found
        }
    }

    @Test
    @DisplayName("PUT /api/v1/users/1 - доступ запрещен при неверном email")
    void updateUser_ShouldReturnForbidden_WhenAccessDenied() throws Exception {
        // given
        // Создаём DTO для запроса — данные, которые клиент отправляет для обновления пользователя
        UpdateUserDto updateDto = new UpdateUserDto();
        updateDto.setFirstName("Updated");

        //when
        // Когда кто-то вызовет userService.updateUser(1L, любой UpdateUserDto, любой String), выбрось исключение
        // Это имитирует ситуацию, когда пользователь пытается обновить данные другого пользователя
        // (email из токена не совпадает с email пользователя, которого пытаются обновить)
        when(userService.updateUser(eq(1L), any(UpdateUserDto.class), any(String.class)))
                .thenThrow(new org.springframework.security.access.AccessDeniedException(
                    "Access denied: You can only update your own information. Please, change Id in url."));

        // Внутри этого блока try — все вызовы SecurityUtils будут мокнутыми
        try (MockedStatic<SecurityUtils> mockedSecurityUtils = mockStatic(SecurityUtils.class)) {
            String email = "test@example.com";
            // Создаём мок аутентификации — как будто пользователь уже залогинен
            JwtAuthenticationToken authentication = createMockAuthentication(email, "USER");
            // Когда метод SecurityUtils.getEmailFromToken(authentication) вызвается — возвращай email
            mockedSecurityUtils.when(() -> SecurityUtils.getEmailFromToken(authentication))
                    .thenReturn(email);

            // then
            // Выполняем PUT запрос на обновление пользователя с неверным доступом
            mockMvc.perform(put("/api/v1/users/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateDto))
                            .principal(authentication))
                    .andExpect(status().isForbidden()) // Проверка: что контроллер вернул 403 Forbidden
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED")) // Проверка: код ошибки ACCESS_DENIED
                    .andExpect(jsonPath("$.message").exists()); // Проверка: что сообщение об ошибке присутствует
        }
    }

    // Тест с валидацией email удален, так как email больше не передается в UpdateUserDto
    // Email берется из токена и не может быть изменен через этот endpoint

    @Test
    @DisplayName("DELETE /api/v1/users/1 - успешное удаление пользователя ADMIN")
    void deleteUser_ShouldReturnNoContent_WhenAdmin() throws Exception {
        // given
        // Когда кто-то вызовет userService.findUserById(1L), верни userDto
        // (это нужно для проверки прав доступа — контроллер сначала получает пользователя)
        when(userService.findUserById(1L)).thenReturn(userDto);

        //when
        // Внутри этого блока try — все вызовы SecurityUtils будут мокнутыми
        try (MockedStatic<SecurityUtils> mockedSecurityUtils = mockStatic(SecurityUtils.class)) {
            // Создаём мок аутентификации — как будто администратор уже залогинен
            JwtAuthenticationToken authentication = createMockAuthentication("admin@example.com", "ADMIN");
            // Когда метод SecurityUtils.isAdmin(...) вызвается — возвращай true (пользователь является администратором)
            mockedSecurityUtils.when(() -> SecurityUtils.isAdmin(authentication))
                    .thenReturn(true);

            // then
            // Выполняем DELETE запрос на /api/v1/users/1
            mockMvc.perform(delete("/api/v1/users/1")
                            .principal(authentication))
                    .andExpect(status().isNoContent()); // Проверка: что контроллер вернул 204 No Content
            // (стандартный HTTP статус для успешного удаления без тела ответа)
        }
    }

    @Test
    @DisplayName("DELETE /api/v1/users/1 - доступ запрещен для USER")
    void deleteUser_ShouldReturnForbidden_WhenUser() throws Exception {
        // given
        // Когда кто-то вызовет userService.findUserById(1L), верни userDto
        when(userService.findUserById(1L)).thenReturn(userDto);

        //when
        // Внутри этого блока try — все вызовы SecurityUtils будут мокнутыми
        try (MockedStatic<SecurityUtils> mockedSecurityUtils = mockStatic(SecurityUtils.class)) {
            // Создаём мок аутентификации — как будто обычный пользователь уже залогинен
            JwtAuthenticationToken authentication = createMockAuthentication("test@example.com", "USER");
            // Когда метод SecurityUtils.isAdmin(...) вызвается — возвращай false (пользователь НЕ является администратором)
            mockedSecurityUtils.when(() -> SecurityUtils.isAdmin(authentication))
                    .thenReturn(false);

            // then
            // Выполняем DELETE запрос на /api/v1/users/1 от обычного пользователя
            mockMvc.perform(delete("/api/v1/users/1")
                            .principal(authentication))
                    .andExpect(status().isForbidden()) // Проверка: что контроллер вернул 403 Forbidden
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED")) // Проверка: код ошибки ACCESS_DENIED
                    .andExpect(jsonPath("$.message").value("Access denied: Only administrators can delete users")); // Проверка: сообщение об ошибке
        }
    }

    @Test
    @DisplayName("DELETE /api/v1/users/999 - пользователь не найден для удаления")
    void deleteUser_ShouldReturnNotFound_WhenUserNotFound() throws Exception {
        // given & when
        // Когда кто-то вызовет userService.findUserById(999L), выбрось исключение
        // Это имитирует ситуацию, когда пользователя с таким ID не существует в базе данных
        // Контроллер сначала получает пользователя для проверки прав доступа, поэтому исключение выбросится здесь
        when(userService.findUserById(999L))
                .thenThrow(new UserNotFoundException("User with id 999 not found!"));

        // Внутри этого блока try — все вызовы SecurityUtils будут мокнутыми
        try (MockedStatic<SecurityUtils> mockedSecurityUtils = mockStatic(SecurityUtils.class)) {
            // Создаём мок аутентификации — как будто администратор уже залогинен
            JwtAuthenticationToken authentication = createMockAuthentication("admin@example.com", "ADMIN");
            // Когда метод SecurityUtils.isAdmin(...) вызвается — возвращай true (пользователь является администратором)
            mockedSecurityUtils.when(() -> SecurityUtils.isAdmin(authentication))
                    .thenReturn(true);

            // then
            // Выполняем DELETE запрос на несуществующего пользователя
            mockMvc.perform(delete("/api/v1/users/999")
                            .principal(authentication))
                    .andExpect(status().isNotFound()); // Проверка: что контроллер вернул 404 Not Found
        }
    }
}

