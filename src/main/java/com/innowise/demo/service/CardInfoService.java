package com.innowise.demo.service;

import jakarta.transaction.Transactional;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import com.innowise.demo.dto.CardInfoDto;
import com.innowise.demo.exception.CardInfoNotFoundException;
import com.innowise.demo.exception.UserNotFoundException;
import com.innowise.demo.mapper.CardInfoMapper;
import com.innowise.demo.model.CardInfo;
import com.innowise.demo.model.User;
import com.innowise.demo.repository.CardInfoRepository;
import com.innowise.demo.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CardInfoService {
    private final CardInfoRepository cardInfoRepository;
    private final CardInfoMapper cardInfoMapper;
    private final UserRepository userRepository;

    private static final String CARD_CACHE = "cardCache"; // кеш отдельной карты
    private static final String ALL_CARDS_CACHE = "allCards"; // кеш всех карт

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

    @Cacheable(value = CARD_CACHE, key = "#id")
    public CardInfoDto getCardInfoById(Long id) {
        CardInfo cardInfo = cardInfoRepository.findById(id)
                .orElseThrow(() -> new CardInfoNotFoundException("CardInfo with id " + id + " not found"));
        return cardInfoMapper.toDto(cardInfo);
    }


    @Cacheable(value = ALL_CARDS_CACHE, key = "{#page, #size}")
    public Page<CardInfoDto> getAllCardInfos(int page, int size) {
         Page<CardInfoDto> dto =  cardInfoRepository.findAll(PageRequest.of(page, size))
                .map(cardInfoMapper::toDto);

         if(dto.isEmpty()) throw new CardInfoNotFoundException("CardInfo list is empty");

        return dto;
    }

    @Caching(
            put = {@CachePut(value = CARD_CACHE, key = "#id")},
            evict = {@CacheEvict(value = ALL_CARDS_CACHE, allEntries = true)}
    )
    @Transactional
    public CardInfoDto updateCardInfo(Long id, CardInfoDto dto) {
        CardInfo existing = cardInfoRepository.findById(id)
                .orElseThrow(() -> new CardInfoNotFoundException("CardInfo with id " + id + " not found"));

        existing .setNumber(dto.getNumber());
        existing .setHolder(dto.getHolder());
        existing .setExpirationDate(dto.getExpirationDate());
        if (dto.getUserId() != null && (existing.getUser() == null || !existing.getUser().getId().equals(dto.getUserId()))) {
            existing.setUser(cardInfoMapper.mapUser(dto.getUserId()));
        }
        return cardInfoMapper.toDto(cardInfoRepository.save(existing));
    }

    @Caching(evict = {
            @CacheEvict(value = CARD_CACHE, key = "#id"),
            @CacheEvict(value = ALL_CARDS_CACHE, allEntries = true)
    })
    @Transactional
    public void deleteCardInfo(Long id) {
        CardInfo card = cardInfoRepository.findById(id)
                .orElseThrow(
                        () -> new CardInfoNotFoundException("CardInfo with id " + id + " not found"));

        cardInfoRepository.deleteById(id);
    }
}
