package com.innowise.demo.controller;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innowise.demo.dto.PagedUserResponse;
import com.innowise.demo.dto.UpdateUserDto;
import com.innowise.demo.dto.UserDto;
import com.innowise.demo.exception.UserAlreadyExistsException;
import com.innowise.demo.exception.UserNotFoundException;
import com.innowise.demo.service.UserService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
import java.time.Instant;
import java.util.Collections;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    private UserDto userDto;

    @BeforeEach
    void setUp() {
        userDto = new UserDto();
        userDto.setId(1L);
        userDto.setName("Test");
        userDto.setSurname("User");
        userDto.setEmail("test@example.com");
        userDto.setBirthDate(LocalDate.of(1990, 1, 1));
    }

    @Test
    @DisplayName("POST /api/v1/users - успешное создание пользователя")
    void createUser_ShouldReturnCreatedUser() throws Exception {
        // given
        UserDto requestDto = new UserDto();
        requestDto.setName("Test");
        requestDto.setSurname("User");
        requestDto.setEmail("test@example.com");
        requestDto.setBirthDate(LocalDate.of(1990, 1, 1));

        when(userService.createUser(any(UserDto.class))).thenReturn(userDto);

        // when & then
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("Test"))
                .andExpect(jsonPath("$.email").value("test@example.com"));
    }

    @Test
    @DisplayName("POST /api/v1/users - валидация: пустое имя")
    void createUser_ShouldReturnBadRequest_WhenNameIsBlank() throws Exception {
        // given
        UserDto invalidDto = new UserDto();
        invalidDto.setName(""); // пустое имя
        invalidDto.setSurname("User");
        invalidDto.setEmail("test@example.com");
        invalidDto.setBirthDate(LocalDate.of(1990, 1, 1));

        // when & then
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/users - пользователь уже существует")
    void createUser_ShouldReturnConflict_WhenUserAlreadyExists() throws Exception {
        // given
        UserDto requestDto = new UserDto();
        requestDto.setName("Test");
        requestDto.setSurname("User");
        requestDto.setEmail("test@example.com");
        requestDto.setBirthDate(LocalDate.of(1990, 1, 1));

        when(userService.createUser(any(UserDto.class)))
                .thenThrow(new UserAlreadyExistsException("User with email test@example.com already exists"));

        // when & then
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET /api/v1/users/id?id=1 - успешное получение пользователя по ID")
    void getUserById_ShouldReturnUser() throws Exception {
        // given
        when(userService.findUserById(1L)).thenReturn(userDto);

        // when & then
        mockMvc.perform(get("/api/v1/users/id")
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
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(5));
    }

    @Test
    @DisplayName("GET /api/v1/users/self - успешное получение своих данных")
    void getSelfUser_ShouldReturnUser() throws Exception {
        // given
        String email = "test@example.com";
        when(userService.getUserByEmail(email)).thenReturn(userDto);

        // Создаем мок JWT токена с email в subject
        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "HS256")
                .claim("sub", email)
                .claim("role", "USER")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                jwt,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );

        // when & then
        mockMvc.perform(get("/api/v1/users/self")
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.name").value("Test"));
    }

    @Test
    @DisplayName("GET /api/v1/users/self - пользователь не найден")
    void getSelfUser_ShouldReturnNotFound_WhenUserNotFound() throws Exception {
        // given
        String email = "notfound@example.com";
        when(userService.getUserByEmail(email))
                .thenThrow(new UserNotFoundException("User with email notfound@example.com not found!"));

        // Создаем мок JWT токена с email в subject
        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "HS256")
                .claim("sub", email)
                .claim("role", "USER")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                jwt,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );

        // when & then
        mockMvc.perform(get("/api/v1/users/self")
                        .principal(authentication))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/users/self - токен без email")
    void getSelfUser_ShouldReturnForbidden_WhenTokenHasNoEmail() throws Exception {
        // given - JWT токен без subject (email)
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

        // when & then
        mockMvc.perform(get("/api/v1/users/self")
                        .principal(authentication))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/users/email?email=test@example.com - успешное получение по email")
    void getUserByEmail_ShouldReturnUser() throws Exception {
        // given
        when(userService.getUserByEmail("test@example.com")).thenReturn(userDto);

        // when & then
        mockMvc.perform(get("/api/v1/users/email")
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
                        .param("email", "notfound@example.com"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /api/v1/users/1 - успешное обновление пользователя")
    void updateUser_ShouldReturnUpdatedUser() throws Exception {
        // given
        UpdateUserDto updateDto = new UpdateUserDto();
        updateDto.setName("Updated");
        updateDto.setSurname("User");
        updateDto.setBirthDate(LocalDate.of(1995, 5, 5));

        UserDto updatedDto = new UserDto();
        updatedDto.setId(1L);
        updatedDto.setName("Updated");
        updatedDto.setSurname("User");
        updatedDto.setEmail("updated@example.com");
        updatedDto.setBirthDate(LocalDate.of(1995, 5, 5));

        when(userService.updateUser(eq(1L), any(UpdateUserDto.class), any(String.class))).thenReturn(updatedDto);

        // when & then
        mockMvc.perform(put("/api/v1/users/1")
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
        UpdateUserDto updateDto = new UpdateUserDto();
        updateDto.setName("Updated");
        updateDto.setSurname("User");
        updateDto.setBirthDate(LocalDate.of(1990, 1, 1));

        when(userService.updateUser(eq(999L), any(UpdateUserDto.class), any(String.class)))
                .thenThrow(new UserNotFoundException("User with id 999 not found!"));

        // when & then
        mockMvc.perform(put("/api/v1/users/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /api/v1/users/1 - доступ запрещен при неверном email")
    void updateUser_ShouldReturnForbidden_WhenAccessDenied() throws Exception {
        // given
        UpdateUserDto updateDto = new UpdateUserDto();
        updateDto.setName("Updated");

        when(userService.updateUser(eq(1L), any(UpdateUserDto.class), any(String.class)))
                .thenThrow(new org.springframework.security.access.AccessDeniedException(
                    "Access denied: You can only update your own information. Please, change Id in url."));

        // when & then
        mockMvc.perform(put("/api/v1/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").exists());
    }

    // Тест с валидацией email удален, так как email больше не передается в UpdateUserDto
    // Email берется из токена и не может быть изменен через этот endpoint

    @Test
    @DisplayName("DELETE /api/v1/users/1 - успешное удаление пользователя")
    void deleteUser_ShouldReturnNoContent() throws Exception {
        // given
        // when(userService.deleteUser(1L)).thenReturn(null); // void метод

        // when & then
        mockMvc.perform(delete("/api/v1/users/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/v1/users/999 - пользователь не найден для удаления")
    void deleteUser_ShouldReturnNotFound_WhenUserNotFound() throws Exception {
        // given
        doThrow(new UserNotFoundException("User with id 999 not found!"))
                .when(userService).deleteUser(999L);

        // when & then
        mockMvc.perform(delete("/api/v1/users/999"))
                .andExpect(status().isNotFound());
    }
}

