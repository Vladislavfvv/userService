package com.innowise.demo.dto;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.Email;

import lombok.Data;

@Data
public class UserUpdateRequest {
    private Long userId;
    private String name;
    private String surname;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate birthDate;

    @Email(message = "Email should be valid")
    private String email;

    private List<CardInfoDto> cards;
}

