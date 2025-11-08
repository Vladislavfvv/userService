package com.innowise.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Конфигурация для кастомного JwtDecoder, который:
 * 1. Получает JWK set из внутреннего адреса Keycloak (доступного из Docker сети)
 * 2. Проверяет issuer вручную (чтобы соответствовал issuer в токене от клиента)
 */
@Configuration
public class JwtDecoderConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Bean
    public JwtDecoder jwtDecoder() {
        // Создаем декодер, который получает JWK set из внутреннего адреса Keycloak
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        
        // Обертываем декодер для проверки issuer
        return new JwtDecoder() {
            @Override
            public Jwt decode(String token) throws JwtException {
                Jwt jwt = decoder.decode(token);
                
                // Проверяем issuer
                String tokenIssuer = jwt.getIssuer().toString();
                if (!issuerUri.equals(tokenIssuer)) {
                    OAuth2Error error = new OAuth2Error(
                        "invalid_token",
                        "The iss claim is not valid. Expected: " + issuerUri + ", but was: " + tokenIssuer,
                        null
                    );
                    throw new OAuth2AuthenticationException(error);
                }
                
                return jwt;
            }
        };
    }
}

