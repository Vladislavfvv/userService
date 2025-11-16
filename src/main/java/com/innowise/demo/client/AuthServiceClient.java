package com.innowise.demo.client;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.innowise.demo.client.dto.TokenValidationRequest;
import com.innowise.demo.client.dto.TokenValidationResponse;
import com.innowise.demo.client.dto.UpdateUserProfileRequest;

@Component
public class AuthServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceClient.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String internalApiKey;

    public AuthServiceClient(RestTemplate restTemplate,
                             @Value("${authentication.service.base-url:http://authentication-service:8081}") String baseUrl,
                             @Value("${authentication.service.internal-api-key:}") String internalApiKey) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.internalApiKey = internalApiKey;
    }

    public Optional<TokenValidationResponse> validateAuthorizationHeader(String authHeader) {
        String token = extractToken(authHeader);
        if (token == null) {
            return Optional.empty();
        }
        return validateToken(token);
    }

    public Optional<TokenValidationResponse> validateToken(String token) {
        TokenValidationRequest request = new TokenValidationRequest(token);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<TokenValidationRequest> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<TokenValidationResponse> response =
                    restTemplate.postForEntity(baseUrl + "/auth/validate", entity, TokenValidationResponse.class);
            return Optional.ofNullable(response.getBody());
        } catch (RestClientException ex) {
            log.warn("Failed to validate token via authentication-service: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    @SuppressWarnings("null")
    public void updateUserProfile(UpdateUserProfileRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (internalApiKey != null && !internalApiKey.isBlank()) {
            headers.set(INTERNAL_API_KEY_HEADER, internalApiKey);
        }

        HttpEntity<UpdateUserProfileRequest> entity = new HttpEntity<>(request, headers);

        try {
            restTemplate.exchange(baseUrl + "/auth/users/profile", HttpMethod.PUT, entity, Void.class);
        } catch (RestClientException ex) {
            log.error("Failed to update user profile in authentication-service: {}", ex.getMessage());
            throw new IllegalStateException("Failed to synchronize authentication profile", ex);
        }
    }

    /**
     * Удаление пользователя по email из authentication-service (auth_db и Keycloak)
     */
    public void deleteUser(String email) {
        if (baseUrl == null || baseUrl.isBlank() || internalApiKey == null || internalApiKey.isBlank()) {
            log.warn("Authentication service URL or internal API key not configured. Skipping user deletion in authentication-service.");
            return;
        }

        HttpHeaders headers = new HttpHeaders();
        if (internalApiKey != null && !internalApiKey.isBlank()) {
            headers.set(INTERNAL_API_KEY_HEADER, internalApiKey);
        }

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            // URL-кодируем email для использования в path
            String encodedEmail = java.net.URLEncoder.encode(email, java.nio.charset.StandardCharsets.UTF_8);
            restTemplate.exchange(baseUrl + "/auth/users/" + encodedEmail, HttpMethod.DELETE, entity, Void.class);
            log.info("Successfully deleted user {} from authentication-service", email);
        } catch (RestClientException ex) {
            log.error("Failed to delete user {} from authentication-service: {}", email, ex.getMessage(), ex);
            // Не выбрасываем исключение, чтобы не прерывать удаление в user-service
        }
    }

    private String extractToken(String authHeader) {
        if (authHeader == null) {
            return null;
        }
        if (authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }
}


