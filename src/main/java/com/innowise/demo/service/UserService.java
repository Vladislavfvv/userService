package com.innowise.demo.service;

import jakarta.persistence.EntityNotFoundException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import com.innowise.demo.dto.CardInfoDto;
import com.innowise.demo.dto.UserDto;
import com.innowise.demo.mapper.UserMapper;
import com.innowise.demo.model.CardInfo;
import com.innowise.demo.model.User;
import com.innowise.demo.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    //create
    public UserDto createUser(UserDto dto) {
//        if (user.getCards() != null) {
//            user.getCards().forEach(card -> card.setUser(user));
//        }
        User entity = userMapper.toEntity(dto);
        if (entity.getCards() != null) {
            entity.getCards().forEach(card -> card.setUser(entity));
        }
        User savedEntity = userRepository.save(entity);
        return userMapper.toDto(savedEntity);
    }

    //get by id
    public UserDto findUserById(Long id) {
        //return userRepository.findById(id).orElse(null);
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

    //update
    public UserDto updateUser(Long id, UserDto dto) {
        // Находим существующего пользователя
        User existUser = userRepository.findById(id)
                .orElseThrow(
                        () -> new EntityNotFoundException("User with id " + id + " not found!"));

        existUser.setName(dto.getName());
        existUser.setSurname(dto.getSurname());
        existUser.setBirthDate(dto.getBirthDate());
        existUser.setEmail(dto.getEmail());
        //existUser.setCards(dto.getCards());
        // Преобразуем DTO карты → сущности
//        if (dto.getCards() != null) {
//            existUser.setCards(
//                    dto.getCards().stream()
//                            .map(cardDto -> {
//                                var card = userMapper.getCardInfoMapper().toEntity(cardDto);
//                                card.setUser(existUser); // устанавливаем связь
//                                return card;
//                            })
//                            .toList()
//            );
//        }
        if (dto.getCards() != null) {
//            existUser.setCards(
//                    dto.getCards().stream()
//                            .map(cardDto -> {
//                                CardInfo card = userMapper.getCardInfoMapper().toEntity(cardDto);
//                                card.setUser(existUser); // устанавливаем связь
//                                return card;
//                            })
//                            .collect(Collectors.toCollection(ArrayList::new)) // <-- mutable list
//            );
//        } else {
//            existUser.setCards(new ArrayList<>()); // безопасно для Hibernate
//        }

            /// /////////////////////////////////
//            List<CardInfo> cards = dto.getCards().stream()
//                    .map(cardDto -> {
//                        CardInfo card = userMapper.getCardInfoMapper().toEntity(cardDto);
//                        card.setUser(existUser); // устанавливаем связь с пользователем
//                        return card;
//                    })
//                    .collect(Collectors.toCollection(ArrayList::new)); // mutable список
//
//            existUser.setCards(cards);
//        } else {
//            existUser.setCards(new ArrayList<>()); // безопасный пустой список
//        }

            // Удаляем старые карты
            existUser.getCards().clear();

            // Добавляем новые карты
            for (CardInfoDto cardDto : dto.getCards()) {
                CardInfo card = userMapper.getCardInfoMapper().toEntity(cardDto);
                card.setUser(existUser);
                existUser.getCards().add(card);
            }
        }
        // Сохраняем пользователя и возвращаем DTO
        return userMapper.toDto(userRepository.save(existUser));
    }

    //delete + каскадное удаление все CardInfo
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new EntityNotFoundException("User not found with id: " + id);
        }

        userRepository.deleteById(id);
    }
}
