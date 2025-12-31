package com.innowise.demo.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.innowise.demo.dto.CardInfoDto;
import com.innowise.demo.dto.CreateUserFromTokenRequest;
import com.innowise.demo.dto.PagedUserResponse;
import com.innowise.demo.dto.UpdateUserDto;
import com.innowise.demo.dto.UserDto;
import com.innowise.demo.exception.CardAlreadyExistsException;
import com.innowise.demo.exception.UserAlreadyExistsException;
import com.innowise.demo.exception.UserNotFoundException;
import com.innowise.demo.mapper.UserMapper;
import com.innowise.demo.model.CardInfo;
import com.innowise.demo.model.User;
import com.innowise.demo.repository.CardInfoRepository;
import com.innowise.demo.repository.UserRepository;
import com.innowise.demo.client.AuthServiceClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = "users") // общий префикс для всех методов
public class UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final CardInfoRepository cardInfoRepository;
    private final AuthServiceClient authServiceClient;

    private static final String NOT_FOUND_SUFFIX = " not found!";
    private static final String USER_WITH_EMAIL = "User with email ";
    private static final String PREFIX_WITH_ID = "User with id ";


    /**
     * Создает пользователя из JWT токена.
     * Email берется из токена (для проверки уникальности),
     * остальные данные из запроса.
     * 
     * @param email email пользователя, извлеченный из JWT токена
     * @param request данные пользователя (firstName, lastName, birthDate)
     * @return созданный пользователь
     * @throws UserAlreadyExistsException если пользователь с таким email уже существует
     */
    @Caching(
        put = @CachePut(key = "#result.id"),
        evict = {
            @CacheEvict(value = "users_all", allEntries = true),
            @CacheEvict(value = "users_by_email", allEntries = true),
            @CacheEvict(value = "users", allEntries = true)
        }
    )
    public UserDto createUserFromToken(String email, CreateUserFromTokenRequest request) {
        log.info("Creating user from token in database: email={}, firstName={}, lastName={}", 
                email, request.getFirstName(), request.getLastName());
        
        // Проверка на уникальность email из токена
        if (userRepository.findByEmailNativeQuery(email).isPresent()) {
            log.warn("User creation from token failed: email {} already exists", email);
            throw new UserAlreadyExistsException(USER_WITH_EMAIL + email + " already exists");
        }

        // Создаем DTO с email из токена и данными из запроса
        UserDto userDto = new UserDto();
        userDto.setEmail(email);
        userDto.setFirstName(request.getFirstName());
        userDto.setLastName(request.getLastName());
        userDto.setBirthDate(request.getBirthDate());
        userDto.setCards(null); // Карты можно добавить позже через отдельный endpoint

        // Создаём сущность пользователя
        User entity = userMapper.toEntity(userDto);
        entity.setCards(new ArrayList<>()); // Пустой список карт

        // Hibernate сохранит пользователя
        User saved = userRepository.save(entity);
        
        log.info("User from token successfully saved to database: id={}, email={}, firstName={}, lastName={}", 
                saved.getId(), saved.getEmail(), saved.getFirstName(), saved.getLastName());

        return userMapper.toDto(saved);
    }

    @Caching(
        put = @CachePut(key = "#result.id"),
        evict = {
            @CacheEvict(value = "users_all", allEntries = true),
            @CacheEvict(value = "users_by_email", allEntries = true),
            @CacheEvict(value = "users", allEntries = true)
        }
    )
    public UserDto createUser(UserDto dto) {
        log.info("Creating user in database: email={}, firstName={}, lastName={}", 
                dto.getEmail(), dto.getFirstName(), dto.getLastName());
        
        dto.setId(null);
        if (dto.getCards() != null) {
            dto.getCards().forEach(c -> c.setId(null));
        }

        // Проверка на уникальность email
        if (userRepository.findByEmailNativeQuery(dto.getEmail()).isPresent()) {
            log.warn("User creation failed: email {} already exists", dto.getEmail());
            throw new UserAlreadyExistsException(USER_WITH_EMAIL + dto.getEmail() + " already exists");
        }

        // Создаём сущность пользователя
        User entity = userMapper.toEntity(dto);

        // Привязываем карты (старые и новые)
        List<CardInfo> cards = userMapper.updateCards(entity, dto.getCards());
        cards.forEach(c -> c.setUser(entity));
        entity.setCards(cards);

        // Hibernate сохранит и пользователя, и все его карты (CascadeType.ALL)
        User saved = userRepository.save(entity);
        
        log.info("User successfully saved to database: id={}, email={}, firstName={}, lastName={}", 
                saved.getId(), saved.getEmail(), saved.getFirstName(), saved.getLastName());

        return userMapper.toDto(saved);
    }

    //get by id
    @Cacheable(key = "#id")
    @Transactional(readOnly = true)
    public UserDto findUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(
                        () -> new UserNotFoundException(PREFIX_WITH_ID + id + NOT_FOUND_SUFFIX));

        return userMapper.toDto(user);
    }

    //@Cacheable(value = "users_all", key = "'page_' + #page + '_size_' + #size")
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

    // get by email
    @Cacheable(value = "users_by_email", key = "#email")
    @Transactional(readOnly = true)
    public UserDto getUserByEmail(String email) {
        log.debug("Searching for user with email: {}", email);
        
        // Используем native query для более надежного поиска
        // Пробуем сначала native query, затем JPQL, затем named query
        Optional<User> userOpt = userRepository.findByEmailNativeQuery(email);
        if (userOpt.isPresent()) {
            log.debug("User found using native query: {}", email);
        } else {
            log.debug("User not found using native query, trying JPQL: {}", email);
            userOpt = userRepository.findByEmailJPQL(email);
            if (userOpt.isPresent()) {
                log.debug("User found using JPQL: {}", email);
            } else {
                log.debug("User not found using JPQL, trying named query: {}", email);
                userOpt = userRepository.findByEmailNamed(email);
                if (userOpt.isPresent()) {
                    log.debug("User found using named query: {}", email);
                }
            }
        }
        
        User user = userOpt
                .orElseThrow(() -> {
                    log.error("User not found with email: {} (tried all query methods)", email);
                    return new UserNotFoundException(USER_WITH_EMAIL + email + NOT_FOUND_SUFFIX);
                });

        log.debug("User found: id={}, email={}", user.getId(), user.getEmail());
        return userMapper.toDto(user);
    }

    // get by email JPQL
    @Cacheable(value = "users_by_email", key = "#email")
    @Transactional(readOnly = true)
    public UserDto getUserByEmailJPQl(String email) {
        User user = userRepository.findByEmailJPQL(email)
                .orElseThrow(() -> new UserNotFoundException(USER_WITH_EMAIL + email + NOT_FOUND_SUFFIX));

        return userMapper.toDto(user);
    }

    // get by email Native
    @Cacheable(value = "users_by_email", key = "#email")
    @Transactional(readOnly = true)
    public UserDto getUserByEmailNative(String email) {
        User user = userRepository.findByEmailNativeQuery(email)
                .orElseThrow(() -> new UserNotFoundException(USER_WITH_EMAIL + email + NOT_FOUND_SUFFIX));

        return userMapper.toDto(user);
    }

    /**
     * Обновляет текущего пользователя (по email из токена).
     * Выполняет частичное обновление - обновляются только переданные поля.
     * Для карт автоматически формируется holder из firstName + lastName пользователя.
     * 
     * @param userEmail email пользователя из токена
     * @param dto DTO с данными для обновления (все поля опциональны)
     * @return обновленный пользователь
     * @throws UserNotFoundException если пользователь не найден
     */
//    @Caching(
//        evict = {
//            @CacheEvict(value = "users_all", allEntries = true),
//            @CacheEvict(value = "users_by_email", allEntries = true),
//            @CacheEvict(value = "users", allEntries = true)
//        }
//    )
    @Transactional
    public UserDto updateCurrentUser(String userEmail, UpdateUserDto dto) {
        log.info("Updating current user in database: email={}", userEmail);
        
        // Находим пользователя по email из токена
        Optional<User> userOpt = userRepository.findByEmailNativeQuery(userEmail);
        
        User existUser;
        if (userOpt.isPresent()) {
            existUser = userOpt.get();
            log.debug("User found in database: id={}, email={}", existUser.getId(), existUser.getEmail());
        } else {
            // Если профиль не существует, создаем его автоматически
            log.warn("User profile not found for email: {}. Creating profile automatically from update request.", userEmail);
            
            // Создаем нового пользователя с данными из запроса
            User newUser = new User();
            newUser.setEmail(userEmail);
            // Используем данные из запроса или значения по умолчанию
            newUser.setFirstName(dto.getFirstName() != null && !dto.getFirstName().isBlank() 
                    ? dto.getFirstName() : "User");
            newUser.setLastName(dto.getLastName() != null && !dto.getLastName().isBlank() 
                    ? dto.getLastName() : "");
            newUser.setBirthDate(dto.getBirthDate() != null 
                    ? dto.getBirthDate() : java.time.LocalDate.now().minusYears(18));
            newUser.setCards(new ArrayList<>());
            
            existUser = userRepository.save(newUser);
            log.info("User profile automatically created: id={}, email={}, firstName={}, lastName={}", 
                    existUser.getId(), existUser.getEmail(), existUser.getFirstName(), existUser.getLastName());
        }
        
        // Обновляем пользователя (без проверки доступа, так как это свой профиль)
        UserDto updated = updateUserInternal(existUser, dto);
        
        log.info("User successfully updated in database: id={}, email={}", updated.getId(), updated.getEmail());
        return updated;
    }

    /**
     * Обновляет пользователя по ID (только для ADMIN).
     * Админ может обновить любого пользователя без проверки доступа.
     * Выполняет частичное обновление - обновляются только переданные поля.
     * Для карт автоматически формируется holder из firstName + lastName пользователя.
     * 
     * @param id ID пользователя для обновления
     * @param dto DTO с данными для обновления (все поля опциональны)
     * @param adminEmail email админа (для логирования)
     * @return обновленный пользователь
     * @throws UserNotFoundException если пользователь не найден
     */
    @Caching(
        put = @CachePut(key = "#id"),
        evict = {
            @CacheEvict(value = "users_all", allEntries = true),
            @CacheEvict(value = "users_by_email", allEntries = true),
            @CacheEvict(value = "users", allEntries = true)
        }
    )
    @Transactional
    public UserDto updateUserByAdmin(Long id, UpdateUserDto dto, String adminEmail) {
        log.info("Admin {} updating user in database: id={}", adminEmail, id);
        
        // Получаем пользователя по ID (без проверки доступа для админа)
        User existUser = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(PREFIX_WITH_ID + id + NOT_FOUND_SUFFIX));
        
        // Обновляем пользователя (без проверки доступа, так как это админ)
        UserDto updated = updateUserInternal(existUser, dto);
        
        log.info("User successfully updated by admin in database: id={}, email={}", updated.getId(), updated.getEmail());
        return updated;
    }

    /**
     * Внутренний метод для обновления пользователя.
     * Содержит общую логику обновления полей и карт.
     * 
     * @param existUser пользователь для обновления
     * @param dto DTO с данными для обновления
     * @return обновленный пользователь
     */
    private UserDto updateUserInternal(User existUser, UpdateUserDto dto) {

        // Частичное обновление - обновляем только переданные поля
        if (dto.getFirstName() != null) {
            existUser.setFirstName(dto.getFirstName());
        }
        if (dto.getLastName() != null) {
            existUser.setLastName(dto.getLastName());
        }
        if (dto.getBirthDate() != null) {
            existUser.setBirthDate(dto.getBirthDate());
        }
        // Email не обновляется - он берется из токена и не должен изменяться через этот endpoint

        // Автоматически формируем holder для карт из firstName + lastName (после обновления полей)
        String holder = (existUser.getFirstName() != null ? existUser.getFirstName() : "") + 
                        " " + 
                        (existUser.getLastName() != null ? existUser.getLastName() : "");
        holder = holder.trim();
        
        // Если holder пустой, используем email как fallback
        if (holder.isEmpty()) {
            holder = existUser.getEmail();
        }

        if (dto.getCards() != null) {
            Map<Long, CardInfo> existingCardsMap = existUser.getCards().stream()
                    .filter(c -> c.getId() != null)
                    .collect(Collectors.toMap(CardInfo::getId, c -> c));

            // Создаем Set для отслеживания номеров карт, которые уже добавлены в этом запросе
            java.util.Set<String> processedCardNumbers = new java.util.HashSet<>();
            List<CardInfo> updatedCards = new ArrayList<>();

            for (CardInfoDto cardDto : dto.getCards()) {
                String cardNumber = cardDto.getNumber();
                
                // Проверка на дубликаты в текущем запросе
                if (processedCardNumbers.contains(cardNumber)) {
                    continue; // Пропускаем дубликат в запросе
                }
                processedCardNumbers.add(cardNumber);
                
                if (cardDto.getId() != null && existingCardsMap.containsKey(cardDto.getId())) {
                    // Существующая карта (передан ID) — обновляем поля
                    CardInfo existingCard = existingCardsMap.get(cardDto.getId());
                    
                    // Если номер карты изменился, проверяем, не занят ли новый номер
                    if (!existingCard.getNumber().equals(cardNumber)) {
                        // Проверяем, не принадлежит ли карта с таким номером другому пользователю
                        cardInfoRepository.findByNumber(cardNumber).ifPresent(otherCard -> {
                            if (!otherCard.getUser().getId().equals(existUser.getId())) {
                                throw new CardAlreadyExistsException(
                                    "Card with number " + cardNumber + " is already registered to another user");
                            }
                        });
                    }
                    
                    existingCard.setNumber(cardNumber);
                    // holder автоматически формируется из name + surname пользователя
                    existingCard.setHolder(holder);
                    existingCard.setExpirationDate(cardDto.getExpirationDate());
                    updatedCards.add(existingCard);
                } else {
                    // Карта без ID или с ID, которого нет в существующих картах
                    // Проверяем, не существует ли уже такая карта у этого пользователя
                    Optional<CardInfo> existingCardForUser = cardInfoRepository
                            .findByNumberAndUserId(cardNumber, existUser.getId());
                    
                    if (existingCardForUser.isPresent()) {
                        // Карта уже есть у этого пользователя - обновляем существующую (не создаем дубликат)
                        CardInfo existingCard = existingCardForUser.get();
                        existingCard.setHolder(holder);
                        existingCard.setExpirationDate(cardDto.getExpirationDate());
                        // Номер не меняем, так как карта уже существует
                        updatedCards.add(existingCard);
                        continue;
                    }
                    
                    // Проверка: не принадлежит ли карта другому пользователю
                    cardInfoRepository.findByNumber(cardNumber).ifPresent(otherCard -> {
                        if (!otherCard.getUser().getId().equals(existUser.getId())) {
                            throw new CardAlreadyExistsException(
                                "Card with number " + cardNumber + " is already registered to another user");
                        }
                    });
                    
                    // Создаем новую карту (если её еще нет)
                    CardInfo newCard = new CardInfo();
                    newCard.setNumber(cardNumber);
                    // holder автоматически формируется из name + surname пользователя
                    newCard.setHolder(holder);
                    newCard.setExpirationDate(cardDto.getExpirationDate());
                    newCard.setUser(existUser);
                    updatedCards.add(newCard);
                }
            }

            // Обновляем коллекцию (Hibernate удалит старые карты, которых нет)
            existUser.getCards().clear();
            existUser.getCards().addAll(updatedCards);
        }

        User savedUser = userRepository.save(existUser);
        return userMapper.toDto(savedUser);
    }

    @Caching(evict = {
            @CacheEvict(value = {"users", "users_all", "users_by_email"}, allEntries = true),
            @CacheEvict(value = "users", key = "#id")
    })
    @Transactional
    public void deleteUser(Long id) {
        // Получаем пользователя для извлечения email перед удалением
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(PREFIX_WITH_ID + id + NOT_FOUND_SUFFIX));

        String email = user.getEmail();

        // Удаляем из user-service базы данных
        userRepository.deleteById(id);

        // Удаляем из authentication-service (auth_db) для синхронизации
        // Это позволяет пользователю зарегистрироваться снова с тем же email
        if (email != null && !email.isBlank()) {
            org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UserService.class);
            log.info("Attempting to delete user {} from authentication-service after deletion from user-service", email);
            try {
                authServiceClient.deleteUser(email);
                // Успешное удаление логируется внутри AuthServiceClient
            } catch (Exception e) {
                // Логируем ошибку, но не прерываем удаление из user-service
                // Пользователь уже удален из us_db, даже если удаление из auth_db не удалось
                log.error("Failed to delete user {} from authentication-service: {}", email, e.getMessage(), e);
            }
        } else {
            org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UserService.class);
            log.warn("Cannot delete user from authentication-service: email is null or blank");
        }
    }
}
