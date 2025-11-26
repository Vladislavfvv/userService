package com.innowise.demo.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import com.innowise.demo.dto.CardInfoDto;
import com.innowise.demo.exception.CardInfoNotFoundException;
import com.innowise.demo.exception.UserNotFoundException;
import com.innowise.demo.mapper.CardInfoMapper;
import com.innowise.demo.model.CardInfo;
import com.innowise.demo.model.User;
import com.innowise.demo.repository.CardInfoRepository;
import com.innowise.demo.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CardInfoServiceTest {

    @InjectMocks
    private CardInfoService cardInfoService;

    @Mock
    private CardInfoRepository cardInfoRepository;

    @Mock
    private CardInfoMapper cardInfoMapper;

    private CardInfo card;
    private CardInfoDto cardDto;
    private User user;

    @Mock
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");

        card = new CardInfo();
        card.setId(1L);
        card.setNumber("1234 5678 9012 3456");
        card.setHolder("Anna Tolstova");
        card.setExpirationDate(LocalDate.of(2030, 1, 1));
        card.setUser(user);

        cardDto = new CardInfoDto();
        cardDto.setId(1L);
        cardDto.setNumber("1234 5678 9012 3456");
        cardDto.setHolder("Anna Tolstova");
        cardDto.setExpirationDate(LocalDate.of(2030, 1, 1));
        cardDto.setUserId(user.getId());

    }

    private JwtAuthenticationToken createMockAuthentication(String email, String role) {
        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "HS256")
                .claim("sub", email)
                .claim("email", email)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        return new JwtAuthenticationToken(
                jwt,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role))
        );
    }

    private void mockSecurityContext(Authentication authentication) {
        SecurityContext securityContext = org.mockito.Mockito.mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    void getCardInfoById_Exists_ReturnsDto() {
        // given
        // Создаём тестовые объекты для проверки получения карты по ID
        CardInfo cardInfo = new CardInfo();
        cardInfo.setId(1L);
        cardInfo.setUser(user);
        CardInfoDto dto = new CardInfoDto();
        dto.setId(1L);

        // Мокируем аутентификацию
        JwtAuthenticationToken authentication = createMockAuthentication("test@example.com", "USER");
        mockSecurityContext(authentication);

        //when
        // Когда кто-то вызовет cardInfoRepository.findById(1L), верни Optional с cardInfo
        when(cardInfoRepository.findById(1L)).thenReturn(Optional.of(cardInfo));
        // Когда кто-то вызовет cardInfoMapper.toDto(cardInfo), верни dto
        when(cardInfoMapper.toDto(cardInfo)).thenReturn(dto);

        // Вызываем тестируемый метод
        CardInfoDto result = cardInfoService.getCardInfoById(1L);

        // then
        assertNotNull(result); // Проверка: что результат не null
        assertEquals(1L, result.getId()); // Проверка: что ID карты совпадает
    }

    @Test
    void getCardInfoById_NotFound_ThrowsException() {
        // given & when
        // Когда кто-то вызовет cardInfoRepository.findById(1L), верни пустой Optional
        // Это имитирует ситуацию, когда карты с таким ID не существует в базе данных
        when(cardInfoRepository.findById(1L)).thenReturn(Optional.empty());

        // then
        // Проверка: что метод выбросит исключение CardInfoNotFoundException
        assertThrows(CardInfoNotFoundException.class, () -> cardInfoService.getCardInfoById(1L));
    }

    // ----------------- save CardInfo -----------------
    @DisplayName("saveCardInfo_Positive")
    @Test
    void save_ShouldReturnCardDto_WhenUserExists() {
        // given
        // Мокируем аутентификацию
        JwtAuthenticationToken authentication = createMockAuthentication("test@example.com", "USER");
        mockSecurityContext(authentication);

        // Когда кто-то вызовет userRepository.findById(1L), верни Optional с user
        // (это нужно для проверки существования пользователя перед сохранением карты)
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        // Когда кто-то вызовет cardInfoMapper.toEntity(cardDto), верни card
        // (преобразование DTO в сущность для сохранения в БД)
        when(cardInfoMapper.toEntity(cardDto)).thenReturn(card);
        // Когда кто-то вызовет cardInfoRepository.save(card), верни card
        // (имитация сохранения карты в БД)
        when(cardInfoRepository.save(card)).thenReturn(card);
        // Когда кто-то вызовет cardInfoMapper.toDto(card), верни cardDto
        // (преобразование сущности обратно в DTO для возврата клиенту)
        when(cardInfoMapper.toDto(card)).thenReturn(cardDto);

        //when
        // Вызываем тестируемый метод сохранения карты
        CardInfoDto result = cardInfoService.save(cardDto);

        // then
        assertNotNull(result); // Проверка: что результат не null
        assertEquals(cardDto.getNumber(), result.getNumber()); // Проверка: что номер карты совпадает
        verify(cardInfoRepository, times(1)).save(card); // Проверка: что метод save был вызван ровно 1 раз
    }

    @DisplayName("saveCardInfo_Negative")
    @Test
    void save_ShouldThrow_WhenUserNotFound() {
        // given & when
        // Когда кто-то вызовет userRepository.findById(1L), верни пустой Optional
        // Это имитирует ситуацию, когда пользователя с таким ID не существует в базе данных
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        // then
        // Проверка: что метод выбросит исключение UserNotFoundException
        // (нельзя сохранить карту для несуществующего пользователя)
        assertThrows(UserNotFoundException.class, () -> cardInfoService.save(cardDto));
    }

    // ----------------- getCardInfoById -----------------
    @DisplayName("getCardInfo_Positive")
    @Test
    void getCardInfoById_ShouldReturnCardDto_WhenExists() {
        // given
        // Мокируем аутентификацию
        JwtAuthenticationToken authentication = createMockAuthentication("test@example.com", "USER");
        mockSecurityContext(authentication);

        // Когда кто-то вызовет cardInfoRepository.findById(1L), верни Optional с card
        // (это объект, созданный в setUp() с данными карты)
        when(cardInfoRepository.findById(1L)).thenReturn(Optional.of(card));
        // Когда кто-то вызовет cardInfoMapper.toDto(card), верни cardDto
        // (преобразование сущности в DTO для возврата клиенту)
        when(cardInfoMapper.toDto(card)).thenReturn(cardDto);

        //when
        // Вызываем тестируемый метод получения карты по ID
        CardInfoDto result = cardInfoService.getCardInfoById(1L);

        // then
        assertNotNull(result); // Проверка: что результат не null
        assertEquals(card.getNumber(), result.getNumber()); // Проверка: что номер карты совпадает
    }

    // ----------------- getAllCardInfos -----------------
    @DisplayName("getALLCardInfo_Positive")
    @Test
    void getAllCardInfos_ShouldReturnPage() {
        // given
        // Мокируем аутентификацию для ADMIN (чтобы использовался findAll)
        JwtAuthenticationToken authentication = createMockAuthentication("admin@example.com", "ADMIN");
        mockSecurityContext(authentication);

        // Создаём список карт с одной картой внутри (card из setUp())
        List<CardInfo> cards = List.of(card);
        // Создаём объект Page с одной картой (страница 0, размер страницы 5)
        // PageImpl — это реализация интерфейса Page от Spring Data
        Page<CardInfo> page = new PageImpl<>(cards, PageRequest.of(0, 5), cards.size());

        //when
        // Когда кто-то вызовет cardInfoRepository.findAll(PageRequest.of(0, 5)), верни этот объект page
        when(cardInfoRepository.findAll(PageRequest.of(0, 5))).thenReturn(page);
        // Когда кто-то вызовет cardInfoMapper.toDto(card), верни cardDto
        when(cardInfoMapper.toDto(card)).thenReturn(cardDto);

        // Вызываем тестируемый метод получения всех карт с пагинацией
        Page<CardInfoDto> result = cardInfoService.getAllCardInfos(0, 5);

        // then
        assertNotNull(result); // Проверка: что результат не null
        assertEquals(1, result.getContent().size()); // Проверка: что в результате 1 карта
    }

    @DisplayName("getALLCardInfo_Negative")
    @Test
    void getAllCardInfos_ShouldThrow_WhenEmpty() {
        // given
        // Мокируем аутентификацию для ADMIN (чтобы использовался findAll)
        JwtAuthenticationToken authentication = createMockAuthentication("admin@example.com", "ADMIN");
        mockSecurityContext(authentication);

        // Создаём пустую страницу — это имитирует ситуацию, когда в базе данных нет карт
        Page<CardInfo> emptyPage = Page.empty();
        // Когда кто-то вызовет cardInfoRepository.findAll(PageRequest.of(0, 10)), верни пустую страницу
        when(cardInfoRepository.findAll(PageRequest.of(0, 10))).thenReturn(emptyPage);

        //when & then
        // Проверка: что метод выбросит исключение CardInfoNotFoundException
        // (нельзя получить список карт, если их нет в базе данных)
        assertThrows(CardInfoNotFoundException.class, () -> cardInfoService.getAllCardInfos(0, 10));
    }

    @DisplayName("updateCardInfo_Positive")
    @Test
    void updateCardInfo_ShouldReturnUpdatedDto() {
        // given
        // Мокируем аутентификацию
        JwtAuthenticationToken authentication = createMockAuthentication("test@example.com", "USER");
        mockSecurityContext(authentication);

        // Когда кто-то вызовет cardInfoRepository.findById(1L), верни Optional с card
        // (это нужно для получения существующей карты перед обновлением)
        when(cardInfoRepository.findById(1L)).thenReturn(Optional.of(card));
        // Когда кто-то вызовет userRepository.findById(1L), верни Optional с user
        // (это нужно для проверки существования пользователя)
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        // Мок save через thenAnswer возвращает объект, который реально был передан
        // Это имитирует сохранение: метод save возвращает тот же объект, который был передан
        when(cardInfoRepository.save(any(CardInfo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Маппер формирует DTO из текущего состояния объекта CardInfo
        // Это позволяет проверить, что данные действительно обновились
        when(cardInfoMapper.toDto(any(CardInfo.class))).thenAnswer(invocation -> {
            CardInfo c = invocation.getArgument(0);
            CardInfoDto dto = new CardInfoDto();
            dto.setId(c.getId());
            dto.setNumber(c.getNumber());
            dto.setHolder(c.getHolder());
            dto.setExpirationDate(c.getExpirationDate());
            dto.setUserId(c.getUser().getId());
            return dto;
        });

        // Создаём DTO с данными для обновления карты
        CardInfoDto updateDto = new CardInfoDto();
        updateDto.setNumber("9999 8888 7777 6666"); // новый номер карты
        updateDto.setHolder("Updated Holder"); // новое имя держателя
        updateDto.setExpirationDate(LocalDate.of(2035, 1, 1)); // новая дата истечения
        updateDto.setUserId(user.getId()); // ID пользователя остаётся прежним

        //when
        // Вызываем тестируемый метод обновления карты
        CardInfoDto result = cardInfoService.updateCardInfo(1L, updateDto);

        // then
        assertNotNull(result); // Проверка: что результат не null
        assertEquals("9999 8888 7777 6666", result.getNumber()); // Проверка: что номер обновился
        assertEquals("Updated Holder", result.getHolder()); // Проверка: что имя держателя обновилось
        assertEquals(LocalDate.of(2035, 1, 1), result.getExpirationDate()); // Проверка: что дата обновилась
        verify(cardInfoRepository, times(1)).save(any(CardInfo.class)); // Проверка: что метод save был вызван ровно 1 раз
    }

    // ----------------- deleteCardInfo -----------------
    @DisplayName("deleteCardInfo_WhenExists")
    @Test
    void deleteCardInfo_ShouldCallDelete_WhenExists() {
        // given
        // Мокируем аутентификацию
        JwtAuthenticationToken authentication = createMockAuthentication("test@example.com", "USER");
        mockSecurityContext(authentication);

        // Когда кто-то вызовет cardInfoRepository.findById(1L), верни Optional с card
        // (это нужно для проверки существования карты перед удалением)
        when(cardInfoRepository.findById(1L)).thenReturn(Optional.of(card));

        //when
        // Вызываем тестируемый метод удаления карты
        cardInfoService.deleteCardInfo(1L);

        // then
        // Проверка: что метод findById был вызван для получения карты
        verify(cardInfoRepository, times(1)).findById(1L);
        // Проверка: что метод delete был вызван ровно 1 раз с объектом card
        verify(cardInfoRepository, times(1)).delete(card);
    }

    @DisplayName("updateCardInfo_NotFound")
    @Test
    void deleteCardInfo_ShouldThrow_WhenNotFound() {
        // given & when
        // Когда кто-то вызовет cardInfoRepository.findById(1L), верни пустой Optional
        // Это имитирует ситуацию, когда карты с таким ID не существует в базе данных
        when(cardInfoRepository.findById(1L)).thenReturn(Optional.empty());

        // then
        // Проверка: что метод выбросит исключение CardInfoNotFoundException
        // (нельзя удалить карту, которой не существует)
        assertThrows(CardInfoNotFoundException.class, () -> cardInfoService.deleteCardInfo(1L));
    }

    // ----------------- updateCardInfo with userId change -----------------

    @DisplayName("updateCardInfo_WithDifferentUserId_ShouldUpdateUser")
    @Test
    void updateCardInfo_WithDifferentUserId_ShouldUpdateUser() {
        // given
        // Мокируем аутентификацию для ADMIN (чтобы можно было менять владельца)
        JwtAuthenticationToken authentication = createMockAuthentication("admin@example.com", "ADMIN");
        mockSecurityContext(authentication);

        // Создаём нового пользователя с другим ID для проверки смены владельца карты
        User newUser = new User();
        newUser.setId(2L);
        newUser.setEmail("newuser@example.com");

        // Когда кто-то вызовет cardInfoRepository.findById(1L), верни Optional с card
        when(cardInfoRepository.findById(1L)).thenReturn(Optional.of(card));
        // Когда кто-то вызовет userRepository.findById(2L), верни Optional с newUser
        // (это нужно для получения нового пользователя при смене владельца карты)
        when(userRepository.findById(2L)).thenReturn(Optional.of(newUser));

        // Мок save возвращает объект, который был передан
        when(cardInfoRepository.save(any(CardInfo.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // Маппер формирует DTO из текущего состояния объекта CardInfo
        when(cardInfoMapper.toDto(any(CardInfo.class))).thenAnswer(invocation -> {
            CardInfo c = invocation.getArgument(0);
            CardInfoDto dto = new CardInfoDto();
            dto.setId(c.getId());
            dto.setNumber(c.getNumber());
            dto.setHolder(c.getHolder());
            dto.setExpirationDate(c.getExpirationDate());
            dto.setUserId(c.getUser().getId()); // userId берётся из обновлённого объекта
            return dto;
        });

        // Создаём DTO с данными для обновления карты, включая новый userId
        CardInfoDto updateDto = new CardInfoDto();
        updateDto.setNumber("9999 8888 7777 6666");
        updateDto.setHolder("Updated Holder");
        updateDto.setExpirationDate(LocalDate.of(2035, 1, 1));
        updateDto.setUserId(2L); // новый userId — карта переходит к другому пользователю

        //when
        // Вызываем тестируемый метод обновления карты с новым userId
        CardInfoDto result = cardInfoService.updateCardInfo(1L, updateDto);

        // then
        assertNotNull(result); // Проверка: что результат не null
        assertEquals(2L, result.getUserId()); // Проверка: что userId изменился на 2L
        verify(cardInfoRepository, times(1)).save(any(CardInfo.class)); // Проверка: что метод save был вызван ровно 1 раз
        verify(userRepository, times(1)).findById(2L); // Проверка: что метод findById был вызван для нового пользователя
    }

    @DisplayName("updateCardInfo_WithSameUserId_ShouldNotUpdateUser")
    @Test
    void updateCardInfo_WithSameUserId_ShouldNotUpdateUser() {
        // given
        // Мокируем аутентификацию
        JwtAuthenticationToken authentication = createMockAuthentication("test@example.com", "USER");
        mockSecurityContext(authentication);

        // Когда кто-то вызовет cardInfoRepository.findById(1L), верни Optional с card
        when(cardInfoRepository.findById(1L)).thenReturn(Optional.of(card));
        // Мок save возвращает объект, который был передан
        when(cardInfoRepository.save(any(CardInfo.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // Маппер формирует DTO из текущего состояния объекта CardInfo
        when(cardInfoMapper.toDto(any(CardInfo.class))).thenAnswer(invocation -> {
            CardInfo c = invocation.getArgument(0);
            CardInfoDto dto = new CardInfoDto();
            dto.setId(c.getId());
            dto.setNumber(c.getNumber());
            dto.setHolder(c.getHolder());
            dto.setExpirationDate(c.getExpirationDate());
            dto.setUserId(c.getUser().getId());
            return dto;
        });

        // Создаём DTO с данными для обновления карты, но с тем же userId
        CardInfoDto updateDto = new CardInfoDto();
        updateDto.setNumber("9999 8888 7777 6666");
        updateDto.setHolder("Updated Holder");
        updateDto.setExpirationDate(LocalDate.of(2035, 1, 1));
        updateDto.setUserId(1L); // тот же userId — владелец карты не меняется

        //when
        // Вызываем тестируемый метод обновления карты с тем же userId
        CardInfoDto result = cardInfoService.updateCardInfo(1L, updateDto);

        // then
        assertNotNull(result); // Проверка: что результат не null
        assertEquals(1L, result.getUserId()); // Проверка: что userId остался прежним
        verify(cardInfoRepository, times(1)).save(any(CardInfo.class)); // Проверка: что метод save был вызван ровно 1 раз
        // Проверка: что метод findById НЕ был вызван для userRepository
        // (если userId не изменился, нет необходимости искать пользователя в БД)
        verify(userRepository, never()).findById(any());
    }

    @DisplayName("updateCardInfo_WithNullUserId_ShouldNotUpdateUser")
    @Test
    void updateCardInfo_WithNullUserId_ShouldNotUpdateUser() {
        // given
        // Мокируем аутентификацию
        JwtAuthenticationToken authentication = createMockAuthentication("test@example.com", "USER");
        mockSecurityContext(authentication);

        // Когда кто-то вызовет cardInfoRepository.findById(1L), верни Optional с card
        when(cardInfoRepository.findById(1L)).thenReturn(Optional.of(card));
        // Мок save возвращает объект, который был передан
        when(cardInfoRepository.save(any(CardInfo.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // Маппер формирует DTO из текущего состояния объекта CardInfo
        // Обрабатывает случай, когда user может быть null
        when(cardInfoMapper.toDto(any(CardInfo.class))).thenAnswer(invocation -> {
            CardInfo c = invocation.getArgument(0);
            CardInfoDto dto = new CardInfoDto();
            dto.setId(c.getId());
            dto.setNumber(c.getNumber());
            dto.setHolder(c.getHolder());
            dto.setExpirationDate(c.getExpirationDate());
            dto.setUserId(c.getUser() != null ? c.getUser().getId() : null);
            return dto;
        });

        // Создаём DTO с данными для обновления карты, но с null userId
        CardInfoDto updateDto = new CardInfoDto();
        updateDto.setNumber("9999 8888 7777 6666");
        updateDto.setHolder("Updated Holder");
        updateDto.setExpirationDate(LocalDate.of(2035, 1, 1));
        updateDto.setUserId(null); // null userId — владелец карты не указан

        //when
        // Вызываем тестируемый метод обновления карты с null userId
        CardInfoDto result = cardInfoService.updateCardInfo(1L, updateDto);

        // then
        assertNotNull(result); // Проверка: что результат не null
        verify(cardInfoRepository, times(1)).save(any(CardInfo.class)); // Проверка: что метод save был вызван ровно 1 раз
        // Проверка: что метод findById НЕ был вызван для userRepository
        // (если userId равен null, нет необходимости искать пользователя в БД)
        verify(userRepository, never()).findById(any());
    }

}