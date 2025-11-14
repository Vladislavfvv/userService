package com.innowise.demo.config;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.util.StringUtils;

@Profile("auth-service")
@Configuration
public class AuthServiceJwtDecoderConfig {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceJwtDecoderConfig.class);

    @Value("${auth.jwt.secret}")
    private String jwtSecret;

    private final OAuth2ResourceServerProperties resourceServerProperties;

    public AuthServiceJwtDecoderConfig(OAuth2ResourceServerProperties resourceServerProperties) {
        this.resourceServerProperties = resourceServerProperties;
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        SecretKey key = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusJwtDecoder hmacDecoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        // Получаем jwkSetUri для ленивой инициализации RSA декодера
        // НЕ создаем RSA декодер при инициализации бина - только при первом использовании
        String jwkSetUri = resourceServerProperties.getJwt().getJwkSetUri();
        final boolean hasJwkSetUri = StringUtils.hasText(jwkSetUri);
        final String finalJwkSetUri = jwkSetUri;

        // Используем ленивую инициализацию для RSA декодера
        // Это предотвращает попытки подключения к Keycloak при старте приложения
        return new JwtDecoder() {
            private volatile NimbusJwtDecoder rsaDecoder;
            private volatile boolean rsaDecoderInitialized = false;
            private final Object lock = new Object();

            @Override
            public org.springframework.security.oauth2.jwt.Jwt decode(String token) throws JwtException {
                try {
                    // Сначала пытаемся декодировать как HMAC токен (от authentication-service)
                    return hmacDecoder.decode(token);
                } catch (JwtException ex) {
                    // Если HMAC не сработал и есть jwkSetUri, пробуем RSA (от Keycloak)
                    if (hasJwkSetUri) {
                        try {
                            NimbusJwtDecoder rsa = getOrCreateRsaDecoder();
                            if (rsa != null) {
                                return rsa.decode(token);
                            }
                        } catch (JwtException rsaEx) {
                            // Если RSA декодер недоступен или не смог декодировать токен,
                            // выбрасываем оригинальную ошибку HMAC
                            log.debug("RSA decoder failed or unavailable, using original HMAC error: {}", rsaEx.getMessage());
                        }
                    }
                    // Выбрасываем оригинальную ошибку HMAC
                    throw ex;
                }
            }

            private NimbusJwtDecoder getOrCreateRsaDecoder() throws JwtException {
                if (!rsaDecoderInitialized) {
                    synchronized (lock) {
                        if (!rsaDecoderInitialized) {
                            try {
                                // Создаем RSA декодер только при первом использовании
                                // Это предотвращает попытки подключения к Keycloak при старте
                                rsaDecoder = NimbusJwtDecoder.withJwkSetUri(finalJwkSetUri).build();
                                log.debug("RSA decoder initialized successfully from Keycloak");
                            } catch (Exception e) {
                                // Если не удалось создать RSA декодер, логируем и помечаем как недоступный
                                log.warn("Could not initialize RSA decoder from Keycloak. Keycloak may be unavailable. Error: {}", e.getMessage());
                                // Устанавливаем флаг, чтобы не пытаться снова
                                rsaDecoderInitialized = true;
                                rsaDecoder = null;
                                // Выбрасываем исключение, чтобы вернуться к обработке ошибки HMAC
                                throw new JwtException("RSA decoder unavailable: " + e.getMessage(), e);
                            }
                            rsaDecoderInitialized = true;
                        }
                    }
                }
                if (rsaDecoder == null) {
                    throw new JwtException("RSA decoder is not available");
                }
                return rsaDecoder;
            }
        };
    }
}
