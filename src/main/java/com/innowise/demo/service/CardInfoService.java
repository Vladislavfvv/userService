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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import com.innowise.demo.dto.CardInfoDto;
import com.innowise.demo.exception.CardInfoNotFoundException;
import com.innowise.demo.exception.UserNotFoundException;
import com.innowise.demo.mapper.CardInfoMapper;
import com.innowise.demo.model.CardInfo;
import com.innowise.demo.model.User;
import com.innowise.demo.repository.CardInfoRepository;
import com.innowise.demo.repository.UserRepository;
import com.innowise.demo.util.SecurityUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CardInfoService {
    private final CardInfoRepository cardInfoRepository;
    private final CardInfoMapper cardInfoMapper;
    private final UserRepository userRepository;

    private static final String CARD_CACHE = "cardCache"; // кеш отдельной карты
    private static final String ALL_CARDS_CACHE = "allCards"; // кеш всех карт

    private static final String NOT_FOUND_SUFFIX = " not found";
    private static final String PREFIX_CARDINFO_WITH_ID = "CardInfo with id ";

    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @CachePut(value = CARD_CACHE, key = "#result.id")
    @CacheEvict(value = ALL_CARDS_CACHE, allEntries = true)
    public CardInfoDto save(CardInfoDto dto) {
        User user = userRepository.findById(dto.getUserId())
                .orElseThrow(
                        () -> new UserNotFoundException("User not found with id: " + dto.getUserId()));

        ensureCurrentUserCanAccessUser(user);

        CardInfo entity = cardInfoMapper.toEntity(dto);
        entity.setUser(user);

        CardInfo saved = cardInfoRepository.save(entity);
        return cardInfoMapper.toDto(saved);

    }

    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @Cacheable(value = CARD_CACHE, key = "#id")
    public CardInfoDto getCardInfoById(Long id) {
        CardInfo cardInfo = cardInfoRepository.findById(id)
                .orElseThrow(() -> new CardInfoNotFoundException(PREFIX_CARDINFO_WITH_ID + id + NOT_FOUND_SUFFIX));

        ensureCurrentUserCanAccessCard(cardInfo);
        return cardInfoMapper.toDto(cardInfo);
    }

    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    // Кеширование отключено для этого метода, так как Page<CardInfoDto> не может быть корректно десериализован из Redis
    // из-за проблем с полиморфной валидацией Jackson для PageImpl
    // @Cacheable(value = ALL_CARDS_CACHE,
    //         key = "{ T(org.springframework.security.core.context.SecurityContextHolder).getContext().getAuthentication()?.name ?: 'anonymous', #page, #size }",
    //         unless = "#result == null")
    public Page<CardInfoDto> getAllCardInfos(int page, int size) {
        Authentication authentication = requireAuthentication();
        boolean isAdmin = isAdmin(authentication);

        try {
            if (isAdmin) {
                log.debug("Admin user requested all cards");
                Page<CardInfoDto> dto = cardInfoRepository.findAll(PageRequest.of(page, size)).map(cardInfoMapper::toDto);
                return dto;
            } else {
                String userEmail = resolveCurrentUserIdentifier(authentication);
                log.debug("User {} requested their cards", userEmail);
                Page<CardInfoDto> dto = cardInfoRepository.findAllByUser_EmailIgnoreCase(userEmail,
                        PageRequest.of(page, size))
                        .map(cardInfoMapper::toDto);
                log.debug("Found {} cards for user {}", dto.getTotalElements(), userEmail);
        return dto;
            }
        } catch (Exception e) {
            log.error("Error getting card infos for page {} size {}", page, size, e);
            throw e;
        }
    }

    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @Caching(
            put = {@CachePut(value = CARD_CACHE, key = "#id")},
            evict = {@CacheEvict(value = ALL_CARDS_CACHE, allEntries = true)}
    )
    @Transactional
    public CardInfoDto updateCardInfo(Long id, CardInfoDto dto) {
        CardInfo existing = cardInfoRepository.findById(id)
                .orElseThrow(() -> new CardInfoNotFoundException(PREFIX_CARDINFO_WITH_ID + id + NOT_FOUND_SUFFIX));

        ensureCurrentUserCanAccessCard(existing);

        existing .setNumber(dto.getNumber());
        existing .setHolder(dto.getHolder());
        existing .setExpirationDate(dto.getExpirationDate());

        Authentication authentication = requireAuthentication();
        boolean isAdmin = isAdmin(authentication);

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

    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @Caching(evict = {
            @CacheEvict(value = CARD_CACHE, key = "#id"),
            @CacheEvict(value = ALL_CARDS_CACHE, allEntries = true)
    })
    @Transactional
    public void deleteCardInfo(Long id) {
        CardInfo cardInfo = cardInfoRepository.findById(id)
                .orElseThrow(() -> new CardInfoNotFoundException(PREFIX_CARDINFO_WITH_ID + id + NOT_FOUND_SUFFIX));

        ensureCurrentUserCanAccessCard(cardInfo);

        cardInfoRepository.delete(cardInfo);

    }

    private Authentication requireAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new AccessDeniedException("Authentication required");
        }
        return authentication;
    }

    private void ensureCurrentUserCanAccessUser(User user) {
        Authentication authentication = requireAuthentication();
        if (isAdmin(authentication)) {
            return;
        }

        String currentIdentifier = resolveCurrentUserIdentifier(authentication);
        if (user == null || user.getEmail() == null
                || !user.getEmail().equalsIgnoreCase(currentIdentifier)) {
            throw new AccessDeniedException("Access denied");
        }
    }

    private void ensureCurrentUserCanAccessCard(CardInfo cardInfo) {
        if (cardInfo == null) {
            throw new AccessDeniedException("Access denied");
        }
        ensureCurrentUserCanAccessUser(cardInfo.getUser());
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }

    private String resolveCurrentUserIdentifier(Authentication authentication) {
        // Используем SecurityUtils для единообразия с другими частями приложения
        try {
            return SecurityUtils.getEmailFromToken(authentication);
        } catch (IllegalStateException e) {
            // Если не удалось извлечь email через SecurityUtils, пробуем альтернативные способы
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            Jwt jwt = jwtAuthenticationToken.getToken();
                // Fallback на другие claims
            String email = jwt.getClaimAsString("email");
            if (email != null && !email.isBlank()) {
                return email;
            }
            String preferredUsername = jwt.getClaimAsString("preferred_username");
            if (preferredUsername != null && !preferredUsername.isBlank()) {
                return preferredUsername;
            }
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }

        String name = authentication.getName();
        if (name != null && !name.isBlank()) {
            return name;
        }

            throw new AccessDeniedException("Cannot determine current user: " + e.getMessage());
        }
    }
}
