package com.innowise.demo.service;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
    
    @PersistenceContext
    private EntityManager entityManager;

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
        
        // Проверка на существование пользователя
        Optional<User> existingUserOpt = userRepository.findByEmailNativeQuery(email);
        
        if (existingUserOpt.isPresent()) {
            // Пользователь уже существует - обновляем его данные вместо выбрасывания исключения
            log.info("User with email {} already exists, updating profile data", email);
            User existingUser = existingUserOpt.get();
            
            // Обновляем данные профиля
            existingUser.setFirstName(request.getFirstName());
            existingUser.setLastName(request.getLastName());
            existingUser.setBirthDate(request.getBirthDate());
            
            // Сохраняем обновленного пользователя
            User saved = userRepository.save(existingUser);
            
            log.info("User profile updated successfully: id={}, email={}, firstName={}, lastName={}", 
                    saved.getId(), saved.getEmail(), saved.getFirstName(), saved.getLastName());
            
            return userMapper.toDto(saved);
        }

        // Пользователь не существует - создаем нового
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
        // Сортировка на уровне базы данных по ID через Sort в PageRequest
        Page<User> users = userRepository.findAll(PageRequest.of(page, size, Sort.by("id").ascending()));

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
    @Caching(
        evict = {
            @CacheEvict(value = "users_all", allEntries = true),
            @CacheEvict(value = "users_by_email", allEntries = true),
            @CacheEvict(value = "users", allEntries = true),
            @CacheEvict(value = "users_by_email", key = "#userEmail") // Инвалидируем кеш для конкретного email
        }
    )
    @Transactional
    public UserDto updateCurrentUser(String userEmail, UpdateUserDto dto) {
        log.info("Updating current user in database: email={}", userEmail);
        
        // Находим пользователя по email из токена
        User existUser = userRepository.findByEmailNativeQuery(userEmail)
                .orElseThrow(() -> new UserNotFoundException(USER_WITH_EMAIL + userEmail + NOT_FOUND_SUFFIX));
        
        log.debug("User found in database: id={}, email={}", existUser.getId(), existUser.getEmail());
        
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
            // Сохраняем ID пользователя в финальную переменную для использования в лямбда-выражениях
            final Long existUserId = existUser.getId();
            
            // Перезагружаем пользователя из БД, чтобы получить актуальный список карт
            // Это гарантирует, что мы работаем с актуальными данными, а не с кешем
            entityManager.refresh(existUser);
            log.debug("Refreshed user from database before card update. User ID: {}, Cards count: {}", 
                     existUserId, existUser.getCards() != null ? existUser.getCards().size() : 0);
            
            Map<Long, CardInfo> existingCardsMap = existUser.getCards().stream()
                    .filter(c -> c.getId() != null)
                    .collect(Collectors.toMap(CardInfo::getId, c -> c));

            // Создаем Set для отслеживания номеров карт, которые уже добавлены в этом запросе
            java.util.Set<String> processedCardNumbers = new java.util.HashSet<>();
            List<CardInfo> updatedCards = new ArrayList<>();
            
            log.debug("Processing {} cards from DTO. Existing cards in DB: {}", 
                      dto.getCards().size(), existingCardsMap.size());

            // Если отправлен пустой список карт, удаляем все карты пользователя сразу
            if (dto.getCards().isEmpty()) {
                log.info("Received empty cards list in DTO. Deleting all existing cards for user ID: {}", 
                         existUser.getId());
                
                // Принудительно загружаем коллекцию карт (если она lazy)
                if (existUser.getCards() != null) {
                    existUser.getCards().size(); // Инициализируем коллекцию
                }
                
                // Удаляем все карты из БД
                if (!existUser.getCards().isEmpty()) {
                    List<CardInfo> allCardsToRemove = new ArrayList<>(existUser.getCards());
                    List<Long> cardIdsToDelete = allCardsToRemove.stream()
                            .map(CardInfo::getId)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
                    
                    log.info("Deleting all {} cards from database. Card IDs: {}", 
                            allCardsToRemove.size(), cardIdsToDelete);
                    
                    // Используем явный SQL DELETE через @Modifying @Query для гарантированного удаления
                    int deletedCount = cardInfoRepository.deleteByIds(cardIdsToDelete);
                    cardInfoRepository.flush();
                    
                    // Принудительно синхронизируем удаление с БД через EntityManager
                    // Это гарантирует, что DELETE запросы будут выполнены немедленно
                    entityManager.flush();
                    
                    // Очищаем кеш Hibernate для удаленных карт
                    allCardsToRemove.forEach(entityManager::detach);
                    
                    log.info("Deleted {} cards from database using explicit DELETE query. Card IDs: {}", 
                            deletedCount, cardIdsToDelete);
                }
                
                // Очищаем коллекцию пользователя
                existUser.getCards().clear();
                
                // Пропускаем обработку карт, так как список пустой
                updatedCards = new ArrayList<>();
                
                // После удаления карт перезагружаем пользователя из БД, чтобы получить актуальное состояние
                // Это гарантирует, что при сохранении не будет попыток загрузить удаленные карты
                final Long userId = existUser.getId();
                entityManager.detach(existUser); // Отсоединяем текущий объект
                User reloadedUser = userRepository.findById(userId)
                        .orElseThrow(() -> new UserNotFoundException(PREFIX_WITH_ID + userId + NOT_FOUND_SUFFIX));
                // Принудительно загружаем коллекцию карт (должна быть пустой после удаления)
                if (reloadedUser.getCards() != null) {
                    reloadedUser.getCards().size(); // Инициализируем коллекцию
                }
                log.debug("Reloaded user from database after card deletion. User ID: {}, Cards count: {}", 
                         reloadedUser.getId(), reloadedUser.getCards() != null ? reloadedUser.getCards().size() : 0);
                
                // После удаления всех карт и перезагрузки пользователя, сразу возвращаем результат
                // без дальнейшей обработки, так как карт больше нет
                UserDto userDto = userMapper.toDto(reloadedUser);
                log.info("User successfully updated after deleting all cards. User ID: {}, email: {}", 
                        userDto.getId(), userDto.getEmail());
                return userDto;
            } else {
                // Обрабатываем карты из DTO
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
                            if (!otherCard.getUser().getId().equals(existUserId)) {
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
                        if (!otherCard.getUser().getId().equals(existUserId)) {
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
            }
            
            // Явно сохраняем все обновленные карты в БД перед обновлением коллекции
            // Это гарантирует, что изменения существующих карт будут сохранены
            // saveAll() вернет сохраненные сущности с обновленными данными
            List<CardInfo> savedCards = cardInfoRepository.saveAll(updatedCards);
            
            // Принудительно синхронизируем изменения с БД (flush)
            // Это гарантирует, что все изменения карт будут закоммичены до обновления пользователя
            cardInfoRepository.flush();
            log.info("Flushed card updates to database. Saved {} cards. Card IDs: {}", 
                    savedCards.size(),
                    savedCards.stream().map(CardInfo::getId).filter(Objects::nonNull).collect(Collectors.toList()));
            
            // Определяем карты, которые нужно удалить (те, что были у пользователя, но отсутствуют в новом списке)
            Set<Long> savedCardIds = savedCards.stream()
                    .map(CardInfo::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            
            log.info("Saved card IDs after saveAll: {}", savedCardIds);
            
            // Перезагружаем пользователя еще раз, чтобы получить актуальный список карт после сохранения
            // НО: если мы только что удалили все карты (пустой список), то refresh может вернуть их обратно
            // Поэтому делаем refresh только если список не был пустым
            if (!dto.getCards().isEmpty()) {
                entityManager.refresh(existUser);
                
                // Принудительно загружаем коллекцию карт (если она lazy)
                // Это гарантирует, что мы получим все карты из БД
                if (existUser.getCards() != null) {
                    existUser.getCards().size(); // Инициализируем коллекцию
                }
                
                // Определяем карты, которые нужно удалить (те, что были у пользователя, но отсутствуют в новом списке)
                List<CardInfo> cardsToRemove = existUser.getCards().stream()
                        .filter(card -> card.getId() != null && !savedCardIds.contains(card.getId()))
                        .collect(Collectors.toList());
                
                log.info("Cards to remove: {} (saved card IDs: {}, existing card IDs: {})", 
                        cardsToRemove.size(), 
                        savedCardIds,
                        existUser.getCards().stream()
                                .map(CardInfo::getId)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList()));
                
                // Удаляем карты, которых больше нет в новом списке
                if (!cardsToRemove.isEmpty()) {
                    List<Long> cardIdsToDelete = cardsToRemove.stream()
                            .map(CardInfo::getId)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
                    
                    log.info("Deleting {} cards from database using explicit DELETE query. Card IDs: {}", 
                            cardIdsToDelete.size(), cardIdsToDelete);
                    
                    // Используем явный SQL DELETE через @Modifying @Query для гарантированного удаления
                    int deletedCount = cardInfoRepository.deleteByIds(cardIdsToDelete);
                    cardInfoRepository.flush(); // Синхронизируем удаление с БД
                    
                    // Принудительно синхронизируем удаление с БД через EntityManager
                    entityManager.flush();
                    
                    // Очищаем кеш Hibernate для удаленных карт
                    cardsToRemove.forEach(entityManager::detach);
                    
                    log.info("Deleted {} cards from database using explicit DELETE query. Card IDs: {}", 
                            deletedCount, cardIdsToDelete);
                    
                    // После удаления карт перезагружаем пользователя из БД, чтобы получить актуальное состояние
                    // Это гарантирует, что коллекция existUser.getCards() не содержит удаленных карт
                    // Сохраняем ID пользователя в финальную переменную для использования в лямбда-выражениях
                    final Long userIdForReload = existUser.getId();
                    entityManager.detach(existUser); // Отсоединяем текущий объект
                    User reloadedUserAfterDeletion = userRepository.findById(userIdForReload)
                            .orElseThrow(() -> new UserNotFoundException(PREFIX_WITH_ID + userIdForReload + NOT_FOUND_SUFFIX));
                    // Принудительно загружаем коллекцию карт (должна быть без удаленных карт)
                    if (reloadedUserAfterDeletion.getCards() != null) {
                        reloadedUserAfterDeletion.getCards().size(); // Инициализируем коллекцию
                    }
                    log.debug("Reloaded user from database after card deletion. User ID: {}, Cards count: {}", 
                             reloadedUserAfterDeletion.getId(), reloadedUserAfterDeletion.getCards() != null ? reloadedUserAfterDeletion.getCards().size() : 0);
                    
                    // Обновляем ссылку на пользователя после удаления карт
                    existUser = reloadedUserAfterDeletion;
                } else {
                    log.debug("No cards to remove. All existing cards are in the new list.");
                }
            }
            
            // Обновляем коллекцию пользователя
            // Вместо clear() и addAll() используем прямое обновление связей
            // Это гарантирует, что Hibernate правильно отследит изменения
            existUser.getCards().clear();
            existUser.getCards().addAll(savedCards);
            
            // Устанавливаем обратную связь для всех карт
            // Используем финальную переменную для ссылки на пользователя
            final User finalExistUser = existUser;
            savedCards.forEach(card -> card.setUser(finalExistUser));
        }

        // Сохраняем пользователя и принудительно синхронизируем с БД
        User savedUser = userRepository.save(existUser);
        userRepository.flush(); // Гарантируем, что все изменения пользователя сохранены
        log.debug("Flushed user updates to database. User ID: {}", savedUser.getId());
        
        // Очищаем кеш Hibernate первого уровня для этого пользователя
        // Это гарантирует, что последующие запросы будут читать данные из БД, а не из кеша
        entityManager.detach(savedUser);
        
        // Очищаем весь кеш первого уровня перед перезагрузкой
        // Это гарантирует, что мы получим актуальные данные из БД, включая удаленные карты
        entityManager.clear();
        
        // Перезагружаем пользователя из БД, чтобы получить актуальные данные (включая карты)
        // Это гарантирует, что мы возвращаем данные, которые реально сохранены в БД
        User refreshedUser = userRepository.findById(savedUser.getId())
                .orElseThrow(() -> new UserNotFoundException(PREFIX_WITH_ID + savedUser.getId() + NOT_FOUND_SUFFIX));
        
        // Принудительно загружаем коллекцию карт (если она lazy)
        if (refreshedUser.getCards() != null) {
            refreshedUser.getCards().size(); // Инициализируем коллекцию
        }
        
        log.info("Reloaded user from database. User ID: {}, Cards count: {}", 
                  refreshedUser.getId(), refreshedUser.getCards() != null ? refreshedUser.getCards().size() : 0);
        
        // Если карты все еще есть после удаления, логируем предупреждение
        if (refreshedUser.getCards() != null && !refreshedUser.getCards().isEmpty()) {
            log.warn("WARNING: Cards still present after deletion! User ID: {}, Cards count: {}, Card IDs: {}", 
                    refreshedUser.getId(), 
                    refreshedUser.getCards().size(),
                    refreshedUser.getCards().stream().map(CardInfo::getId).collect(Collectors.toList()));
        }
        
        return userMapper.toDto(refreshedUser);
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
