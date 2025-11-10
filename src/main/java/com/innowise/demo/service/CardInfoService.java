package com.innowise.demo.service;

import jakarta.transaction.Transactional;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.innowise.demo.dto.CardInfoDto;
import com.innowise.demo.exception.CardInfoNotFoundException;
import com.innowise.demo.exception.UserNotFoundException;
import com.innowise.demo.mapper.CardInfoMapper;
import com.innowise.demo.model.CardInfo;
import com.innowise.demo.model.User;
import com.innowise.demo.repository.CardInfoRepository;
import com.innowise.demo.repository.UserRepository;
import com.innowise.demo.security.AccessManager;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CardInfoService {
    private final CardInfoRepository cardInfoRepository;
    private final CardInfoMapper cardInfoMapper;
    private final UserRepository userRepository;
    private final AccessManager accessManager;

    private static final String CARD_CACHE = "cardCache"; // кеш отдельной карты
    private static final String ALL_CARDS_CACHE = "allCards"; // кеш всех карт

    private static final String NOT_FOUND_SUFFIX = " not found";
    private static final String PREFIX_CARDINFO_WITH_ID = "CardInfo with id ";

    @PreAuthorize("@accessManager.canAccessUser(#dto.userId, authentication)")
    @CachePut(value = CARD_CACHE, key = "#result.id")
    @CacheEvict(value = ALL_CARDS_CACHE, allEntries = true)
    public CardInfoDto save(CardInfoDto dto) {
        User user = userRepository.findById(dto.getUserId())
                .orElseThrow(
                        () -> new UserNotFoundException("User not found with id: " + dto.getUserId()));

        CardInfo entity = cardInfoMapper.toEntity(dto);
        entity.setUser(user);

        CardInfo saved = cardInfoRepository.save(entity);
        return cardInfoMapper.toDto(saved);

    }

    @PreAuthorize("@accessManager.canAccessCard(#id, authentication)")
    @Cacheable(value = CARD_CACHE, key = "#id")
    public CardInfoDto getCardInfoById(Long id) {
        CardInfo cardInfo = cardInfoRepository.findById(id)
                .orElseThrow(() -> new CardInfoNotFoundException(PREFIX_CARDINFO_WITH_ID + id + NOT_FOUND_SUFFIX));
        return cardInfoMapper.toDto(cardInfo);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @Cacheable(
            value = ALL_CARDS_CACHE,
            key = "{#page, #size}",
            unless = "!@accessManager.isAdmin(T(org.springframework.security.core.context.SecurityContextHolder).context.authentication)"
    )
    public Page<CardInfoDto> getAllCardInfos(int page, int size) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = accessManager.isAdmin(authentication);

        Page<CardInfoDto> dto;
        if (isAdmin) {
            dto = cardInfoRepository.findAll(PageRequest.of(page, size))
                    .map(cardInfoMapper::toDto);
        } else {
            String currentUser = accessManager.currentUserIdentifier(authentication)
                    .orElseThrow(() -> new AccessDeniedException("Cannot determine current user"));
            dto = cardInfoRepository.findAllByUser_EmailIgnoreCase(currentUser, PageRequest.of(page, size))
                    .map(cardInfoMapper::toDto);
        }

        if(dto.isEmpty()) throw new CardInfoNotFoundException("CardInfo list is empty");

        return dto;
    }

    @PreAuthorize("@accessManager.canAccessCard(#id, authentication)")
    @Caching(
            put = {@CachePut(value = CARD_CACHE, key = "#id")},
            evict = {@CacheEvict(value = ALL_CARDS_CACHE, allEntries = true)}
    )
    @Transactional
    public CardInfoDto updateCardInfo(Long id, CardInfoDto dto) {
        CardInfo existing = cardInfoRepository.findById(id)
                .orElseThrow(() -> new CardInfoNotFoundException(PREFIX_CARDINFO_WITH_ID + id + NOT_FOUND_SUFFIX));

        existing .setNumber(dto.getNumber());
        existing .setHolder(dto.getHolder());
        existing .setExpirationDate(dto.getExpirationDate());

        boolean isAdmin = accessManager.isAdmin(SecurityContextHolder.getContext().getAuthentication());

        if (dto.getUserId() != null) {
            User currentUser = existing.getUser();
            if (!isAdmin && (currentUser == null || !currentUser.getId().equals(dto.getUserId()))) {
                throw new AccessDeniedException("Only administrators can reassign card ownership");
            }
            if (isAdmin && (currentUser == null || !currentUser.getId().equals(dto.getUserId()))) {
                User user = userRepository.findById(dto.getUserId())
                        .orElseThrow(() -> new UserNotFoundException("User not found with id: " + dto.getUserId()));
                existing.setUser(user);
            }
        }

        return cardInfoMapper.toDto(cardInfoRepository.save(existing));
    }

    @PreAuthorize("@accessManager.canAccessCard(#id, authentication)")
    @Caching(evict = {
            @CacheEvict(value = CARD_CACHE, key = "#id"),
            @CacheEvict(value = ALL_CARDS_CACHE, allEntries = true)
    })
    @Transactional
    public void deleteCardInfo(Long id) {
        cardInfoRepository.findById(id)
                .orElseThrow(
                        () -> new CardInfoNotFoundException(PREFIX_CARDINFO_WITH_ID + id + NOT_FOUND_SUFFIX));

        cardInfoRepository.deleteById(id);


    }
}
