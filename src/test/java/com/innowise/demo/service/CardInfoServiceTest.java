package com.innowise.demo.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

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

    @Mock
    private UserRepository userRepository;

    private CardInfo card;
    private CardInfoDto cardDto;
    private User user;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "admin@example.com",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                )
        );

        user = new User();
        user.setId(1L);
        user.setEmail("user@example.com");

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

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCardInfoById_Exists_ReturnsDto() {
        CardInfo cardInfo = new CardInfo();
        cardInfo.setId(1L);
        CardInfoDto dto = new CardInfoDto();
        dto.setId(1L);

        when(cardInfoRepository.findById(1L)).thenReturn(Optional.of(cardInfo));
        when(cardInfoMapper.toDto(cardInfo)).thenReturn(dto);

        CardInfoDto result = cardInfoService.getCardInfoById(1L);
        assertNotNull(result);
        assertEquals(1L, result.getId());
    }

    @Test
    void getCardInfoById_NotFound_ThrowsException() {
        when(cardInfoRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(CardInfoNotFoundException.class, () -> cardInfoService.getCardInfoById(1L));
    }

    @DisplayName("saveCardInfo_Positive")
    @Test
    void save_ShouldReturnCardDto_WhenUserExists() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(cardInfoMapper.toEntity(cardDto)).thenReturn(card);
        when(cardInfoRepository.save(card)).thenReturn(card);
        when(cardInfoMapper.toDto(card)).thenReturn(cardDto);

        CardInfoDto result = cardInfoService.save(cardDto);

        assertNotNull(result);
        assertEquals(cardDto.getNumber(), result.getNumber());
        verify(cardInfoRepository, times(1)).save(card);
    }

    @DisplayName("saveCardInfo_Negative")
    @Test
    void save_ShouldThrow_WhenUserNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> cardInfoService.save(cardDto));
    }

    @DisplayName("getCardInfo_Positive")
    @Test
    void getCardInfoById_ShouldReturnCardDto_WhenExists() {
        when(cardInfoRepository.findById(1L)).thenReturn(Optional.of(card));
        when(cardInfoMapper.toDto(card)).thenReturn(cardDto);

        CardInfoDto result = cardInfoService.getCardInfoById(1L);

        assertNotNull(result);
        assertEquals(card.getNumber(), result.getNumber());
    }

    @DisplayName("getALLCardInfo_Positive")
    @Test
    void getAllCardInfos_ShouldReturnPage() {
        List<CardInfo> cards = List.of(card);
        Page<CardInfo> page = new PageImpl<>(cards, PageRequest.of(0, 5), cards.size());
        when(cardInfoRepository.findAll(PageRequest.of(0, 5))).thenReturn(page);
        when(cardInfoMapper.toDto(card)).thenReturn(cardDto);

        Page<CardInfoDto> result = cardInfoService.getAllCardInfos(0, 5);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
    }

    @DisplayName("getALLCardInfo_Negative")
    @Test
    void getAllCardInfos_ShouldThrow_WhenEmpty() {
        Page<CardInfo> emptyPage = Page.empty();
        when(cardInfoRepository.findAll(PageRequest.of(0, 10))).thenReturn(emptyPage);

        assertThrows(CardInfoNotFoundException.class, () -> cardInfoService.getAllCardInfos(0, 10));
    }

    @DisplayName("updateCardInfo_Positive")
    @Test
    void updateCardInfo_ShouldReturnUpdatedDto() {
        when(cardInfoRepository.findById(1L)).thenReturn(Optional.of(card));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(cardInfoRepository.save(any(CardInfo.class))).thenAnswer(invocation -> invocation.getArgument(0));
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

        CardInfoDto updateDto = new CardInfoDto();
        updateDto.setNumber("9999 8888 7777 6666");
        updateDto.setHolder("Updated Holder");
        updateDto.setExpirationDate(LocalDate.of(2035, 1, 1));
        updateDto.setUserId(user.getId());

        CardInfoDto result = cardInfoService.updateCardInfo(1L, updateDto);

        assertNotNull(result);
        assertEquals("9999 8888 7777 6666", result.getNumber());
        assertEquals("Updated Holder", result.getHolder());
        assertEquals(LocalDate.of(2035, 1, 1), result.getExpirationDate());
        verify(cardInfoRepository, times(1)).save(any(CardInfo.class));
    }

    @DisplayName("deleteCardInfo_WhenExists")
    @Test
    void deleteCardInfo_ShouldCallDelete_WhenExists() {
        when(cardInfoRepository.findById(1L)).thenReturn(Optional.of(card));

        cardInfoService.deleteCardInfo(1L);

        verify(cardInfoRepository, times(1)).delete(card);
    }

    @DisplayName("updateCardInfo_NotFound")
    @Test
    void deleteCardInfo_ShouldThrow_WhenNotFound() {
        when(cardInfoRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(CardInfoNotFoundException.class, () -> cardInfoService.deleteCardInfo(1L));
    }

    @DisplayName("updateCardInfo_WithDifferentUserId_ShouldUpdateUser")
    @Test
    void updateCardInfo_WithDifferentUserId_ShouldUpdateUser() {
        User newUser = new User();
        newUser.setId(2L);
        newUser.setEmail("other@example.com");

        when(cardInfoRepository.findById(1L)).thenReturn(Optional.of(card));
        when(userRepository.findById(2L)).thenReturn(Optional.of(newUser));
        when(cardInfoRepository.save(any(CardInfo.class))).thenAnswer(invocation -> invocation.getArgument(0));
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

        CardInfoDto updateDto = new CardInfoDto();
        updateDto.setNumber("9999 8888 7777 6666");
        updateDto.setHolder("Updated Holder");
        updateDto.setExpirationDate(LocalDate.of(2035, 1, 1));
        updateDto.setUserId(2L);

        CardInfoDto result = cardInfoService.updateCardInfo(1L, updateDto);

        assertNotNull(result);
        assertEquals(2L, result.getUserId());
        verify(cardInfoRepository, times(1)).save(any(CardInfo.class));
        verify(userRepository, times(1)).findById(2L);
    }

    @DisplayName("updateCardInfo_WithSameUserId_ShouldNotUpdateUser")
    @Test
    void updateCardInfo_WithSameUserId_ShouldNotUpdateUser() {
        when(cardInfoRepository.findById(1L)).thenReturn(Optional.of(card));
        when(cardInfoRepository.save(any(CardInfo.class))).thenAnswer(invocation -> invocation.getArgument(0));
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

        CardInfoDto updateDto = new CardInfoDto();
        updateDto.setNumber("9999 8888 7777 6666");
        updateDto.setHolder("Updated Holder");
        updateDto.setExpirationDate(LocalDate.of(2035, 1, 1));
        updateDto.setUserId(1L);

        CardInfoDto result = cardInfoService.updateCardInfo(1L, updateDto);

        assertNotNull(result);
        assertEquals(1L, result.getUserId());
        verify(cardInfoRepository, times(1)).save(any(CardInfo.class));
        verify(userRepository, never()).findById(any());
    }

    @DisplayName("updateCardInfo_WithNullUserId_ShouldNotUpdateUser")
    @Test
    void updateCardInfo_WithNullUserId_ShouldNotUpdateUser() {
        when(cardInfoRepository.findById(1L)).thenReturn(Optional.of(card));
        when(cardInfoRepository.save(any(CardInfo.class))).thenAnswer(invocation -> invocation.getArgument(0));
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

        CardInfoDto updateDto = new CardInfoDto();
        updateDto.setNumber("9999 8888 7777 6666");
        updateDto.setHolder("Updated Holder");
        updateDto.setExpirationDate(LocalDate.of(2035, 1, 1));
        updateDto.setUserId(null);

        CardInfoDto result = cardInfoService.updateCardInfo(1L, updateDto);

        assertNotNull(result);
        verify(cardInfoRepository, times(1)).save(any(CardInfo.class));
        verify(userRepository, never()).findById(any());
    }
}