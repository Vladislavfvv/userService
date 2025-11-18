package com.innowise.demo.config;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import io.jsonwebtoken.security.Keys;

/**
 * Конфигурация Spring Security для user-service.
 * Настраивает OAuth2 Resource Server для валидации JWT токенов от auth-service.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${jwt.secret}")
    private String jwtSecret;

    /**
     * Настраивает Security Filter Chain для работы с JWT токенами.
     * Endpoint /api/v1/users/self требует аутентификации (токен из auth-service).
     * Остальные endpoints также требуют аутентификации.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/cache/**").hasRole("ADMIN")
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/api/v1/users/sync").permitAll()
                        .requestMatchers("/api/v1/users/**", "/api/v1/cards/**").hasAnyRole("ADMIN", "USER")
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(jwtDecoder()))
                );

        return http.build();
    }

    /**
     * Создает JWT Decoder для валидации токенов от auth-service.
     * Использует тот же секрет, что и auth-service (HMAC SHA-256).
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        SecretKey secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        return NimbusJwtDecoder.withSecretKey(secretKey).build();
    }
}

