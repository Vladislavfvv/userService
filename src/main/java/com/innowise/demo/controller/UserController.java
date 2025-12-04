package com.innowise.demo.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.innowise.demo.dto.CreateUserFromTokenRequest;
import com.innowise.demo.dto.PagedUserResponse;
import com.innowise.demo.dto.UpdateUserDto;
import com.innowise.demo.dto.UserDto;
import com.innowise.demo.service.UserService;
import com.innowise.demo.util.SecurityUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    /**
     * Получение своих данных из JWT токена.
     * Email извлекается из токена (claim "sub"), пользователь получает свои данные.
     * 
     * @param authentication объект аутентификации, содержащий JWT токен
     * @return данные текущего пользователя
     */
    @GetMapping("/self")
    public ResponseEntity<UserDto> getSelfUser(Authentication authentication) {
        log.info("Getting user data from token");
        
        // Извлекаем email из JWT токена
        String email = SecurityUtils.getEmailFromToken(authentication);
        log.debug("Extracted email from token: {}", email);
        
        // Получаем пользователя по email
        UserDto userDto = userService.getUserByEmail(email);
        return ResponseEntity.ok(userDto);
    }

    /**
     * Создание пользователя из JWT токена.
     * Email извлекается из токена (claim "sub"), остальные данные из тела запроса.
     * Пользователь должен быть зарегистрирован в auth-service и иметь валидный JWT токен.
     * 
     * @param request данные пользователя (firstName, lastName, birthDate)
     * @param authentication объект аутентификации, содержащий JWT токен
     * @return созданный пользователь
     */
    @PostMapping("/createUser")
    public ResponseEntity<UserDto> createUserFromToken(
            @Valid @RequestBody CreateUserFromTokenRequest request,
            Authentication authentication) {
        log.info("Creating user from token for authenticated user");
        
        // Извлекаем email из JWT токена
        String email = SecurityUtils.getEmailFromToken(authentication);
        log.debug("Extracted email from token: {}", email);
        
        // Создаем пользователя с email из токена
        UserDto userDto = userService.createUserFromToken(email, request);
        return ResponseEntity.ok(userDto);
    }

    @PostMapping
    public ResponseEntity<UserDto> createUser(@Valid @RequestBody UserDto dto) {
        return ResponseEntity.ok(userService.createUser(dto));
    }


    /**
     * Получение пользователя по ID.
     * ADMIN: может получить любого пользователя.
     * USER: может получить только свою информацию.
     */
    @GetMapping("/id")
    public ResponseEntity<UserDto> getUserById(
            @RequestParam Long id,
            Authentication authentication) {
        UserDto user = userService.findUserById(id);
        
        // Проверка доступа: USER может получить только свою информацию
        if (!SecurityUtils.hasAccess(authentication, user.getEmail())) {
            throw new AccessDeniedException("Access denied: You can only access your own information");
        }
        
        return ResponseEntity.ok(user);
    }

    /**
     * Получение списка всех пользователей.
     * Доступно только для ADMIN (ограничение в SecurityConfig).
     */
    @GetMapping
    public ResponseEntity<PagedUserResponse> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size) {
        return ResponseEntity.ok(userService.findAllUsers(page, size));
    }

    /**
     * Получение пользователя по email.
     * ADMIN: может получить любого пользователя.
     * USER: может получить только свою информацию.
     */
    @GetMapping("/email")
    public ResponseEntity<UserDto> getUserByEmail(
            @RequestParam(required = false) String email,
            Authentication authentication) {
        UserDto user = userService.getUserByEmail(email);
        
        // Проверка доступа: USER может получить только свою информацию
        if (!SecurityUtils.hasAccess(authentication, user.getEmail())) {
            throw new AccessDeniedException("Access denied: You can only access your own information");
        }
        
        return ResponseEntity.ok(user);
    }

    /**
     * Обновление текущего пользователя (свой профиль).
     * ID берется из JWT токена (по email).
     * Выполняет частичное обновление - обновляются только переданные поля.
     * Email берется из токена, holder для карт автоматически формируется из name + surname.
     */
    @PutMapping("/me")
    public ResponseEntity<UserDto> updateCurrentUser(
            @RequestBody UpdateUserDto dto,
            Authentication authentication) {
        // Извлекаем email из токена
        String userEmail;
        try {
            userEmail = SecurityUtils.getEmailFromToken(authentication);
        } catch (IllegalStateException e) {
            throw new AccessDeniedException("Access denied: Authentication required.");
        }
        
        // Находим пользователя по email и обновляем его
        return ResponseEntity.ok(userService.updateCurrentUser(userEmail, dto));
    }

    /**
     * Обновление пользователя по ID (только для ADMIN).
     * ADMIN может обновить любого пользователя.
     * Выполняет частичное обновление - обновляются только переданные поля.
     * Holder для карт автоматически формируется из name + surname.
     */
    @PutMapping("/{id}")
    public ResponseEntity<UserDto> updateUser(
            @PathVariable Long id,
            @RequestBody UpdateUserDto dto,
            Authentication authentication) {
        // Проверка доступа: только ADMIN может обновлять пользователей по ID
        if (!SecurityUtils.isAdmin(authentication)) {
            throw new AccessDeniedException("Access denied: Only administrators can update users by ID. " +
                    "Use PUT /api/v1/users/me to update your own profile.");
        }
        
        // Извлекаем email админа для логирования
        String adminEmail;
        try {
            adminEmail = SecurityUtils.getEmailFromToken(authentication);
        } catch (IllegalStateException e) {
            throw new AccessDeniedException("Access denied: Authentication required.");
        }
        
        // Админ может обновить любого пользователя (проверка доступа не требуется)
        return ResponseEntity.ok(userService.updateUserByAdmin(id, dto, adminEmail));
    }

    /**
     * Удаление пользователя.
     * Только ADMIN может удалять пользователей.
     * USER не может удалять даже свои данные.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable Long id,
            Authentication authentication) {
        log.info("Delete user request received for user ID: {} by user: {}", id, 
                authentication != null ? SecurityUtils.getEmailFromToken(authentication) : "unknown");
        
        // Проверка доступа: только ADMIN может удалять пользователей
        if (!SecurityUtils.isAdmin(authentication)) {
            log.warn("Access denied: User {} attempted to delete user ID: {}", 
                    SecurityUtils.getEmailFromToken(authentication), id);
            throw new AccessDeniedException("Access denied: Only administrators can delete users");
        }
        
        log.info("Admin user {} is deleting user ID: {}", SecurityUtils.getEmailFromToken(authentication), id);
        
        // Проверяем, что пользователь существует и получаем email для синхронизации
        UserDto userToDelete = userService.findUserById(id);
        log.info("User to delete found: {} (email: {})", id, userToDelete.getEmail());
        
        userService.deleteUser(id);
        log.info("User ID: {} successfully deleted from user-service", id);
        
        return ResponseEntity.noContent().build();
    }
}
