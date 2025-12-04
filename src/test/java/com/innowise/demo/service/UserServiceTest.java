package com.innowise.demo.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import com.innowise.demo.dto.PagedUserResponse;
import com.innowise.demo.dto.UpdateUserDto;
import com.innowise.demo.dto.UserDto;
import com.innowise.demo.exception.UserNotFoundException;
import com.innowise.demo.mapper.UserMapper;
import com.innowise.demo.model.User;
import com.innowise.demo.repository.CardInfoRepository;
import com.innowise.demo.repository.UserRepository;
import com.innowise.demo.client.AuthServiceClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {
    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private CardInfoRepository cardInfoRepository;

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
        user.setFirstName("Masha");
        user.setLastName("Raspberry");
        user.setEmail("masha@gmail.com");
        user.setBirthDate(LocalDate.of(1990, 1, 1));

        userDto = new UserDto();
        userDto.setId(1L);
        userDto.setFirstName("Masha");
        userDto.setLastName("Raspberry");
        userDto.setEmail("masha@gmail.com");
        userDto.setBirthDate(LocalDate.of(1990, 1, 1));
    }

    // ----------------- findByIdUser -----------------

    @DisplayName("findUserById_Positive")
    @Test
    void findUserById_UserExists_ReturnsDto() {
        // given
        // Когда кто-то вызовет userRepository.findById(1L), верни Optional с user
        // (это объект, созданный в setUp() с данными пользователя)
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        // Когда кто-то вызовет userMapper.toDto(user), верни userDto
        // (преобразование сущности в DTO для возврата клиенту)
        when(userMapper.toDto(user)).thenReturn(userDto);

        //when
        // Вызываем тестируемый метод получения пользователя по ID
        UserDto result = userService.findUserById(1L);

        //then
        assertNotNull(result); // Проверка: что результат не null
        assertEquals(1L, result.getId()); // Проверка: что ID пользователя совпадает
    }

    @DisplayName("findUserById_Negative")
    @Test
    void findUserById_UserNotFound_ThrowsException() {
        // given & when
        Long userId = 1L;
        // Когда кто-то вызовет userRepository.findById(1L), верни пустой Optional
        // Это имитирует ситуацию, когда пользователя с таким ID не существует в базе данных
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        // Вызываем тестируемый метод и ожидаем выброс исключения UserNotFoundException
        UserNotFoundException exception = assertThrows(UserNotFoundException.class, () -> userService.findUserById(userId));

        // then
        // Проверка: что текст сообщения исключения соответствует ожидаемому
        assertEquals("User with id " + userId + " not found!", exception.getMessage());
        // Проверка: что метод findById был вызван ровно 1 раз с нужным аргументом
        verify(userRepository, times(1)).findById(userId);
    }

    // ----------------- findByEmailUser -----------------

    @DisplayName("getUserByEmailNamed_Test_Positive")
    @Test
    void findUserByEmailNamed_UserExists_ReturnsDto() {
        // given
        // Когда кто-то вызовет userRepository.findByEmailNamed("masha@gmail.com"), верни Optional с user
        // (это объект, созданный в setUp() с данными пользователя)
        when(userRepository.findByEmailNamed("masha@gmail.com")).thenReturn(Optional.of(user));
        // Когда кто-то вызовет userMapper.toDto(user), верни userDto
        // (преобразование сущности в DTO для возврата клиенту)
        when(userMapper.toDto(user)).thenReturn(userDto);

        //when
        // Вызываем тестируемый метод получения пользователя по email
        UserDto result = userService.getUserByEmail("masha@gmail.com");

        //then
        assertNotNull(result); // Проверка: что результат не null
        assertEquals("masha@gmail.com", result.getEmail()); // Проверка: что email совпадает
        verify(userRepository, times(1)).findByEmailNamed("masha@gmail.com"); // Проверка: что метод был вызван ровно 1 раз
    }

    @DisplayName("getUserByEmailNamed_Test_Negative")
    @Test
    void findUserByEmail_UserNotFound_ThrowsException() {
        // given & when
        // Когда кто-то вызовет userRepository.findByEmailNamed("masha@gmail.com"), верни пустой Optional
        // Это имитирует ситуацию, когда пользователя с таким email не существует в базе данных
        when(userRepository.findByEmailNamed("masha@gmail.com")).thenReturn(Optional.empty());

        // then
        // Проверка: что метод выбросит исключение UserNotFoundException
        assertThrows(UserNotFoundException.class, () -> userService.getUserByEmail("masha@gmail.com"));
    }

    // ----------------- createUser -----------------

    @DisplayName("createUser_Positive")
    @Test
    void createUser_UserExists_ReturnsDto_WhenValid() {
        // given
        // Когда кто-то вызовет userRepository.findByEmailNamed(email), верни Optional с user
        // (это нужно для проверки существования пользователя перед созданием)
        when(userRepository.findByEmailNamed(userDto.getEmail()))
                .thenReturn(Optional.of(user));
        // Когда кто-то вызовет userMapper.toEntity(userDto), верни user
        // (преобразование DTO в сущность для сохранения в БД)
        when(userMapper.toEntity(userDto)).thenReturn(user);
        // Когда кто-то вызовет userMapper.updateCards(user, cards), верни пустой список
        // (обработка карт пользователя при создании)
        when(userMapper.updateCards(user, userDto.getCards()))
                .thenReturn(new ArrayList<>());
        // Когда кто-то вызовет userRepository.save(user), верни user
        // (имитация сохранения пользователя в БД)
        when(userRepository.save(user)).thenReturn(user);
        // Когда кто-то вызовет userMapper.toDto(user), верни userDto
        // (преобразование сущности обратно в DTO для возврата клиенту)
        when(userMapper.toDto(user)).thenReturn(userDto);

        //when
        // Вызываем тестируемый метод создания пользователя
        UserDto result = userService.createUser(userDto);

        //then
        assertNotNull(result); // Проверка: что результат не null
        assertEquals(userDto.getEmail(), result.getEmail()); // Проверка: что email совпадает
        verify(userRepository, times(1)).save(user); // Проверка: что метод save был вызван ровно 1 раз
    }

    @DisplayName("createUser_whenEmailExists_Negative")
    @Test
    void createUser_UserAlreadyExists_ThrowsException() {
        // given & when
        // Когда кто-то вызовет userRepository.findByEmailNativeQuery(email), верни Optional с user
        // Это имитирует ситуацию, когда пользователь с таким email уже существует в базе данных
        when(userRepository.findByEmailNativeQuery(userDto.getEmail()))
                .thenReturn(Optional.of(user));

        // Вызываем тестируемый метод и ожидаем выброс исключения RuntimeException
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> userService.createUser(userDto));

        // then
        // Проверка: что сообщение исключения содержит информацию о том, что пользователь уже существует
        assertTrue(exception.getMessage().contains("User with email " + userDto.getEmail() + " already exists"));
    }

    @DisplayName("createUser_whenEmailNotExists_Positive")
    @Test
    void createUser_UserNotExists_ShouldCreateUser() {
        // given
        // Когда кто-то вызовет userRepository.findByEmailNamed(email), верни пустой Optional
        // Это имитирует ситуацию, когда пользователя с таким email не существует в базе данных
        when(userRepository.findByEmailNamed(userDto.getEmail()))
                .thenReturn(Optional.empty());

        // Мок save через thenAnswer возвращает объект, который реально был передан
        // Это имитирует сохранение: метод save возвращает тот же объект, который был передан
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // Маппер toEntity формирует сущность User из DTO
        when(userMapper.toEntity(any(UserDto.class))).thenAnswer(invocation -> {
            UserDto dto = invocation.getArgument(0);
            User u = new User();
            u.setFirstName(dto.getFirstName());
            u.setLastName(dto.getLastName());
            u.setEmail(dto.getEmail());
            u.setBirthDate(dto.getBirthDate());
            return u;
        });
        // Маппер toDto формирует DTO из сущности User
        when(userMapper.toDto(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            UserDto dto = new UserDto();
            dto.setFirstName(u.getFirstName());
            dto.setLastName(u.getLastName());
            dto.setEmail(u.getEmail());
            dto.setBirthDate(u.getBirthDate());
            return dto;
        });

        //when
        // Вызываем тестируемый метод создания пользователя
        UserDto result = userService.createUser(userDto);

        // then
        assertNotNull(result); // Проверка: что результат не null
        assertEquals("Masha", result.getFirstName()); // Проверка: что имя совпадает
        assertEquals("Raspberry", result.getLastName()); // Проверка: что фамилия совпадает
        assertEquals("masha@gmail.com", result.getEmail()); // Проверка: что email совпадает
        assertEquals(LocalDate.of(1990,1,1), result.getBirthDate()); // Проверка: что дата рождения совпадает
        verify(userRepository, times(1)).save(any(User.class)); // Проверка: что метод save был вызван ровно 1 раз
    }

    // ----------------- findAllUser -----------------

    @Test
    @DisplayName("findAllUsers_Positive")
    void findAllUsers_ShouldReturnPagedResponse() {
        // given
        // Создаём список пользователей с одним пользователем внутри (user из setUp())
        List<User> users = List.of(user);
        // Создаём объект Page с одним пользователем (страница 0, размер страницы 5)
        // PageImpl — это реализация интерфейса Page от Spring Data
        Page<User> page = new PageImpl<>(users, PageRequest.of(0, 5), users.size());

        //when
        // Когда кто-то вызовет userRepository.findAll(PageRequest.of(0,5)), верни этот объект page
        when(userRepository.findAll(PageRequest.of(0,5))).thenReturn(page);
        // Когда кто-то вызовет userMapper.toDto(user), верни userDto
        when(userMapper.toDto(user)).thenReturn(userDto);

        // Вызываем тестируемый метод получения всех пользователей с пагинацией
        PagedUserResponse response = userService.findAllUsers(0,5);

        // then
        assertNotNull(response); // Проверка: что результат не null
        assertEquals(1, response.getContent().size()); // Проверка: что в результате 1 пользователь
        verify(userRepository, times(1)).findAll(PageRequest.of(0,5)); // Проверка: что метод был вызван ровно 1 раз
    }

    // ----------------- updateCurrentUser -----------------

    @DisplayName("updateCurrentUser_Positive")
    @Test
    void updateCurrentUser_ShouldReturnUpdatedDto_WhenUserExists() {
        // given
        when(userRepository.findByEmailNativeQuery("masha@gmail.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toDto(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            UserDto dto = new UserDto();
            dto.setId(u.getId());
            dto.setFirstName(u.getFirstName());
            dto.setLastName(u.getLastName());
            dto.setEmail(u.getEmail());
            dto.setBirthDate(u.getBirthDate());
            return dto;
        });

        UpdateUserDto updateDto = new UpdateUserDto();
        updateDto.setFirstName("Ivan");
        updateDto.setLastName("Vanusha");
        updateDto.setBirthDate(LocalDate.of(2000,1,1));

        //when
        UserDto result = userService.updateCurrentUser("masha@gmail.com", updateDto);

        // then
        assertNotNull(result);
        assertEquals("Ivan", result.getFirstName());
        assertEquals("Vanusha", result.getLastName());
        assertEquals("masha@gmail.com", result.getEmail());
        assertEquals(LocalDate.of(2000,1,1), result.getBirthDate());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @DisplayName("updateCurrentUser_Negative_UserNotFound")
    @Test
    void updateCurrentUser_ShouldThrowUserNotFound_WhenUserNotExists() {
        // given
        when(userRepository.findByEmailNativeQuery("notfound@gmail.com")).thenReturn(Optional.empty());

        UpdateUserDto updateDto = new UpdateUserDto();
        updateDto.setFirstName("Ivan");

        //when & then
        assertThrows(UserNotFoundException.class, 
                () -> userService.updateCurrentUser("notfound@gmail.com", updateDto));
    }

    // ----------------- updateUserByAdmin -----------------

    @DisplayName("updateUserByAdmin_Positive")
    @Test
    void updateUserByAdmin_ShouldReturnUpdatedDto_WhenUserExists() {
        // given
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toDto(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            UserDto dto = new UserDto();
            dto.setId(u.getId());
            dto.setFirstName(u.getFirstName());
            dto.setLastName(u.getLastName());
            dto.setEmail(u.getEmail());
            dto.setBirthDate(u.getBirthDate());
            return dto;
        });

        UpdateUserDto updateDto = new UpdateUserDto();
        updateDto.setFirstName("Ivan");
        updateDto.setLastName("Vanusha");
        updateDto.setBirthDate(LocalDate.of(2000,1,1));

        //when
        UserDto result = userService.updateUserByAdmin(1L, updateDto, "admin@example.com");

        // then
        assertNotNull(result);
        assertEquals("Ivan", result.getFirstName());
        assertEquals("Vanusha", result.getLastName());
        assertEquals("masha@gmail.com", result.getEmail());
        assertEquals(LocalDate.of(2000,1,1), result.getBirthDate());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @DisplayName("updateUserByAdmin_Negative_UserNotFound")
    @Test
    void updateUserByAdmin_ShouldThrowUserNotFound_WhenUserNotExists() {
        // given
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        UpdateUserDto updateDto = new UpdateUserDto();
        updateDto.setFirstName("Ivan");

        //when & then
        assertThrows(UserNotFoundException.class, 
                () -> userService.updateUserByAdmin(999L, updateDto, "admin@example.com"));
    }

    // ----------------- deleteUser -----------------

    @DisplayName("deleteUser_Positive")
    @Test
    void deleteUser_ShouldCallRepository_WhenUserExists() {
        // given
        // Когда кто-то вызовет userRepository.findById(1L), верни Optional с user
        // (это нужно для проверки существования пользователя перед удалением)
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        //when
        // Вызываем тестируемый метод удаления пользователя
        userService.deleteUser(1L);

        // then
        // Проверка: что метод deleteById был вызван ровно 1 раз с аргументом 1L
        verify(userRepository, times(1)).deleteById(1L);
        // Проверка: что метод deleteUser был вызван в authServiceClient для синхронизации удаления
        verify(authServiceClient, times(1)).deleteUser(user.getEmail());
    }

    @DisplayName("deleteUser_Negative")
    @Test
    void deleteUser_ShouldThrow_WhenUserNotFound() {
        // given & when
        // Когда кто-то вызовет userRepository.findById(1L), верни пустой Optional
        // Это имитирует ситуацию, когда пользователя с таким ID не существует в базе данных
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        // then
        // Проверка: что метод выбросит исключение UserNotFoundException
        // (нельзя удалить пользователя, которого не существует)
        assertThrows(UserNotFoundException.class, () -> userService.deleteUser(1L));
    }

    // ----------------- getUserByEmailJPQL -----------------

    @DisplayName("getUserByEmailJPQL_Positive")
    @Test
    void getUserByEmailJPQL_UserExists_ReturnsDto() {
        // given
        // Когда кто-то вызовет userRepository.findByEmailJPQL("masha@gmail.com"), верни Optional с user
        // (это объект, созданный в setUp() с данными пользователя)
        when(userRepository.findByEmailJPQL("masha@gmail.com")).thenReturn(Optional.of(user));
        // Когда кто-то вызовет userMapper.toDto(user), верни userDto
        // (преобразование сущности в DTO для возврата клиенту)
        when(userMapper.toDto(user)).thenReturn(userDto);

        //when
        // Вызываем тестируемый метод получения пользователя по email через JPQL запрос
        UserDto result = userService.getUserByEmailJPQl("masha@gmail.com");

        // then
        assertNotNull(result); // Проверка: что результат не null
        assertEquals("masha@gmail.com", result.getEmail()); // Проверка: что email совпадает
        verify(userRepository, times(1)).findByEmailJPQL("masha@gmail.com"); // Проверка: что метод был вызван ровно 1 раз
    }

    @DisplayName("getUserByEmailJPQL_Negative")
    @Test
    void getUserByEmailJPQL_UserNotFound_ThrowsException() {
        // given & when
        // Когда кто-то вызовет userRepository.findByEmailJPQL("notfound@gmail.com"), верни пустой Optional
        // Это имитирует ситуацию, когда пользователя с таким email не существует в базе данных
        when(userRepository.findByEmailJPQL("notfound@gmail.com")).thenReturn(Optional.empty());

        // then
        // Проверка: что метод выбросит исключение UserNotFoundException
        assertThrows(UserNotFoundException.class, () -> userService.getUserByEmailJPQl("notfound@gmail.com"));
    }

    // ----------------- getUserByEmailNative -----------------

    @DisplayName("getUserByEmailNative_Positive")
    @Test
    void getUserByEmailNative_UserExists_ReturnsDto() {
        // given
        // Когда кто-то вызовет userRepository.findByEmailNativeQuery("masha@gmail.com"), верни Optional с user
        // (это объект, созданный в setUp() с данными пользователя)
        when(userRepository.findByEmailNativeQuery("masha@gmail.com")).thenReturn(Optional.of(user));
        // Когда кто-то вызовет userMapper.toDto(user), верни userDto
        // (преобразование сущности в DTO для возврата клиенту)
        when(userMapper.toDto(user)).thenReturn(userDto);

        //when
        // Вызываем тестируемый метод получения пользователя по email через нативный SQL запрос
        UserDto result = userService.getUserByEmailNative("masha@gmail.com");

        // then
        assertNotNull(result); // Проверка: что результат не null
        assertEquals("masha@gmail.com", result.getEmail()); // Проверка: что email совпадает
        verify(userRepository, times(1)).findByEmailNativeQuery("masha@gmail.com"); // Проверка: что метод был вызван ровно 1 раз
    }

    @DisplayName("getUserByEmailNative_Negative")
    @Test
    void getUserByEmailNative_UserNotFound_ThrowsException() {
        // given & when
        // Когда кто-то вызовет userRepository.findByEmailNativeQuery("notfound@gmail.com"), верни пустой Optional
        // Это имитирует ситуацию, когда пользователя с таким email не существует в базе данных
        when(userRepository.findByEmailNativeQuery("notfound@gmail.com")).thenReturn(Optional.empty());

        // then
        // Проверка: что метод выбросит исключение UserNotFoundException
        assertThrows(UserNotFoundException.class, () -> userService.getUserByEmailNative("notfound@gmail.com"));
    }

    // ----------------- updateUser with cards -----------------

    @DisplayName("updateCurrentUser_WithCards_ShouldUpdateCards")
    @Test
    void updateCurrentUser_WithCards_ShouldUpdateCards() {
        // given
        // Когда кто-то вызовет userRepository.findByEmailNativeQuery("masha@gmail.com"), верни Optional с user
        when(userRepository.findByEmailNativeQuery("masha@gmail.com")).thenReturn(Optional.of(user));
        // Устанавливаем пустой список карт для пользователя
        user.setCards(new ArrayList<>()); // пустой список карт

        // Моки для проверки карт (если карты не переданы, репозиторий не вызывается)
        // Когда кто-то вызовет cardInfoRepository.findByNumberAndUserId(...), верни пустой Optional
        when(cardInfoRepository.findByNumberAndUserId(any(String.class), any(Long.class)))
                .thenReturn(Optional.empty());
        // Когда кто-то вызовет cardInfoRepository.findByNumber(...), верни пустой Optional
        when(cardInfoRepository.findByNumber(any(String.class)))
                .thenReturn(Optional.empty());

        // Мок save возвращает объект, который был передан
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // Маппер toDto формирует DTO из текущего состояния объекта User
        when(userMapper.toDto(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            UserDto dto = new UserDto();
            dto.setId(u.getId());
            dto.setFirstName(u.getFirstName());
            dto.setLastName(u.getLastName());
            dto.setEmail(u.getEmail());
            dto.setBirthDate(u.getBirthDate());
            return dto;
        });

        // Создаём DTO с данными для обновления пользователя
        UpdateUserDto updateDto = new UpdateUserDto();
        updateDto.setFirstName("Ivan");
        updateDto.setLastName("Vanusha");
        updateDto.setBirthDate(LocalDate.of(2000, 1, 1));
        updateDto.setCards(null); // null карты — карты не передаются для обновления

        //when
        // Вызываем тестируемый метод обновления пользователя через updateCurrentUser
        UserDto result = userService.updateCurrentUser("masha@gmail.com", updateDto);

        // then
        assertNotNull(result); // Проверка: что результат не null
        verify(userRepository, times(1)).save(any(User.class)); // Проверка: что метод save был вызван ровно 1 раз
    }

    // ----------------- findAllUsers edge cases -----------------

    @DisplayName("findAllUsers_EmptyPage_ShouldReturnEmpty")
    @Test
    void findAllUsers_EmptyPage_ShouldReturnEmpty() {
        // given
        // Создаём пустую страницу — это имитирует ситуацию, когда в базе данных нет пользователей
        Page<User> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 5), 0);

        //when
        // Когда кто-то вызовет userRepository.findAll(PageRequest.of(0, 5)), верни пустую страницу
        when(userRepository.findAll(PageRequest.of(0, 5))).thenReturn(emptyPage);

        // Вызываем тестируемый метод получения всех пользователей с пагинацией
        PagedUserResponse response = userService.findAllUsers(0, 5);

        // then
        assertNotNull(response); // Проверка: что результат не null
        assertEquals(0, response.getContent().size()); // Проверка: что в результате 0 пользователей
        assertEquals(0L, response.getTotalElements()); // Проверка: что всего элементов 0
        verify(userRepository, times(1)).findAll(PageRequest.of(0, 5)); // Проверка: что метод был вызван ровно 1 раз
    }
}