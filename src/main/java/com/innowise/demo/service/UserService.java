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
import com.innowise.demo.dto.PagedUserResponse;
import com.innowise.demo.dto.UserDto;
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

    @CachePut(key = "#result.id")
    @CacheEvict(value = "users_all", allEntries = true) // очищаем кэш списка
    public UserDto createUser(UserDto dto) {
        dto.setId(null);
        if (dto.getCards() != null) {
            dto.getCards().forEach(c -> c.setId(null));
        }

        // Проверка на уникальность email
        if (userRepository.findByEmailNativeQuery(dto.getEmail()).isPresent()) {
            throw new RuntimeException("User with email " + dto.getEmail() + " already exists");
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
        System.out.println("Загружаем пользователя из БД, id=" + id);
        User user = userRepository.findById(id)
                .orElseThrow(
                        () -> new UserNotFoundException("User with id " + id + " not found!"));

        return userMapper.toDto(user);
    }

    @Cacheable(value = "users_all", key = "'page_' + #page + '_size_' + #size")
    @Transactional(readOnly = true)//длф решения проблемы ленивой инициализации
    public PagedUserResponse findAllUsers(int page, int size) {
        System.out.println("Получаем из БД (а не из кэша)");
        Page<User> users = userRepository.findAll(PageRequest.of(page, size));
        //Page<User> users = userRepository.findAllWithCards(PageRequest.of(page, size));

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
    public UserDto getUserByEmailNamed(String email) {
        long start = System.currentTimeMillis();
        System.out.println("Загружаем пользователя из БД, email=" + email);
        System.out.println("Время: " + (System.currentTimeMillis() - start) + " мс");
        User user = userRepository.findByEmailNamed(email)
                .orElseThrow(() -> new UserNotFoundException("User with email " + email + " not found!"));

        return userMapper.toDto(user);
    }

    // get by email JPQL
    @Cacheable(value = "users_by_email", key = "#email")
    @Transactional(readOnly = true)
    public UserDto getUserByEmailJPQl(String email) {
        long start = System.currentTimeMillis();
        System.out.println("Загружаем пользователя из БД, email=" + email);
        System.out.println("Время: " + (System.currentTimeMillis() - start) + " мс");
        User user = userRepository.findByEmailJPQL(email)
                .orElseThrow(() -> new UserNotFoundException("User with email " + email + " not found!"));

        return userMapper.toDto(user);
    }

    // get by email Native
    @Cacheable(value = "users_by_email", key = "#email")
    @Transactional(readOnly = true)
    public UserDto getUserByEmailNative(String email) {
        long start = System.currentTimeMillis();
        System.out.println("Загружаем пользователя из БД, email=" + email);
        System.out.println("Время: " + (System.currentTimeMillis() - start) + " мс");
        User user = userRepository.findByEmailNativeQuery(email)
                .orElseThrow(() -> new UserNotFoundException("User with email " + email + " not found!"));

        return userMapper.toDto(user);
    }

    @CachePut(key = "#id")
    @CacheEvict(value = "users_all", allEntries = true)
    @Transactional
    public UserDto updateUser(Long id, UserDto dto) {
        User existUser = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User with id " + id + " not found!"));

        // Обновляем простые поля
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
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));

        userRepository.deleteById(id);
    }
}
