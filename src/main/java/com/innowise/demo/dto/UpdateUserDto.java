package com.innowise.demo.dto;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

/**
 * DTO для обновления пользователя.
 * Все поля опциональны - обновляются только переданные поля.
 * Поддерживает оба варианта именования: firstName/lastName и name/surname.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateUserDto {

    /**
     * Имя пользователя. Поддерживает оба варианта: "firstName" и "name"
     */
    private String firstName;
    
    /**
     * Альтернативное имя для совместимости: "name" -> firstName
     */
    private String name;

    /**
     * Фамилия пользователя. Поддерживает оба варианта: "lastName" и "surname"
     */
    private String lastName;
    
    /**
     * Альтернативная фамилия для совместимости: "surname" -> lastName
     */
    private String surname;
    
    /**
     * Получить firstName (поддерживает оба варианта: firstName и name)
     */
    public String getFirstName() {
        return firstName != null && !firstName.isBlank() ? firstName : (name != null && !name.isBlank() ? name : null);
    }
    
    /**
     * Получить lastName (поддерживает оба варианта: lastName и surname)
     */
    public String getLastName() {
        return lastName != null && !lastName.isBlank() ? lastName : (surname != null && !surname.isBlank() ? surname : null);
    }

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate birthDate;

    /**
     * Список карт для обновления.
     * userId и holder будут автоматически заполнены из данных пользователя.
     */
    private List<CardInfoDto> cards;
}

