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
     * @param request данные пользователя (name, surname, birthDate)
     * @param authentication объект аутентификации, содержащий JWT токен
     * @return созданный пользователь
     */
    @PostMapping("/self")
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
     * Обновление пользователя.
     * ADMIN: может обновить любого пользователя.
     * USER: может обновить только свою информацию.
     * Выполняет частичное обновление - обновляются только переданные поля.
     * Email берется из токена, holder для карт автоматически формируется из name + surname.
     * Проверка доступа выполняется в сервисе.
     */
    @PutMapping("/{id}")
    public ResponseEntity<UserDto> updateUser(
            @PathVariable Long id,
            @RequestBody UpdateUserDto dto,
            Authentication authentication) {
        // Извлекаем email из токена для передачи в сервис
        String userEmail;
        try {
            userEmail = SecurityUtils.getEmailFromToken(authentication);
        } catch (IllegalStateException e) {
            throw new AccessDeniedException("Access denied: You can only update your own information.");
        }
        
        // Сервис сам проверит права доступа и выбросит исключение при необходимости
        return ResponseEntity.ok(userService.updateUser(id, dto, userEmail));
    }

    /**
     * Удаление пользователя.
     * ADMIN: может удалить любого пользователя.
     * USER: может удалить только свою информацию.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable Long id,
            Authentication authentication) {
        // Получаем текущего пользователя для проверки доступа
        UserDto currentUser = userService.findUserById(id);
        
        // Проверка доступа: USER может удалить только свою информацию
        if (!SecurityUtils.hasAccess(authentication, currentUser.getEmail())) {
            throw new AccessDeniedException("Access denied: You can only delete your own information");
        }
        
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
