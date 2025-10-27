package com.innowise.demo.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonManagedReference;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Entity
@Table(name = "users", schema = "userservice_data")
//@Getter
//@Setter
@NoArgsConstructor
@NamedQueries({
        @NamedQuery(
                name = "User.findByEmailNamed",
                query = "Select u FROM User u where u.email=:email"
        )
})
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String surname;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(unique = true, nullable = false)
    private String email;

    @OneToMany(mappedBy = "user",
            cascade = CascadeType.ALL)//,
           // orphanRemoval = true)
    @JsonManagedReference//"ведущий" объект (с которого начинается сериализация)
            //Убирает рекурсию при сериализации в JSON
    private List<CardInfo> cards = new ArrayList<>();


    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSurname() { return surname; }
    public void setSurname(String surname) { this.surname = surname; }
    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public List<CardInfo> getCards() { return cards; }
    public void setCards(List<CardInfo> cards) { this.cards = cards; }
}
