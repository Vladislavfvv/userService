package com.innowise.demo.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.innowise.demo.model.CardInfo;

public interface CardInfoRepository extends JpaRepository<CardInfo, Long> {

    // JPQL-запрос
    @Query("SELECT COUNT(c) FROM CardInfo c WHERE c.user.id = :userId")
    long countCardsByUserId(Long userId);

    /**
     * Находит карту по номеру и ID пользователя.
     * Используется для проверки, есть ли уже такая карта у данного пользователя.
     */
    @Query("SELECT c FROM CardInfo c WHERE c.number = :number AND c.user.id = :userId")
    Optional<CardInfo> findByNumberAndUserId(@Param("number") String number, @Param("userId") Long userId);

    /**
     * Находит карту по номеру (независимо от пользователя).
     * Используется для проверки, не принадлежит ли карта другому пользователю.
     */
    @Query("SELECT c FROM CardInfo c WHERE c.number = :number")
    Optional<CardInfo> findByNumber(@Param("number") String number);

    /**
     * Находит все карты пользователя по email (без учета регистра) с пагинацией.
     * Используется для получения списка карт текущего пользователя.
     */
    @Query("SELECT c FROM CardInfo c WHERE LOWER(c.user.email) = LOWER(:email)")
    Page<CardInfo> findAllByUser_EmailIgnoreCase(@Param("email") String email, Pageable pageable);
}
