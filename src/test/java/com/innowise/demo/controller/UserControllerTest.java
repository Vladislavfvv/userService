package com.innowise.demo.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innowise.demo.client.AuthServiceClient;
import com.innowise.demo.client.dto.TokenValidationResponse;
import com.innowise.demo.dto.PagedUserResponse;
import com.innowise.demo.dto.UserDto;
import com.innowise.demo.dto.UserUpdateRequest;
import com.innowise.demo.exception.UserNotFoundException;
import com.innowise.demo.service.UserService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private AuthServiceClient authServiceClient;

    private UserDto userDto;
    private static final String AUTH_HEADER = "Bearer test-token";

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @BeforeEach
    void setUp() {
        userDto = new UserDto();
        userDto.setId(1L);
        userDto.setName("Test");
        userDto.setSurname("User");
        userDto.setEmail("test@example.com");
        userDto.setBirthDate(LocalDate.of(1990, 1, 1));

        when(authServiceClient.validateAuthorizationHeader(anyString()))
                .thenReturn(Optional.of(new TokenValidationResponse(true, "test", "ROLE_USER")));
    }

    private static RequestPostProcessor adminAuth() {
        TestingAuthenticationToken token =
                new TestingAuthenticationToken("admin@example.com", "password", "ROLE_ADMIN");
        return withAuthentication(token);
    }

    private static RequestPostProcessor userAuth(String email) {
        TestingAuthenticationToken token =
                new TestingAuthenticationToken(email, "password", "ROLE_USER");
        return withAuthentication(token);
    }

    private static RequestPostProcessor withAuthentication(TestingAuthenticationToken token) {
        return request -> {
            token.setAuthenticated(true);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(token);
            request.setUserPrincipal(token);
            request.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
            return request;
        };
    }

    // Тесты для POST /api/v1/users удалены, так как этот эндпоинт был удален.
    // Пользователи теперь создаются только через регистрацию в authentication-service,
    // которая синхронизирует их через POST /api/v1/users/sync (защищенный внутренним API ключом).

    @Test
    @DisplayName("GET /api/v1/users/id?id=1 - успешное получение пользователя по ID")
    void getUserById_ShouldReturnUser() throws Exception {
        // given
        when(userService.findUserById(1L)).thenReturn(userDto);

        // when & then
        mockMvc.perform(get("/api/v1/users/id")
                        .with(adminAuth())
                        .param("id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("Test"))
                .andExpect(jsonPath("$.email").value("test@example.com"));
    }

    @Test
    @DisplayName("GET /api/v1/users/id?id=999 - пользователь не найден")
    void getUserById_ShouldReturnNotFound_WhenUserNotFound() throws Exception {
        // given
        when(userService.findUserById(999L))
                .thenThrow(new UserNotFoundException("User with id 999 not found!"));

        // when & then
        mockMvc.perform(get("/api/v1/users/id")
                        .with(adminAuth())
                        .param("id", "999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/users - успешное получение списка пользователей с пагинацией")
    void getUsers_ShouldReturnPagedResponse() throws Exception {
        // given
        PagedUserResponse pagedResponse = new PagedUserResponse(
                List.of(userDto),
                0,
                5,
                1L,
                1
        );
        when(userService.findAllUsers(0, 5)).thenReturn(pagedResponse);

        // when & then
        mockMvc.perform(get("/api/v1/users")
                        .with(adminAuth())
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1L))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/users - дефолтные параметры пагинации")
    void getUsers_ShouldUseDefaultPagination() throws Exception {
        // given
        PagedUserResponse pagedResponse = new PagedUserResponse(
                List.of(userDto),
                0,
                5,
                1L,
                1
        );
        when(userService.findAllUsers(0, 5)).thenReturn(pagedResponse);

        // when & then
        mockMvc.perform(get("/api/v1/users")
                        .with(adminAuth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(5));
    }

    @Test
    @DisplayName("GET /api/v1/users/email?email=test@example.com - успешное получение по email")
    void getUserByEmail_ShouldReturnUser() throws Exception {
        // given
        when(userService.getUserByEmail("test@example.com")).thenReturn(userDto);

        // when & then
        mockMvc.perform(get("/api/v1/users/email")
                        .with(userAuth("test@example.com"))
                        .header("Authorization", AUTH_HEADER)
                        .param("email", "test@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@example.com"));
    }

    @Test
    @DisplayName("GET /api/v1/users/email - пользователь не найден по email")
    void getUserByEmail_ShouldReturnNotFound_WhenUserNotFound() throws Exception {
        // given
        when(userService.getUserByEmail("notfound@example.com"))
                .thenThrow(new UserNotFoundException("User with email notfound@example.com not found!"));

        // when & then
        mockMvc.perform(get("/api/v1/users/email")
                        .with(userAuth("notfound@example.com"))
                        .header("Authorization", AUTH_HEADER)
                        .param("email", "notfound@example.com"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /api/v1/users/1 - успешное обновление пользователя")
    void updateUser_ShouldReturnUpdatedUser() throws Exception {
        // given
        UserUpdateRequest updateDto = new UserUpdateRequest();
        updateDto.setUserId(1L);
        updateDto.setName("Updated");
        updateDto.setSurname("User");
        updateDto.setEmail("updated@example.com");
        updateDto.setBirthDate(LocalDate.of(1995, 5, 5));

        UserDto updatedDto = new UserDto();
        updatedDto.setId(1L);
        updatedDto.setName("Updated");
        updatedDto.setSurname("User");
        updatedDto.setEmail("updated@example.com");
        updatedDto.setBirthDate(LocalDate.of(1995, 5, 5));

        when(userService.updateUser(eq(1L), any(UserUpdateRequest.class))).thenReturn(updatedDto);

        // when & then
        mockMvc.perform(put("/api/v1/users/1")
                        .with(adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("Updated"))
                .andExpect(jsonPath("$.email").value("updated@example.com"));
    }

    @Test
    @DisplayName("PUT /api/v1/users/999 - пользователь не найден для обновления")
    void updateUser_ShouldReturnNotFound_WhenUserNotFound() throws Exception {
        // given
        UserUpdateRequest updateDto = new UserUpdateRequest();
        updateDto.setUserId(999L);
        updateDto.setName("Updated");
        updateDto.setSurname("User");
        updateDto.setEmail("updated@example.com");
        updateDto.setBirthDate(LocalDate.of(1990, 1, 1));

        when(userService.updateUser(eq(999L), any(UserUpdateRequest.class)))
                .thenThrow(new UserNotFoundException("User with id 999 not found!"));

        // when & then
        mockMvc.perform(put("/api/v1/users/999")
                        .with(adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /api/v1/users/1 - валидация: некорректный email")
    void updateUser_ShouldReturnBadRequest_WhenEmailInvalid() throws Exception {
        // given
        UserUpdateRequest invalidDto = new UserUpdateRequest();
        invalidDto.setUserId(1L);
        invalidDto.setName("Test");
        invalidDto.setEmail("invalid-email"); // невалидный email

        // when & then
        mockMvc.perform(put("/api/v1/users/1")
                        .with(adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/v1/users/1 - успешное удаление пользователя")
    void deleteUser_ShouldReturnNoContent() throws Exception {
        // given
        // when(userService.deleteUser(1L)).thenReturn(null); // void метод

        // when & then
        mockMvc.perform(delete("/api/v1/users/1")
                        .with(adminAuth()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/v1/users/999 - пользователь не найден для удаления")
    void deleteUser_ShouldReturnNotFound_WhenUserNotFound() throws Exception {
        // given
        doThrow(new UserNotFoundException("User with id 999 not found!"))
                .when(userService).deleteUser(999L);

        // when & then
        mockMvc.perform(delete("/api/v1/users/999")
                        .with(adminAuth()))
                .andExpect(status().isNotFound());
    }
}

