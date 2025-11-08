package com.innowise.demo.client;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.innowise.demo.client.dto.TokenValidationRequest;
import com.innowise.demo.client.dto.TokenValidationResponse;

@Component
public class AuthServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceClient.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public AuthServiceClient(RestTemplate restTemplate,
                             @Value("${authentication.service.base-url:http://authentication-service:8081}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
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


