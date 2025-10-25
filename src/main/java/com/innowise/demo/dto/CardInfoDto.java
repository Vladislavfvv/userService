package com.innowise.demo.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

@Data
public class CardInfoDto {
    private Long id;

    @NotNull(message = "User ID is required")
    private Long userId;

    @NotBlank(message = "Card number must not be blank")
    private String number;

    @NotBlank(message = "Card holder name must not be blank")
    private String holder;

    @NotNull(message = "Expiration date is required")
    private LocalDate expirationDate;
}
