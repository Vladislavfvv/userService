package com.innowise.demo.config;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

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

        String jwkSetUri = resourceServerProperties.getJwt().getJwkSetUri();
        NimbusJwtDecoder rsaDecoder = null;
        if (StringUtils.hasText(jwkSetUri)) {
            rsaDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        }

        NimbusJwtDecoder finalRsaDecoder = rsaDecoder;
        return token -> {
            try {
                return hmacDecoder.decode(token);
            } catch (JwtException ex) {
                if (finalRsaDecoder != null) {
                    return finalRsaDecoder.decode(token);
                }
                throw ex;
            }
        };
    }
}
