package com.innowise.demo.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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
import com.innowise.demo.dto.UserDto;
import com.innowise.demo.service.UserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

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
        String email = extractEmailFromToken(authentication);
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
     * Извлекает email из JWT токена в Authentication объекте.
     * Email находится в claim "sub" (subject) токена.
     * 
     * @param authentication объект аутентификации
     * @return email пользователя из токена
     * @throws IllegalStateException если токен не содержит email
     */
    private String extractEmailFromToken(Authentication authentication) {
        if (authentication == null) {
            throw new IllegalStateException("Authentication is required");
        }

        Jwt jwt = extractJwt(authentication);
        if (jwt == null) {
            throw new IllegalStateException("JWT token is required");
        }

        // Извлекаем email из claim "sub" (subject) - это логин пользователя из auth-service
        String email = jwt.getSubject();
        if (email == null || email.isBlank()) {
            throw new IllegalStateException("Email (sub claim) not found in JWT token");
        }

        return email;
    }

    /**
     * Извлекает JWT из Authentication объекта.
     * Поддерживает разные типы Authentication (JwtAuthenticationToken, OAuth2AuthenticationToken и т.д.)
     */
    private Jwt extractJwt(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return jwtAuthenticationToken.getToken();
        }
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        return null;
    }   

    @GetMapping("/id")
    public ResponseEntity<UserDto> getUserById(@RequestParam Long id) {
        return ResponseEntity.ok(userService.findUserById(id));
    }

    @GetMapping
    public ResponseEntity<PagedUserResponse> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size) {
        return ResponseEntity.ok(userService.findAllUsers(page, size));
    }

    //find user by email named
    @GetMapping("/email")
    public  ResponseEntity<UserDto> getUserByEmail(@RequestParam(required = false) String email) {
         return ResponseEntity.ok(userService.getUserByEmail(email));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserDto> updateUser(@PathVariable Long id, @Valid @RequestBody UserDto dto) {
        return ResponseEntity.ok(userService.updateUser(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
