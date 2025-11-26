package com.innowise.demo.controller;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.innowise.demo.dto.CardInfoDto;
import com.innowise.demo.dto.UserDto;
import com.innowise.demo.service.CardInfoService;
import com.innowise.demo.service.UserService;
import com.innowise.demo.util.SecurityUtils;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/cards")
@RequiredArgsConstructor
public class CardInfoController {
    private final CardInfoService cardInfoService;
    private final UserService userService;

    /**
     * Создание карты.
     * ADMIN: может создать карту для любого пользователя.
     * USER: может создать карту только для себя.
     */
    @PostMapping
    public ResponseEntity<CardInfoDto> addCardInfo(
            @Valid @RequestBody CardInfoDto cardInfoDto,
            Authentication authentication) {
        // Получаем информацию о пользователе, для которого создается карта
        UserDto cardOwner = userService.findUserById(cardInfoDto.getUserId());
        
        // Проверка доступа: USER может создать карту только для себя
        if (!SecurityUtils.hasAccess(authentication, cardOwner.getEmail())) {
            throw new AccessDeniedException("Access denied: You can only create cards for yourself");
        }
        
        CardInfoDto created = cardInfoService.save(cardInfoDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Получение карты по ID.
     * ADMIN: может получить любую карту.
     * USER: может получить только свои карты.
     */
    @GetMapping("/{id}")
    public ResponseEntity<CardInfoDto> getCardInfoById(
            @PathVariable Long id,
            Authentication authentication) {
        CardInfoDto card = cardInfoService.getCardInfoById(id);
        
        // Получаем информацию о владельце карты
        UserDto cardOwner = userService.findUserById(card.getUserId());
        
        // Проверка доступа: USER может получить только свои карты
        if (!SecurityUtils.hasAccess(authentication, cardOwner.getEmail())) {
            throw new AccessDeniedException("Access denied: You can only access your own cards");
        }
        
        return ResponseEntity.ok(card);
    }

    /**
     * Получение списка всех карт.
     * ADMIN: может получить все карты.
     * USER: может получить только свои карты (нужно добавить фильтрацию в сервисе).
     * 
     * TODO: Добавить фильтрацию по пользователю для USER роли в сервисе.
     */
    @GetMapping
    public ResponseEntity<Page<CardInfoDto>> getAllCardInfos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        // Если USER, нужно фильтровать только его карты
        // Пока возвращаем все карты (ADMIN может видеть все)
        // TODO: Добавить метод в сервисе для получения карт по userId
        if (!SecurityUtils.isAdmin(authentication)) {
            throw new AccessDeniedException("Access denied: Only ADMIN can view all cards. Use GET /api/v1/cards/{id} to view your own cards");
        }
        
        return ResponseEntity.ok(cardInfoService.getAllCardInfos(page, size));
    }

    /**
     * Обновление карты.
     * ADMIN: может обновить любую карту.
     * USER: может обновить только свои карты.
     */
    @PutMapping("/{id}")
    public ResponseEntity<CardInfoDto> updateCardInfo(
            @PathVariable Long id,
            @Valid @RequestBody CardInfoDto dto,
            Authentication authentication) {
        // Получаем текущую карту для проверки доступа
        CardInfoDto currentCard = cardInfoService.getCardInfoById(id);
        UserDto cardOwner = userService.findUserById(currentCard.getUserId());
        
        // Проверка доступа: USER может обновить только свои карты
        if (!SecurityUtils.hasAccess(authentication, cardOwner.getEmail())) {
            throw new AccessDeniedException("Access denied: You can only update your own cards");
        }
        
        return ResponseEntity.ok(cardInfoService.updateCardInfo(id, dto));
    }

    /**
     * Удаление карты.
     * ADMIN: может удалить любую карту.
     * USER: может удалить только свои карты.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCardInfo(
            @PathVariable Long id,
            Authentication authentication) {
        // Получаем текущую карту для проверки доступа
        CardInfoDto currentCard = cardInfoService.getCardInfoById(id);
        UserDto cardOwner = userService.findUserById(currentCard.getUserId());
        
        // Проверка доступа: USER может удалить только свои карты
        if (!SecurityUtils.hasAccess(authentication, cardOwner.getEmail())) {
            throw new AccessDeniedException("Access denied: You can only delete your own cards");
        }
        
        cardInfoService.deleteCardInfo(id);
        return ResponseEntity.noContent().build();
    }
}
