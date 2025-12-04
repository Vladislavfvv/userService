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
        log.info("Attempting to delete user {} from authentication-service", email);
        log.info("AuthServiceClient configuration - baseUrl: {}, internalApiKey configured: {}", 
                baseUrl, internalApiKey != null && !internalApiKey.isBlank());
        
        if (baseUrl == null || baseUrl.isBlank()) {
            log.warn("Authentication service URL not configured. Skipping user deletion in authentication-service.");
            return;
        }

        // Внутренний API ключ опционален - endpoint может работать без него
        // Но лучше настроить его для безопасности
        HttpHeaders headers = new HttpHeaders();
        if (internalApiKey != null && !internalApiKey.isBlank()) {
            headers.set(INTERNAL_API_KEY_HEADER, internalApiKey);
            log.info("Using internal API key for authentication-service request (key length: {})", internalApiKey.length());
        } else {
            log.warn("Internal API key not configured. Request will be sent without API key.");
        }

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            // Используем UriComponentsBuilder для правильного кодирования URL
            // RestTemplate автоматически кодирует URL, поэтому не нужно кодировать вручную
            String url = org.springframework.web.util.UriComponentsBuilder
                    .fromUriString(baseUrl)
                    .path("/auth/v1/internal/sync/users/{email}")
                    .buildAndExpand(email)
                    .toUriString();
            log.info("Calling DELETE {} for user {}", url, email);
            
            ResponseEntity<Void> response = restTemplate.exchange(url, HttpMethod.DELETE, entity, Void.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Successfully deleted user {} from authentication-service. Response status: {}", email, response.getStatusCode());
            } else {
                log.warn("Unexpected response status {} when deleting user {} from authentication-service", response.getStatusCode(), email);
            }
        } catch (org.springframework.web.client.HttpClientErrorException ex) {
            log.error("HTTP error when deleting user {} from authentication-service. Status: {}, Response: {}", 
                    email, ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
            // Не выбрасываем исключение, чтобы не прерывать удаление в user-service
        } catch (RestClientException ex) {
            log.error("Failed to delete user {} from authentication-service. URL: {}. Error: {}", 
                    email, baseUrl + "/auth/v1/internal/sync/users/" + email, ex.getMessage(), ex);
            // Не выбрасываем исключение, чтобы не прерывать удаление в user-service
        } catch (Exception ex) {
            log.error("Unexpected error while deleting user {} from authentication-service: {}", email, ex.getMessage(), ex);
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


