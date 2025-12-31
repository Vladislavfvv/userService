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

import org.springframework.beans.factory.annotation.Value;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    
    @Value("${authentication.service.internal-api-key:}")
    private String internalApiKey;

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
     * Внутренний endpoint для синхронизации создания пользователя из authentication-service.
     * Используется только для внутренних вызовов от authentication-service с внутренним API ключом.
     * НЕ предназначен для прямого использования клиентами.
     * 
     * @param dto данные пользователя (email, firstName, lastName, birthDate)
     * @param apiKey внутренний API ключ из заголовка X-Internal-Api-Key
     * @return созданный пользователь
     */
    @PostMapping("/sync")
    public ResponseEntity<UserDto> syncCreateUser(
            @Valid @RequestBody UserDto dto,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Internal-Api-Key", required = false) String apiKey) {
        
        // Проверка внутреннего API ключа
        if (internalApiKey != null && !internalApiKey.isBlank()) {
            if (apiKey == null || apiKey.isBlank() || !apiKey.equals(internalApiKey)) {
                log.warn("Unauthorized attempt to sync create user. Invalid or missing internal API key.");
                throw new AccessDeniedException("Invalid or missing internal API key");
            }
        } else {
            log.warn("Internal API key not configured. Endpoint is accessible without authentication.");
        }
        
        log.info("Received sync user creation request from authentication-service: email={}, firstName={}, lastName={}", 
                dto.getEmail(), dto.getFirstName(), dto.getLastName());
        
        UserDto createdUser = userService.createUser(dto);
        
        log.info("User successfully synced and created: id={}, email={}", createdUser.getId(), createdUser.getEmail());
        return ResponseEntity.ok(createdUser);
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
            @RequestParam String email,
            Authentication authentication) {
        log.info("Getting user by email: {} (requested by: {})", 
                email, authentication != null ? SecurityUtils.getEmailFromToken(authentication) : "unknown");
        
        // Проверка доступа ДО получения пользователя из базы
        // USER может запрашивать только свой email
        if (!SecurityUtils.isAdmin(authentication)) {
            String userEmail = SecurityUtils.getEmailFromToken(authentication);
            log.debug("User is not ADMIN. Checking access: token email={}, requested email={}", userEmail, email);
            if (!userEmail.equals(email)) {
                log.warn("Access denied: User {} tried to access email {}", userEmail, email);
                throw new AccessDeniedException("Access denied: You can only access your own information");
            }
        } else {
            log.debug("User is ADMIN, skipping access check");
        }
        
        // Получаем пользователя из базы только после проверки доступа
        log.debug("Fetching user from database for email: {}", email);
        UserDto user = userService.getUserByEmail(email);
        log.info("User found: id={}, email={}", user.getId(), user.getEmail());
        
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
        log.info("Received update request for current user. DTO: firstName={}, lastName={}, name={}, surname={}, birthDate={}", 
                dto.getFirstName(), dto.getLastName(), dto.getName(), dto.getSurname(), dto.getBirthDate());
        
        // Извлекаем email из токена
        String userEmail;
        try {
            userEmail = SecurityUtils.getEmailFromToken(authentication);
            log.debug("Extracted email from token: {}", userEmail);
        } catch (IllegalStateException e) {
            log.error("Failed to extract email from token: {}", e.getMessage());
            throw new AccessDeniedException("Access denied: Authentication required.");
        }
        
        // Находим пользователя по email и обновляем его
        UserDto updated = userService.updateCurrentUser(userEmail, dto);
        log.info("User successfully updated: id={}, email={}", updated.getId(), updated.getEmail());
        return ResponseEntity.ok(updated);
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
