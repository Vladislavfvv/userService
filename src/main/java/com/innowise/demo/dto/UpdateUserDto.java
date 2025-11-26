package com.innowise.demo.dto;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO для обновления пользователя.
 * Все поля опциональны - обновляются только переданные поля.
 */
@Data
@Setter
@Getter
public class UpdateUserDto {

    private String name;

    private String surname;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate birthDate;

    /**
     * Список карт для обновления.
     * userId и holder будут автоматически заполнены из данных пользователя.
     */
    private List<CardInfoDto> cards;
}

