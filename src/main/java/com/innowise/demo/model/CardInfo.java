package com.innowise.demo.model;

import java.time.LocalDate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonBackReference;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Entity
@Table(name = "card_info", indexes = {
        @Index(name = "idx_card_user", columnList = "user_id"),
        @Index(name = "idx_card_number", columnList = "number")
},
        schema="userservice_data")
@Getter
@Setter
@NoArgsConstructor
public class CardInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Getter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonBackReference//"обратная" ссылка, Jackson её пропускает при сериализации.
    private User user;

    private String number;
    private String holder;

    @Column(name = "expiration_date")
    private LocalDate expirationDate;

}
