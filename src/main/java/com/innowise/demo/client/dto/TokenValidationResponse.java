package com.innowise.demo.client.dto;

public record TokenValidationResponse(boolean valid, String username, String role) {
}


