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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.innowise.demo.dto.CardInfoDto;
import com.innowise.demo.dto.CreateUserFromTokenRequest;
import com.innowise.demo.dto.PagedUserResponse;
import com.innowise.demo.dto.UserDto;
import com.innowise.demo.exception.UserAlreadyExistsException;
import com.innowise.demo.exception.UserNotFoundException;
import com.innowise.demo.mapper.UserMapper;
import com.innowise.demo.model.CardInfo;
import com.innowise.demo.model.User;
import com.innowise.demo.repository.CardInfoRepository;
import com.innowise.demo.repository.UserRepository;

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


    /**
     * Создает пользователя из JWT токена.
     * Email берется из токена (для проверки уникальности),
     * остальные данные из запроса.
     * 
     * @param email email пользователя, извлеченный из JWT токена
     * @param request данные пользователя (name, surname, birthDate)
     * @return созданный пользователь
     * @throws UserAlreadyExistsException если пользователь с таким email уже существует
     */
    @CachePut(key = "#result.id")
    @CacheEvict(value = "users_all", allEntries = true)
    public UserDto createUserFromToken(String email, CreateUserFromTokenRequest request) {
        // Проверка на уникальность email из токена
        if (userRepository.findByEmailNativeQuery(email).isPresent()) {
            throw new UserAlreadyExistsException(USER_WITH_EMAIL + email + " already exists");
        }

        // Создаем DTO с email из токена и данными из запроса
        UserDto userDto = new UserDto();
        userDto.setEmail(email);
        userDto.setName(request.getName());
        userDto.setSurname(request.getSurname());
        userDto.setBirthDate(request.getBirthDate());
        userDto.setCards(null); // Карты можно добавить позже через отдельный endpoint

        // Создаём сущность пользователя
        User entity = userMapper.toEntity(userDto);
        entity.setCards(new ArrayList<>()); // Пустой список карт

        // Hibernate сохранит пользователя
        User saved = userRepository.save(entity);

        return userMapper.toDto(saved);
    }

    @CachePut(key = "#result.id")
    @CacheEvict(value = "users_all", allEntries = true) // очищаем кэш списка
    public UserDto createUser(UserDto dto) {
        dto.setId(null);
        if (dto.getCards() != null) {
            dto.getCards().forEach(c -> c.setId(null));
        }

        // Проверка на уникальность email
        if (userRepository.findByEmailNativeQuery(dto.getEmail()).isPresent()) {
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

    // get by email
    @Cacheable(value = "users_by_email", key = "#email")
    @Transactional(readOnly = true)
    public UserDto getUserByEmail(String email) {
        User user = userRepository.findByEmailNamed(email)
                .orElseThrow(() -> new UserNotFoundException(USER_WITH_EMAIL + email + NOT_FOUND_SUFFIX));

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

    @CachePut(key = "#id")
    @CacheEvict(value = "users_all", allEntries = true)
    @Transactional
    public UserDto updateUser(Long id, UserDto dto) {
        User existUser = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(PREFIX_WITH_ID + id + NOT_FOUND_SUFFIX));

        existUser.setName(dto.getName());
        existUser.setSurname(dto.getSurname());
        existUser.setBirthDate(dto.getBirthDate());
        existUser.setEmail(dto.getEmail());

        if (dto.getCards() != null) {
            Map<Long, CardInfo> existingCardsMap = existUser.getCards().stream()
                    .filter(c -> c.getId() != null)
                    .collect(Collectors.toMap(CardInfo::getId, c -> c));

            List<CardInfo> updatedCards = new ArrayList<>();

            for (CardInfoDto cardDto : dto.getCards()) {
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

    @CacheEvict(value = "users_all", allEntries = true)
    @CachePut(key = "#id")
    @Transactional
    public void deleteUser(Long id) {
        userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(PREFIX_WITH_ID + id + NOT_FOUND_SUFFIX));

        userRepository.deleteById(id);
    }
}
