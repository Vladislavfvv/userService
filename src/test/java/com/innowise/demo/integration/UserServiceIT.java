package com.innowise.demo.integration;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import com.innowise.demo.dto.UpdateUserDto;
import com.innowise.demo.dto.UserDto;
import com.innowise.demo.exception.UserNotFoundException;
import com.innowise.demo.mapper.UserMapper;
import com.innowise.demo.model.User;
import com.innowise.demo.repository.UserRepository;
import com.innowise.demo.service.UserService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserServiceIT extends BaseIntegrationTest{



    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private UserDto userDto;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        userDto = new UserDto();
        userDto.setName("Integration");
        userDto.setSurname("Test");
        userDto.setEmail("integration@example.com");
        userDto.setBirthDate(LocalDate.of(1995, 5, 5));
    }

    @Test
    @Order(1)
    void createUser_ShouldSaveUser() {
        UserDto saved = userService.createUser(userDto);

        assertNotNull(saved.getId());
        assertEquals("integration@example.com", saved.getEmail());
        assertEquals(1, userRepository.count());
    }

    @Test
    @Order(2)
    void findUserById_ShouldReturnUser() {
        User saved = userRepository.save(userMapper.toEntity(userDto));

        UserDto result = userService.findUserById(saved.getId());

        assertNotNull(result);
        assertEquals(saved.getEmail(), result.getEmail());
    }

    @Test
    @Order(3)
    void findUserById_NotFound_ShouldThrow() {
        assertThrows(UserNotFoundException.class, () -> userService.findUserById(999L));
    }

    @Test
    @Order(4)
    void getUserByEmailNative_ShouldReturnUser() {
        User saved = userRepository.save(userMapper.toEntity(userDto));

        UserDto result = userService.getUserByEmailNative(saved.getEmail());

        assertNotNull(result);
        assertEquals(saved.getEmail(), result.getEmail());
    }

    @Test
    @Order(5)
    void updateUser_ShouldModifyData() {
        User saved = userRepository.save(userMapper.toEntity(userDto));

        UpdateUserDto updateDto = new UpdateUserDto();
        updateDto.setName("Updated");
        updateDto.setSurname("User");
        updateDto.setBirthDate(LocalDate.of(1990, 1, 1));

        UserDto updated = userService.updateUser(saved.getId(), updateDto, saved.getEmail());

        assertEquals("Updated", updated.getName());
        assertEquals(saved.getEmail(), updated.getEmail()); // email не меняется, берется из токена
    }

    @Test
    @Order(6)
    void deleteUser_ShouldRemoveFromDatabase() {
        User saved = userRepository.save(userMapper.toEntity(userDto));

        userService.deleteUser(saved.getId());

        assertEquals(0, userRepository.count());
    }
}

