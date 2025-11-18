package com.innowise.demo.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.innowise.demo.client.AuthServiceClient;
import com.innowise.demo.client.dto.UpdateUserProfileRequest;
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
@SuppressWarnings("null")
public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final CardInfoRepository cardInfoRepository;
    private final AuthServiceClient authServiceClient;

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

    /**
     * Создание пользователя для синхронизации из authentication-service
     * Не требует роли ADMIN, используется внутренний API ключ
     */
    @CachePut(key = "#result.id")
    @CacheEvict(value = "users_all", allEntries = true)
    public UserDto syncUser(UserDto dto) {
        dto.setId(null);
        if (dto.getCards() != null) {
            dto.getCards().forEach(c -> c.setId(null));
        }

        String normalizedEmail = normalizeEmail(dto.getEmail());
        dto.setEmail(normalizedEmail);

        // Проверка на уникальность email
        if (userRepository.findByEmailNativeQuery(normalizedEmail).isPresent()) {
            // Пользователь уже существует, возвращаем его
            User existingUser = userRepository.findByEmailNativeQuery(normalizedEmail)
                    .orElseThrow(() -> new UserNotFoundException(USER_WITH_EMAIL + normalizedEmail + NOT_FOUND_SUFFIX));
            return userMapper.toDto(existingUser);
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

        // Email нельзя изменить после регистрации - игнорируем поле email из запроса
        // if (dto.getEmail() != null) { ... }

        // birthDate можно изменить пользователем один раз (если она дефолтная или не установлена)
        // После явной установки пользователем, только админ может изменить
        if (dto.getBirthDate() != null) {
            Authentication auth = requireAuthentication();
            boolean isAdmin = isAdmin(auth);
            
            LocalDate currentBirthDate = existUser.getBirthDate();
            if (currentBirthDate != null && !isAdmin) {
                // Проверяем, является ли текущая дата дефолтной (примерно 18 лет назад)
                // Дефолтная дата устанавливается в UserServiceClient как LocalDate.now().minusYears(18)
                // Используем более широкий диапазон проверки (±365 дней), чтобы учесть возможные задержки при регистрации
                LocalDate now = LocalDate.now();
                LocalDate defaultBirthDateMin = now.minusYears(19); // Минимум 17 лет назад
                LocalDate defaultBirthDateMax = now.minusYears(17); // Максимум 19 лет назад
                
                // Если текущая дата находится в диапазоне дефолтных значений (17-19 лет назад),
                // разрешаем пользователю установить реальную дату рождения один раз
                // Если дата выходит за этот диапазон, значит она была явно установлена пользователем ранее
                if (currentBirthDate.isBefore(defaultBirthDateMin) || currentBirthDate.isAfter(defaultBirthDateMax)) {
                    throw new AccessDeniedException("Birth date can only be changed by admin");
                }
            }
            
            // Разрешаем установку даты рождения, если:
            // 1. Дата не установлена (null)
            // 2. Дата находится в диапазоне дефолтных значений (17-19 лет назад) - пользователь устанавливает реальную дату в первый раз
            // 3. Пользователь - админ
            existUser.setBirthDate(dto.getBirthDate());
        }

        if (dto.getCards() != null) {
            // Генерируем holder из имени и фамилии пользователя
            String cardHolder = generateCardHolder(existUser.getName(), existUser.getSurname());
            
            Map<Long, CardInfo> existingCardsById = existUser.getCards().stream()
                    .filter(c -> c.getId() != null)
                    .collect(Collectors.toMap(CardInfo::getId, c -> c));

            Map<String, CardInfo> existingCardsBySignature = existUser.getCards().stream()
                    .collect(Collectors.toMap(this::cardSignature, c -> c, (left, right) -> left, HashMap::new));

            Set<String> seenSignatures = new HashSet<>();
            List<CardInfo> updatedCards = new ArrayList<>();

            for (CardInfoDto cardDto : dto.getCards()) {
                // Игнорируем userId из запроса, всегда используем id пользователя
                cardDto.setUserId(id);
                // Игнорируем holder из запроса, всегда используем имя и фамилию пользователя
                cardDto.setHolder(cardHolder);

                String signature = cardSignature(cardDto);
                if (seenSignatures.contains(signature)) {
                    continue; // skip duplicates in the same request
                }
                seenSignatures.add(signature);

                if (cardDto.getId() != null && existingCardsById.containsKey(cardDto.getId())) {
                    CardInfo existingCard = existingCardsById.get(cardDto.getId());
                    existingCard.setNumber(cardDto.getNumber());
                    existingCard.setHolder(cardHolder); // Используем автоматически сгенерированный holder
                    existingCard.setExpirationDate(cardDto.getExpirationDate());
                    updatedCards.add(existingCard);
                    existingCardsBySignature.put(signature, existingCard);
                } else if (existingCardsBySignature.containsKey(signature)) {
                    CardInfo existingCard = existingCardsBySignature.get(signature);
                    existingCard.setNumber(cardDto.getNumber());
                    existingCard.setHolder(cardHolder); // Используем автоматически сгенерированный holder
                    existingCard.setExpirationDate(cardDto.getExpirationDate());
                    updatedCards.add(existingCard);
                } else {
                    CardInfo newCard = new CardInfo();
                    newCard.setNumber(cardDto.getNumber());
                    newCard.setHolder(cardHolder); // Используем автоматически сгенерированный holder
                    newCard.setExpirationDate(cardDto.getExpirationDate());
                    newCard.setUser(existUser);
                    updatedCards.add(newCard);
                    existingCardsBySignature.put(signature, newCard);
                }
            }

            existUser.getCards().clear();
            existUser.getCards().addAll(updatedCards);
        }

        User saved = userRepository.save(existUser);
        // Синхронизируем только имя и фамилию, email (логин) не изменяем
        // Используем текущий email пользователя, так как email не может быть изменен
        synchronizeAuthenticationProfile(saved.getEmail(), saved);
        return userMapper.toDto(saved);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @CacheEvict(value = {"users", "users_all"}, key = "#id", allEntries = true)
    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(PREFIX_WITH_ID + id + NOT_FOUND_SUFFIX));

        String userEmail = user.getEmail();
        
        // Удаление из базы данных user-service
        userRepository.deleteById(id);
        log.info("Deleted user {} (id: {}) from user-service database", userEmail, id);
        
        // Удаление из authentication-service (auth_db и Keycloak)
        if (userEmail != null && !userEmail.isBlank()) {
            try {
                authServiceClient.deleteUser(userEmail);
            } catch (Exception e) {
                log.error("Failed to delete user {} from authentication-service: {}", userEmail, e.getMessage(), e);
                // Не выбрасываем исключение, чтобы не прерывать удаление в user-service
            }
        }
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

    private void synchronizeAuthenticationProfile(String email, User user) {
        try {
            // Логин (email) не изменяем - передаем одинаковый email для currentLogin и newLogin
            // Синхронизируем только имя и фамилию
            String firstName = resolveProfileName(user.getName(), user.getEmail());
            String lastName = resolveProfileName(user.getSurname(), user.getEmail());
            log.info("Synchronizing user profile for {}: firstName={}, lastName={}", email, firstName, lastName);
            authServiceClient.updateUserProfile(new UpdateUserProfileRequest(
                    email, // currentLogin - текущий логин (не изменяется)
                    email, // newLogin - новый логин (остается тем же)
                    firstName,
                    lastName
            ));
            log.info("Successfully synchronized user profile for {}", email);
        } catch (IllegalStateException ex) {
            log.error("Failed to synchronize user profile for {}: {}", email, ex.getMessage(), ex);
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to synchronize user profile for {}: {}", email, ex.getMessage(), ex);
            // Не выбрасываем исключение, чтобы не прерывать обновление в user-service
        }
    }

    private String resolveProfileName(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private String cardSignature(CardInfo card) {
        return cardSignature(card.getNumber(), card.getHolder(), card.getExpirationDate());
    }

    private String cardSignature(CardInfoDto dto) {
        return cardSignature(dto.getNumber(), dto.getHolder(), dto.getExpirationDate());
    }

    private String cardSignature(String number, String holder, LocalDate expirationDate) {
        return (number == null ? "" : number.trim()) + "|" +
                (holder == null ? "" : holder.trim()) + "|" +
                (expirationDate == null ? "" : expirationDate.toString());
    }

    /**
     * Генерирует holder для карточки из имени и фамилии пользователя
     */
    private String generateCardHolder(String firstName, String lastName) {
        String name = firstName != null ? firstName.trim() : "";
        String surname = lastName != null ? lastName.trim() : "";
        if (name.isEmpty() && surname.isEmpty()) {
            return "";
        }
        if (name.isEmpty()) {
            return surname;
        }
        if (surname.isEmpty()) {
            return name;
        }
        return name + " " + surname;
    }
}
