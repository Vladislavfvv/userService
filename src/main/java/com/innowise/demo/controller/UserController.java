package com.innowise.demo.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.innowise.demo.client.AuthServiceClient;
import com.innowise.demo.dto.PagedUserResponse;
import com.innowise.demo.dto.UserDto;
import com.innowise.demo.service.UserService;
import com.innowise.demo.dto.UserUpdateRequest;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;

import java.util.Collection;
import java.util.List;

@RestController
@RequestMapping({"/api/v1/users", "/api/users"})
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final AuthServiceClient authServiceClient;
    
    @Value("${authentication.service.internal-api-key:}")
    private String internalApiKey;

    /**
     * Эндпоинт для синхронизации пользователей из authentication-service
     * Проверяет внутренний API ключ вместо роли
     * 
     * ВАЖНО: Публичный эндпоинт POST /api/v1/users удален, чтобы предотвратить
     * создание "фантомных" пользователей. Все пользователи должны создаваться
     * через регистрацию в authentication-service, которая затем синхронизирует
     * их через этот эндпоинт /sync.
     */
    @PostMapping("/sync")
    public ResponseEntity<UserDto> syncUser(
            @RequestHeader(value = "X-Internal-Api-Key", required = false) String apiKey,
            @Valid @RequestBody UserDto dto) {
        // Проверка внутреннего API ключа
        if (internalApiKey == null || internalApiKey.isBlank() || !internalApiKey.equals(apiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        // Создаем или возвращаем существующего пользователя
        return ResponseEntity.ok(userService.syncUser(dto));
    }

    @GetMapping("/id")
    public ResponseEntity<UserDto> getUserById(@RequestParam Long id) {
        return ResponseEntity.ok(userService.findUserById(id));
    }

    /**
     * Получение всех пользователей с проверкой ролей из Keycloak токена.
     * ROLE_ADMIN - может видеть всех пользователей
     * ROLE_USER - может видеть только своих пользователей (логику нужно добавить)
     */
    @GetMapping
    public ResponseEntity<PagedUserResponse> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            Authentication authentication) {
        
        // Получить роли из Keycloak токена
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        
        // Проверить роль
        boolean isAdmin = authorities.stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        
        if (isAdmin) {
            // Администратор видит всех пользователей
            return ResponseEntity.ok(userService.findAllUsers(page, size));
        } else {
            UserDto currentUser = userService.getCurrentUser(authentication);
            PagedUserResponse response = new PagedUserResponse(
                    List.of(currentUser),
                    0,
                    1,
                    1,
                    1
            );
            return ResponseEntity.ok(response);
        }
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> getCurrentUser(Authentication authentication) {
        return ResponseEntity.ok(userService.getCurrentUser(authentication));
    }

    //find user by email named
    @GetMapping("/email")
    public  ResponseEntity<UserDto> getUserByEmail(@RequestParam(required = false) String email,
                                                   @RequestHeader("Authorization") String authHeader) {
        var validation = authServiceClient.validateAuthorizationHeader(authHeader);
        if (validation.isEmpty() || !validation.get().valid()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(userService.getUserByEmail(email));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserDto> updateUser(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest dto) {
        return ResponseEntity.ok(userService.updateUser(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
