package com.innowise.demo.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import com.innowise.demo.dto.CardInfoDto;
import com.innowise.demo.dto.PagedUserResponse;
import com.innowise.demo.dto.UserDto;
import com.innowise.demo.exception.UserAlreadyExistsException;
import com.innowise.demo.exception.UserNotFoundException;
import com.innowise.demo.mapper.UserMapper;
import com.innowise.demo.model.CardInfo;
import com.innowise.demo.model.User;
import com.innowise.demo.repository.CardInfoRepository;
import com.innowise.demo.repository.UserRepository;
import com.innowise.demo.dto.UserUpdateRequest;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = "users") // общий префикс для всех методов
public class UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final CardInfoRepository cardInfoRepository;

    private static final String NOT_FOUND_SUFFIX = " not found!";
    private static final String USER_WITH_EMAIL = "User with email ";
    private static final String PREFIX_WITH_ID = "User with id ";


    @PreAuthorize("hasRole('ADMIN')")
    @CachePut(key = "#result.id")
    @CacheEvict(value = "users_all", allEntries = true) // очищаем кэш списка
    public UserDto createUser(UserDto dto) {
        dto.setId(null);
        if (dto.getCards() != null) {
            dto.getCards().forEach(c -> c.setId(null));
        }

        String normalizedEmail = normalizeEmail(dto.getEmail());
        dto.setEmail(normalizedEmail);

        // Проверка на уникальность email
        if (userRepository.findByEmailNativeQuery(normalizedEmail).isPresent()) {
            throw new UserAlreadyExistsException(USER_WITH_EMAIL + normalizedEmail + " already exists");
        }

        // Создаём сущность пользователя
        User entity = userMapper.toEntity(dto);

        // Привязываем карты (старые и новые)
        List<CardInfo> cards = userMapper.updateCards(entity, dto.getCards());
        cards.forEach(c -> c.setUser(entity));
        entity.setCards(cards);

        // Hibernate сохранит и пользователя, и все его карты (CascadeType.ALL)
        User saved = userRepository.save(entity);

        return userMapper.toDto(saved);
    }

    //get by id
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @Cacheable(key = "#id")
    @Transactional(readOnly = true)
    public UserDto findUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(
                        () -> new UserNotFoundException(PREFIX_WITH_ID + id + NOT_FOUND_SUFFIX));

        ensureCurrentUserCanAccessUser(user, requireAuthentication());
        return userMapper.toDto(user);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Cacheable(value = "users_all", key = "'page_' + #page + '_size_' + #size")
    @Transactional(readOnly = true)//длф решения проблемы ленивой инициализации
    public PagedUserResponse findAllUsers(int page, int size) {
        Page<User> users = userRepository.findAll(PageRequest.of(page, size));

        List<UserDto> dtos = users.stream()
                .map(userMapper::toDto)
                .toList();

        return new PagedUserResponse(
                dtos,
                users.getNumber(),
                users.getSize(),
                users.getTotalElements(),
                users.getTotalPages()
        );
    }

    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @Transactional
    public UserDto getCurrentUser(Authentication authentication) {
        Authentication auth = authentication != null ? authentication : requireAuthentication();
        String identifier = resolveCurrentUserIdentifier(auth);
        String normalizedIdentifier = normalizeEmail(identifier);
        User user = userRepository.findByEmailNamed(normalizedIdentifier)
                .orElseGet(() -> autoProvisionUser(auth, normalizedIdentifier));
        if (user.getCards() != null) {
            user.getCards().size();
        }
        ensureCurrentUserCanAccessUser(user, auth);
        return userMapper.toDto(user);
    }

    // get by email
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @Cacheable(value = "users_by_email", key = "#email")
    @Transactional(readOnly = true)
    public UserDto getUserByEmail(String email) {
        String normalizedEmail = normalizeEmail(email);
        User user = userRepository.findByEmailNamed(normalizedEmail)
                .orElseThrow(() -> new UserNotFoundException(USER_WITH_EMAIL + normalizedEmail + NOT_FOUND_SUFFIX));

        ensureCurrentUserCanAccessUser(user, requireAuthentication());
        return userMapper.toDto(user);
    }

    // get by email JPQL
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @Cacheable(value = "users_by_email", key = "#email")
    @Transactional(readOnly = true)
    public UserDto getUserByEmailJPQl(String email) {
        String normalizedEmail = normalizeEmail(email);
        User user = userRepository.findByEmailJPQL(normalizedEmail)
                .orElseThrow(() -> new UserNotFoundException(USER_WITH_EMAIL + normalizedEmail + NOT_FOUND_SUFFIX));

        ensureCurrentUserCanAccessUser(user, requireAuthentication());
        return userMapper.toDto(user);
    }

    // get by email Native
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @Cacheable(value = "users_by_email", key = "#email")
    @Transactional(readOnly = true)
    public UserDto getUserByEmailNative(String email) {
        String normalizedEmail = normalizeEmail(email);
        User user = userRepository.findByEmailNativeQuery(normalizedEmail)
                .orElseThrow(() -> new UserNotFoundException(USER_WITH_EMAIL + normalizedEmail + NOT_FOUND_SUFFIX));

        ensureCurrentUserCanAccessUser(user, requireAuthentication());
        return userMapper.toDto(user);
    }

    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @CachePut(key = "#id")
    @CacheEvict(value = "users_all", allEntries = true)
    @Transactional
    public UserDto updateUser(Long id, UserUpdateRequest dto) {
        User existUser = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(PREFIX_WITH_ID + id + NOT_FOUND_SUFFIX));

        ensureCurrentUserCanAccessUser(existUser, requireAuthentication());

        if (dto.getUserId() != null && !dto.getUserId().equals(id)) {
            throw new AccessDeniedException("Cannot update another user");
        }

        if (dto.getName() != null) {
            if (dto.getName().isBlank()) {
                throw new AccessDeniedException("Name must not be blank");
            }
            existUser.setName(dto.getName());
        }

        if (dto.getSurname() != null) {
            if (dto.getSurname().isBlank()) {
                throw new AccessDeniedException("Surname must not be blank");
            }
            existUser.setSurname(dto.getSurname());
        }

        if (dto.getBirthDate() != null) {
            existUser.setBirthDate(dto.getBirthDate());
        }

        if (dto.getEmail() != null) {
            if (dto.getEmail().isBlank()) {
                throw new AccessDeniedException("Email must not be blank");
            }
            String normalizedEmail = normalizeEmail(dto.getEmail());
        if (!normalizedEmail.equalsIgnoreCase(existUser.getEmail())
                    && userRepository.findByEmailNativeQuery(normalizedEmail).isPresent()) {
                throw new UserAlreadyExistsException(USER_WITH_EMAIL + normalizedEmail + " already exists");
            }
            existUser.setEmail(normalizedEmail);
        }

        if (dto.getCards() != null) {
            Map<Long, CardInfo> existingCardsMap = existUser.getCards().stream()
                    .filter(c -> c.getId() != null)
                    .collect(Collectors.toMap(CardInfo::getId, c -> c));

            List<CardInfo> updatedCards = new ArrayList<>();

            for (CardInfoDto cardDto : dto.getCards()) {
                Long ownerId = cardDto.getUserId() != null ? cardDto.getUserId() : id;
                if (!ownerId.equals(id)) {
                    throw new AccessDeniedException("Cannot assign card to another user");
                }
                cardDto.setUserId(id);

                if (cardDto.getId() != null && existingCardsMap.containsKey(cardDto.getId())) {
                    // Существующая карта — обновляем поля
                    CardInfo existingCard = existingCardsMap.get(cardDto.getId());
                    existingCard.setNumber(cardDto.getNumber());
                    existingCard.setHolder(cardDto.getHolder());
                    existingCard.setExpirationDate(cardDto.getExpirationDate());
                    updatedCards.add(existingCard);
                } else {
                    // Новая карта — создаём и привязываем к пользователю
                    CardInfo newCard = new CardInfo();
                    newCard.setNumber(cardDto.getNumber());
                    newCard.setHolder(cardDto.getHolder());
                    newCard.setExpirationDate(cardDto.getExpirationDate());
                    newCard.setUser(existUser);
                    updatedCards.add(newCard);
                }
            }

            // Обновляем коллекцию (Hibernate удалит старые карты, которых нет)
            existUser.getCards().clear();
            existUser.getCards().addAll(updatedCards);
        }

        return userMapper.toDto(userRepository.save(existUser));
    }

    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    @CacheEvict(value = "users_all", allEntries = true)
    @CachePut(key = "#id")
    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(PREFIX_WITH_ID + id + NOT_FOUND_SUFFIX));

        ensureCurrentUserCanAccessUser(user, requireAuthentication());
        userRepository.deleteById(id);
    }

    private Authentication requireAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new AccessDeniedException("Authentication required");
        }
        return authentication;
    }

    private void ensureCurrentUserCanAccessUser(User user, Authentication authentication) {
        if (isAdmin(authentication)) {
            return;
        }
        if (user == null) {
            throw new AccessDeniedException("Access denied");
        }
        String currentIdentifier = resolveCurrentUserIdentifier(authentication);
        String userEmail = user.getEmail();
        if (userEmail == null || !userEmail.equalsIgnoreCase(currentIdentifier)) {
            throw new AccessDeniedException("Access denied");
        }
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }

    private String resolveCurrentUserIdentifier(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            Jwt jwt = jwtAuthenticationToken.getToken();
            String email = jwt.getClaimAsString("email");
            if (email != null && !email.isBlank()) {
                return normalizeEmail(email);
            }
            String preferredUsername = jwt.getClaimAsString("preferred_username");
            if (preferredUsername != null && !preferredUsername.isBlank()) {
                return normalizeEmail(preferredUsername);
            }
            String subject = jwt.getSubject();
            if (subject != null && !subject.isBlank()) {
                return normalizeEmail(subject);
            }
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            return normalizeEmail(userDetails.getUsername());
        }

        String name = authentication.getName();
        if (name != null && !name.isBlank()) {
            return normalizeEmail(name);
        }

        throw new AccessDeniedException("Cannot determine current user");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected User autoProvisionUser(Authentication authentication, String email) {
        User user = new User();
        user.setEmail(normalizeEmail(email));

        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            Jwt jwt = jwtAuthenticationToken.getToken();
            String givenName = jwt.getClaimAsString("given_name");
            String familyName = jwt.getClaimAsString("family_name");
            if (givenName != null && !givenName.isBlank()) {
                user.setName(givenName);
            }
            if (familyName != null && !familyName.isBlank()) {
                user.setSurname(familyName);
            }
        }

        return userRepository.save(user);
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase();
    }
}
