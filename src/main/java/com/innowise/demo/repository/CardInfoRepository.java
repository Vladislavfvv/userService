package com.innowise.demo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.innowise.demo.model.CardInfo;

public interface CardInfoRepository extends JpaRepository<CardInfo, Long> {

    /// ///////////////////////

// JPQL-запрос
    @Query("SELECT COUNT(c) FROM CardInfo c WHERE c.user.id = :userId")
    long countCardsByUserId(Long userId);
}
