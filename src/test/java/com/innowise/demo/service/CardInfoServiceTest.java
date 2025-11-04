package com.innowise.demo.service;

import java.time.LocalDate;
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

    // ----------------- save CardInfo -----------------
    @DisplayName("saveCardInfo_Positive")
    @Test
    void save_ShouldReturnCardDto_WhenUserExists() {
        // given
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(cardInfoMapper.toEntity(cardDto)).thenReturn(card);
        when(cardInfoRepository.save(card)).thenReturn(card);
        when(cardInfoMapper.toDto(card)).thenReturn(cardDto);

        // when
        CardInfoDto result = cardInfoService.save(cardDto);

        // then
        assertNotNull(result);
        assertEquals(cardDto.getNumber(), result.getNumber());
        verify(cardInfoRepository, times(1)).save(card);
    }

    @DisplayName("saveCardInfo_Negative")
    @Test
    void save_ShouldThrow_WhenUserNotFound() {
        // given
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        // when & then
        assertThrows(UserNotFoundException.class, () -> cardInfoService.save(cardDto));
    }

    // ----------------- getCardInfoById -----------------
    @DisplayName("getCardInfo_Positive")
    @Test
    void getCardInfoById_ShouldReturnCardDto_WhenExists() {
        // given
        when(cardInfoRepository.findById(1L)).thenReturn(Optional.of(card));
        when(cardInfoMapper.toDto(card)).thenReturn(cardDto);

        // when
        CardInfoDto result = cardInfoService.getCardInfoById(1L);

        // then
        assertNotNull(result);
        assertEquals(card.getNumber(), result.getNumber());
    }

    // ----------------- getAllCardInfos -----------------
    @DisplayName("getALLCardInfo_Positive")
    @Test
    void getAllCardInfos_ShouldReturnPage() {
        // given
        List<CardInfo> cards = List.of(card);
        Page<CardInfo> page = new PageImpl<>(cards, PageRequest.of(0, 5), cards.size());
        when(cardInfoRepository.findAll(PageRequest.of(0, 5))).thenReturn(page);
        when(cardInfoMapper.toDto(card)).thenReturn(cardDto);

        // when
        Page<CardInfoDto> result = cardInfoService.getAllCardInfos(0, 5);

        // then
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
    }

    @DisplayName("getALLCardInfo_Negative")
    @Test
    void getAllCardInfos_ShouldThrow_WhenEmpty() {
        // given
        Page<CardInfo> emptyPage = Page.empty();
        when(cardInfoRepository.findAll(PageRequest.of(0, 10))).thenReturn(emptyPage);

        // when & then
        assertThrows(CardInfoNotFoundException.class, () -> cardInfoService.getAllCardInfos(0, 10));
    }

    @DisplayName("updateCardInfo_Positive")
    @Test
    void updateCardInfo_ShouldReturnUpdatedDto() {
        // given
        when(cardInfoRepository.findById(1L)).thenReturn(Optional.of(card));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        // Мок save просто возвращает объект, который передаем
        //save через thenAnswer возвращает объект, который реально был передан, чтобы имитировать сохранение
        when(cardInfoRepository.save(any(CardInfo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Маппер теперь формирует DTO из текущего состояния объекта
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

        // Данные для обновления
        CardInfoDto updateDto = new CardInfoDto();
        updateDto.setNumber("9999 8888 7777 6666");
        updateDto.setHolder("Updated Holder");
        updateDto.setExpirationDate(LocalDate.of(2035, 1, 1));
        updateDto.setUserId(user.getId());

        // when
        CardInfoDto result = cardInfoService.updateCardInfo(1L, updateDto);

        // then
        assertNotNull(result);
        assertEquals("9999 8888 7777 6666", result.getNumber());
        assertEquals("Updated Holder", result.getHolder());
        assertEquals(LocalDate.of(2035, 1, 1), result.getExpirationDate());
        verify(cardInfoRepository, times(1)).save(any(CardInfo.class));
    }

    // ----------------- deleteCardInfo -----------------
    @DisplayName("deleteCardInfo_WhenExists")
    @Test
    void deleteCardInfo_ShouldCallDelete_WhenExists() {
        // given
        when(cardInfoRepository.findById(1L)).thenReturn(Optional.of(card));

        // when
        cardInfoService.deleteCardInfo(1L);

        // then
        verify(cardInfoRepository, times(1)).deleteById(1L);
    }

    @DisplayName("updateCardInfo_NotFound")
    @Test
    void deleteCardInfo_ShouldThrow_WhenNotFound() {
        // given
        when(cardInfoRepository.findById(1L)).thenReturn(Optional.empty());

        // when & then
        assertThrows(CardInfoNotFoundException.class, () -> cardInfoService.deleteCardInfo(1L));
    }

}