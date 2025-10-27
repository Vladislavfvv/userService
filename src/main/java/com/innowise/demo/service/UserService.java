package com.innowise.demo.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import jakarta.persistence.EntityNotFoundException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.innowise.demo.dto.CardInfoDto;
import com.innowise.demo.dto.UserDto;
import com.innowise.demo.mapper.UserMapper;
import com.innowise.demo.model.CardInfo;
import com.innowise.demo.model.User;
import com.innowise.demo.repository.CardInfoRepository;
import com.innowise.demo.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final CardInfoRepository cardInfoRepository;

    public UserDto createUser(UserDto dto) {
        // Маппим пользователя без карт
        User entity = userMapper.toEntity(dto);

        // Создаём список карт вручную
        List<CardInfo> cards = userMapper.updateCards(entity, dto.getCards());
        entity.setCards(cards);

        // Устанавливаем связь user -> card
        cards.forEach(c -> c.setUser(entity));

        // Сохраняем каскадно (CascadeType.ALL)
        User saved = userRepository.save(entity);

        return userMapper.toDto(saved);
    }

    //get by id
    public UserDto findUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(
                        () -> new EntityNotFoundException("User with id " + id + " not found!"));

        return userMapper.toDto(user);
    }

    //get all with pagination
    public Page<UserDto> findAllUsers(int page, int size) {
        return userRepository.findAll(PageRequest.of(page, size))
                .map(userMapper::toDto);
    }

    // get by email
    public UserDto getUserByEmailNamed(String email) {
        User user = userRepository.findByEmailNamed(email)
                .orElseThrow(() -> new EntityNotFoundException("User with email " + email + " not found!"));

        return userMapper.toDto(user);
    }

    // get by email JPQL
    public UserDto getUserByEmailJPQl(String email) {
        User user = userRepository.findByEmailJPQL(email)
                .orElseThrow(() -> new EntityNotFoundException("User with email " + email + " not found!"));

        return userMapper.toDto(user);
    }

    // get by email Native
    public UserDto getUserByEmailNative(String email) {
        User user = userRepository.findByEmailNativeQuery(email)
                .orElseThrow(() -> new EntityNotFoundException("User with email " + email + " not found!"));

        return userMapper.toDto(user);
    }

    @Transactional
    public UserDto updateUser(Long id, UserDto dto) {
        User existUser = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User with id " + id + " not found!"));

        // Обновляем простые поля
        existUser.setName(dto.getName());
        existUser.setSurname(dto.getSurname());
        existUser.setBirthDate(dto.getBirthDate());
        existUser.setEmail(dto.getEmail());

        if (dto.getCards() != null) {
            List<CardInfo> updatedCards = new ArrayList<>();
            Map<Long, CardInfo> existingCardsMap = existUser.getCards().stream()
                    .collect(Collectors.toMap(CardInfo::getId, c -> c));

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

            // Удаляем старые карты, которых нет в DTO
            List<CardInfo> toDelete = existUser.getCards().stream()
                    .filter(c -> updatedCards.stream().noneMatch(u -> u.getId() != null && u.getId().equals(c.getId())))
                    .collect(Collectors.toList());

            toDelete.forEach(c -> c.setUser(null));
            cardInfoRepository.deleteAll(toDelete);

            // Назначаем обновлённый список карт пользователю
            existUser.setCards(updatedCards);
        }

        return userMapper.toDto(userRepository.save(existUser));
    }

    //delete + каскадное удаление все CardInfo
    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + id));

        // Удаляем все карты пользователя вручную
        List<CardInfo> cards = user.getCards();
        if (cards != null && !cards.isEmpty()) {
            cardInfoRepository.deleteAll(cards);
        }

        userRepository.deleteById(id);
    }
}
