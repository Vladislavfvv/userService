package com.innowise.demo.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import com.innowise.demo.client.AuthServiceClient;
import com.innowise.demo.dto.PagedUserResponse;
import com.innowise.demo.dto.UserDto;
import com.innowise.demo.dto.UserUpdateRequest;
import com.innowise.demo.exception.UserNotFoundException;
import com.innowise.demo.mapper.UserMapper;
import com.innowise.demo.model.User;
import com.innowise.demo.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("null")
class UserServiceTest {
    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private AuthServiceClient authServiceClient;

    private User user;
    private UserDto userDto;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        user = new User();
        user.setId(1L);
        user.setName("Masha");
        user.setSurname("Raspberry");
        user.setEmail("masha@gmail.com");
        user.setBirthDate(LocalDate.of(1990, 1, 1));

        userDto = new UserDto();
        userDto.setId(1L);
        userDto.setName("Masha");
        userDto.setSurname("Raspberry");
        userDto.setEmail("masha@gmail.com");
        userDto.setBirthDate(LocalDate.of(1990, 1, 1));
        doNothing().when(authServiceClient).updateUserProfile(any());
        authenticateAsAdmin();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAsUser(String email) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        email,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void authenticateAsAdmin() {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        "admin@example.com",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    // ----------------- findByIdUser -----------------

    @DisplayName("findUserById_Positive")
    @Test
    void findUserById_UserExists_ReturnsDto() {
        // given
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userMapper.toDto(user)).thenReturn(userDto);

        //when вызов метода
        UserDto result = userService.findUserById(1L);

        //then сравнение
        assertNotNull(result);
        assertEquals(1L, result.getId());
    }

    @DisplayName("findUserById_Negative")
    @Test
    void findUserById_UserNotFound_ThrowsException() {
        //given имитация ситуации, что в базе нет пользователя с ID = 1
        Long userId = 1L;
        when(userRepository.findById(1L)).thenReturn(Optional.empty());
        // when & then // сам вызов метода, который мы тестируем, ожидаем выброс исключения UserNotFoundException
        UserNotFoundException exception = assertThrows(UserNotFoundException.class, () -> userService.findUserById(userId));

        //проверить текст сообщения исключения
        assertEquals("User with id " + userId + " not found!", exception.getMessage());
        // verify: проверяем, что мок был вызван ровно один раз с нужным аргументом
        verify(userRepository, times(1)).findById(userId);
    }

    // ----------------- findByEmailUser -----------------

    @DisplayName("getUserByEmailNamed_Test_Positive")
    @Test
    void findUserByEmailNamed_UserExists_ReturnsDto() {
        // given
        when(userRepository.findByEmailNamed("masha@gmail.com")).thenReturn(Optional.of(user));
        when(userMapper.toDto(user)).thenReturn(userDto);

        //when
        UserDto result = userService.getUserByEmail("masha@gmail.com");

        //then
        assertNotNull(result);
        assertEquals("masha@gmail.com", result.getEmail());
        verify(userRepository, times(1)).findByEmailNamed("masha@gmail.com");
    }

    @DisplayName("getUserByEmailNamed_Test_Negative")
    @Test
    void findUserByEmail_UserNotFound_ThrowsException() {
        // given
        when(userRepository.findByEmailNamed("masha@gmail.com")).thenReturn(Optional.empty());

        // when & then
        assertThrows(UserNotFoundException.class, () -> userService.getUserByEmail("masha@gmail.com"));
    }

    // ----------------- createUser -----------------

    @DisplayName("createUser_Positive")
    @Test
    void createUser_UserExists_ReturnsDto_WhenValid() {
        //given
        when(userRepository.findByEmailNamed(userDto.getEmail()))
                .thenReturn(Optional.of(user));
        when(userMapper.toEntity(userDto)).thenReturn(user);
        when(userMapper.updateCards(user, userDto.getCards()))
                .thenReturn(new ArrayList<>());
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toDto(user)).thenReturn(userDto);

        //when
        UserDto result = userService.createUser(userDto);

        //then
        assertNotNull(result);
        assertEquals(userDto.getEmail(), result.getEmail());
        verify(userRepository, times(1)).save(user);
    }

    @DisplayName("createUser_whenEmailExists_Negative")
    @Test
    void createUser_UserAlreadyExists_ThrowsException() {
        // given: email уже существует
        when(userRepository.findByEmailNativeQuery(userDto.getEmail()))
                .thenReturn(Optional.of(user));

        // when & then
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> userService.createUser(userDto));

        assertTrue(exception.getMessage().contains("User with email " + userDto.getEmail() + " already exists"));
    }

    @DisplayName("createUser_whenEmailNotExists_Positive")
    @Test
    void createUser_UserNotExists_ShouldCreateUser() {
        // given: email не существует
        when(userRepository.findByEmailNamed(userDto.getEmail()))
                .thenReturn(Optional.empty());

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toEntity(any(UserDto.class))).thenAnswer(invocation -> {
            UserDto dto = invocation.getArgument(0);
            User u = new User();
            u.setName(dto.getName());
            u.setSurname(dto.getSurname());
            u.setEmail(dto.getEmail());
            u.setBirthDate(dto.getBirthDate());
            return u;
        });
        when(userMapper.toDto(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            UserDto dto = new UserDto();
            dto.setName(u.getName());
            dto.setSurname(u.getSurname());
            dto.setEmail(u.getEmail());
            dto.setBirthDate(u.getBirthDate());
            return dto;
        });

        // when
        UserDto result = userService.createUser(userDto);

        // then
        assertNotNull(result);
        assertEquals("Masha", result.getName());
        assertEquals("Raspberry", result.getSurname());
        assertEquals("masha@gmail.com", result.getEmail());
        assertEquals(LocalDate.of(1990,1,1), result.getBirthDate());
        verify(userRepository, times(1)).save(any(User.class));
    }

    // ----------------- findAllUser -----------------

    @Test
    @DisplayName("findAllUsers_Positive")
    void findAllUsers_ShouldReturnPagedResponse() {
        //given
        List<User> users = List.of(user);
        Page<User> page = new PageImpl<>(users, PageRequest.of(0, 5), users.size());
        when(userRepository.findAll(PageRequest.of(0,5))).thenReturn(page);
        when(userMapper.toDto(user)).thenReturn(userDto);

        // when
        PagedUserResponse response = userService.findAllUsers(0,5);

        // then
        assertNotNull(response);
        assertEquals(1, response.getContent().size());//т.к. страницы начинаются с нуля
        verify(userRepository, times(1)).findAll(PageRequest.of(0,5));
    }

    // ----------------- updateUser -----------------

    @DisplayName("updateUser_Positive")
    @Test
    void updateUser_ShouldReturnUpdatedDto_WhenUserExists() {
        // given
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        // save возвращает тот же объект, который передали
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // toDto возвращает DTO с актуальными полями пользователя
        when(userMapper.toDto(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            UserDto dto = new UserDto();
            dto.setId(u.getId());
            dto.setName(u.getName());
            dto.setSurname(u.getSurname());
            dto.setEmail(u.getEmail());
            dto.setBirthDate(u.getBirthDate());
            return dto;
        });

        UserUpdateRequest updateDto = new UserUpdateRequest();
        updateDto.setUserId(1L);
        updateDto.setName("Ivan");
        updateDto.setSurname("Vanusha");
        updateDto.setEmail("Vanusha@example.com");
        updateDto.setBirthDate(LocalDate.of(2000,1,1));

        when(userRepository.findByEmailNativeQuery("Vanusha@example.com")).thenReturn(Optional.empty());

        authenticateAsUser("masha@gmail.com");

        // when
        UserDto result = userService.updateUser(1L, updateDto);

        // then
        assertNotNull(result);
        assertEquals("Ivan", result.getName());
        assertEquals("Vanusha", result.getSurname());
        assertEquals("vanusha@example.com", result.getEmail());
        assertEquals(LocalDate.of(2000,1,1), result.getBirthDate());

        verify(userRepository, times(1)).save(any(User.class));
    }

    @DisplayName("getCurrentUser_ShouldProvisionUser_WhenNotExists")
    @Test
    void getCurrentUser_ShouldProvisionUser_WhenNotExists() {
        String email = "newuser@example.com";
        authenticateAsUser(email);
        when(userRepository.findByEmailNativeQuery(email)).thenReturn(Optional.empty());
        when(userRepository.findByEmailNamed(email)).thenReturn(Optional.empty());

        when(userRepository.findByEmailNamed(email)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });
        when(userMapper.toDto(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            UserDto dto = new UserDto();
            dto.setId(saved.getId());
            dto.setEmail(saved.getEmail());
            dto.setName(saved.getName());
            dto.setSurname(saved.getSurname());
            return dto;
        });

        UserDto current = userService.getCurrentUser(SecurityContextHolder.getContext().getAuthentication());

        assertNotNull(current);
        assertEquals(email, current.getEmail());
        verify(userRepository, times(1)).save(any(User.class));
    }

    // ----------------- deleteUser -----------------

    @DisplayName("deleteUser_Positive")
    @Test
    void deleteUser_ShouldCallRepository_WhenUserExists() {
        // given
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        // when
        userService.deleteUser(1L);

        // then
        verify(userRepository, times(1)).deleteById(1L);
    }

    @DisplayName("deleteUser_Negative")
    @Test
    void deleteUser_ShouldThrow_WhenUserNotFound() {
        // given
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        // when & then
        assertThrows(UserNotFoundException.class, () -> userService.deleteUser(1L));
    }

    // ----------------- getUserByEmailJPQL -----------------

    @DisplayName("getUserByEmailJPQL_Positive")
    @Test
    void getUserByEmailJPQL_UserExists_ReturnsDto() {
        // given
        when(userRepository.findByEmailJPQL("masha@gmail.com")).thenReturn(Optional.of(user));
        when(userMapper.toDto(user)).thenReturn(userDto);

        // when
        UserDto result = userService.getUserByEmailJPQl("masha@gmail.com");

        // then
        assertNotNull(result);
        assertEquals("masha@gmail.com", result.getEmail());
        verify(userRepository, times(1)).findByEmailJPQL("masha@gmail.com");
    }

    @DisplayName("getUserByEmailJPQL_Negative")
    @Test
    void getUserByEmailJPQL_UserNotFound_ThrowsException() {
        // given
        when(userRepository.findByEmailJPQL("notfound@gmail.com")).thenReturn(Optional.empty());

        // when & then
        assertThrows(UserNotFoundException.class, () -> userService.getUserByEmailJPQl("notfound@gmail.com"));
    }

    // ----------------- getUserByEmailNative -----------------

    @DisplayName("getUserByEmailNative_Positive")
    @Test
    void getUserByEmailNative_UserExists_ReturnsDto() {
        // given
        when(userRepository.findByEmailNativeQuery("masha@gmail.com")).thenReturn(Optional.of(user));
        when(userMapper.toDto(user)).thenReturn(userDto);

        // when
        UserDto result = userService.getUserByEmailNative("masha@gmail.com");

        // then
        assertNotNull(result);
        assertEquals("masha@gmail.com", result.getEmail());
        verify(userRepository, times(1)).findByEmailNativeQuery("masha@gmail.com");
    }

    @DisplayName("getUserByEmailNative_Negative")
    @Test
    void getUserByEmailNative_UserNotFound_ThrowsException() {
        // given
        when(userRepository.findByEmailNativeQuery("notfound@gmail.com")).thenReturn(Optional.empty());

        // when & then
        assertThrows(UserNotFoundException.class, () -> userService.getUserByEmailNative("notfound@gmail.com"));
    }

    // ----------------- updateUser with cards -----------------

    @DisplayName("updateUser_WithCards_ShouldUpdateCards")
    @Test
    void updateUser_WithCards_ShouldUpdateCards() {
        // given
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        user.setCards(new ArrayList<>()); // пустой список карт

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toDto(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            UserDto dto = new UserDto();
            dto.setId(u.getId());
            dto.setName(u.getName());
            dto.setSurname(u.getSurname());
            dto.setEmail(u.getEmail());
            dto.setBirthDate(u.getBirthDate());
            return dto;
        });

        UserUpdateRequest updateDto = new UserUpdateRequest();
        updateDto.setUserId(1L);
        updateDto.setName("Ivan");
        updateDto.setSurname("Vanusha");
        updateDto.setEmail("Vanusha@example.com");
        updateDto.setBirthDate(LocalDate.of(2000, 1, 1));
        updateDto.setCards(Collections.emptyList());

        when(userRepository.findByEmailNativeQuery("Vanusha@example.com")).thenReturn(Optional.empty());

        authenticateAsUser("masha@gmail.com");

        // when
        UserDto result = userService.updateUser(1L, updateDto);

        // then
        assertNotNull(result);
        verify(userRepository, times(1)).save(any(User.class));
    }

    // ----------------- findAllUsers edge cases -----------------

    @DisplayName("findAllUsers_EmptyPage_ShouldReturnEmpty")
    @Test
    void findAllUsers_EmptyPage_ShouldReturnEmpty() {
        // given
        Page<User> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 5), 0);
        when(userRepository.findAll(PageRequest.of(0, 5))).thenReturn(emptyPage);

        // when
        PagedUserResponse response = userService.findAllUsers(0, 5);

        // then
        assertNotNull(response);
        assertEquals(0, response.getContent().size());
        assertEquals(0L, response.getTotalElements());
        verify(userRepository, times(1)).findAll(PageRequest.of(0, 5));
    }
}