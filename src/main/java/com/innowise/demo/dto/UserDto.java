package com.innowise.demo.dto;

import java.time.LocalDate;
import java.util.List;

import com.innowise.demo.model.CardInfo;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Setter
@Getter
public class UserDto {
    //    private Long id;
//
//    @NotBlank(message = "Name must not be blank")
//    private String name;
//
//    @NotBlank(message = "Surname must not be blank")
//    private String surname;
//
//    @NotNull(message = "BirthDate is required")
//    private LocalDate birthDate;
//
//    @Email(message = "Email must be valid")
//    @NotBlank(message = "Email must not be blank")
//    private String email;
//
//    private List<CardInfo> cards;
    private Long id;
    private String name;
    private String surname;
    private LocalDate birthDate;
    private String email;
    private List<CardInfoDto> cards;


//    public Long getId() {
//        return id;
//    }
//
//    public void setId(Long id) {
//        this.id = id;
//    }
//
//    public String getName() {
//        return name;
//    }
//
//    public void setName(String name) {
//        this.name = name;
//    }
//
//    public String getSurname() {
//        return surname;
//    }
//
//    public void setSurname(String surname) {
//        this.surname = surname;
//    }
//
//    public LocalDate getBirthDate() {
//        return birthDate;
//    }
//
//    public void setBirthDate(LocalDate birthDate) {
//        this.birthDate = birthDate;
//    }
//
//    public String getEmail() {
//        return email;
//    }
//
//    public void setEmail(String email) {
//        this.email = email;
//    }
//
//    public List<CardInfo> getCards() {
//        return cards;
//    }
//
//    public void setCards(List<CardInfo> cards) {
//        this.cards = cards;
//    }
}

