package com.innowise.demo.controller;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innowise.demo.dto.CardInfoDto;
import com.innowise.demo.exception.CardInfoNotFoundException;
import com.innowise.demo.service.CardInfoService;

import static java.util.List.of;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CardInfoController.class)
class CardInfoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private CardInfoService cardInfoService;

    private CardInfoDto cardInfoDto;

    @BeforeEach
    void setUp() {
        cardInfoDto = new CardInfoDto();
        cardInfoDto.setId(1L);
        cardInfoDto.setNumber("1234567890123456"); // 16 цифр без пробелов
        cardInfoDto.setHolder("Roma Romanovich");
        cardInfoDto.setExpirationDate(LocalDate.of(2030, 12, 31));
        cardInfoDto.setUserId(1L);
    }

    @Test
    @DisplayName("POST /api/v1/cards - успешное создание карты")
    void addCardInfo_ShouldReturnCreatedCard() throws Exception {
        // given
        CardInfoDto requestDto = new CardInfoDto();
        requestDto.setNumber("1234567890123456"); // 16 цифр без пробелов
        requestDto.setHolder("Roma Romanovich");
        requestDto.setExpirationDate(LocalDate.of(2030, 12, 31));
        requestDto.setUserId(1L);

        CardInfoDto createdDto = new CardInfoDto();
        createdDto.setId(1L);
        createdDto.setNumber("1234567890123456");
        createdDto.setHolder("Roma Romanovich");
        createdDto.setExpirationDate(LocalDate.of(2030, 12, 31));
        createdDto.setUserId(1L);

        when(cardInfoService.save(any(CardInfoDto.class))).thenReturn(createdDto);

        // when & then
        mockMvc.perform(post("/api/v1/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.number").value("1234567890123456"))
                .andExpect(jsonPath("$.holder").value("Roma Romanovich"))
                .andExpect(jsonPath("$.userId").value(1L));
    }

    @Test
    @DisplayName("POST /api/v1/cards - валидация: пустой номер карты")
    void addCardInfo_ShouldReturnBadRequest_WhenNumberIsBlank() throws Exception {
        // given
        CardInfoDto invalidDto = new CardInfoDto();
        invalidDto.setNumber(""); // пустой номер
        invalidDto.setHolder("Roma Romanovich");
        invalidDto.setUserId(1L);

        // when & then
        mockMvc.perform(post("/api/v1/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/cards/1 - успешное получение карты по ID")
    void getCardInfoById_ShouldReturnCard() throws Exception {
        // given
        when(cardInfoService.getCardInfoById(1L)).thenReturn(cardInfoDto);

        // when & then
        mockMvc.perform(get("/api/v1/cards/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.number").value("1234567890123456"))
                .andExpect(jsonPath("$.holder").value("Roma Romanovich"));
    }

    @Test
    @DisplayName("GET /api/v1/cards/999 - карта не найдена")
    void getCardInfoById_ShouldReturnNotFound_WhenCardNotFound() throws Exception {
        // given
        when(cardInfoService.getCardInfoById(999L))
                .thenThrow(new CardInfoNotFoundException("Card with id 999 not found!"));

        // when & then
        mockMvc.perform(get("/api/v1/cards/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/cards - успешное получение списка карт с пагинацией")
    void getAllCardInfos_ShouldReturnPagedResponse() throws Exception {
        // given
        Page<CardInfoDto> page = new PageImpl<>(of(cardInfoDto));
        when(cardInfoService.getAllCardInfos(0, 10)).thenReturn(page);

        // when & then
        mockMvc.perform(get("/api/v1/cards")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1L));
    }

    @Test
    @DisplayName("GET /api/v1/cards - дефолтные параметры пагинации")
    void getAllCardInfos_ShouldUseDefaultPagination() throws Exception {
        // given
        Page<CardInfoDto> page = new PageImpl<>(of(cardInfoDto));
        when(cardInfoService.getAllCardInfos(0, 10)).thenReturn(page);

        // when & then
        mockMvc.perform(get("/api/v1/cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("PUT /api/v1/cards/1 - успешное обновление карты")
    void updateCardInfo_ShouldReturnUpdatedCard() throws Exception {
        // given
        CardInfoDto updateDto = new CardInfoDto();
        updateDto.setNumber("9999888877776666"); // 16 цифр без пробелов
        updateDto.setHolder("Updated Holder");
        updateDto.setExpirationDate(LocalDate.of(2035, 6, 30));
        updateDto.setUserId(1L);

        CardInfoDto updatedDto = new CardInfoDto();
        updatedDto.setId(1L);
        updatedDto.setNumber("9999888877776666");
        updatedDto.setHolder("Updated Holder");
        updatedDto.setExpirationDate(LocalDate.of(2035, 6, 30));
        updatedDto.setUserId(1L);

        when(cardInfoService.updateCardInfo(eq(1L), any(CardInfoDto.class))).thenReturn(updatedDto);

        // when & then
        mockMvc.perform(put("/api/v1/cards/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.number").value("9999888877776666"))
                .andExpect(jsonPath("$.holder").value("Updated Holder"));
    }

    @Test
    @DisplayName("PUT /api/v1/cards/999 - карта не найдена для обновления")
    void updateCardInfo_ShouldReturnNotFound_WhenCardNotFound() throws Exception {
        // given
        CardInfoDto updateDto = new CardInfoDto();
        updateDto.setNumber("9999888877776666"); // 16 цифр без пробелов
        updateDto.setHolder("Updated Holder");
        updateDto.setExpirationDate(LocalDate.of(2035, 6, 30));
        updateDto.setUserId(1L);

        when(cardInfoService.updateCardInfo(eq(999L), any(CardInfoDto.class)))
                .thenThrow(new CardInfoNotFoundException("Card with id 999 not found!"));

        // when & then
        mockMvc.perform(put("/api/v1/cards/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /api/v1/cards/1 - валидация: пустой holder")
    void updateCardInfo_ShouldReturnBadRequest_WhenHolderIsBlank() throws Exception {
        // given
        CardInfoDto invalidDto = new CardInfoDto();
        invalidDto.setNumber("1234567890123456"); // 16 цифр без пробелов
        invalidDto.setHolder(""); // пустой holder
        invalidDto.setExpirationDate(LocalDate.of(2030, 12, 31));

        // when & then
        mockMvc.perform(put("/api/v1/cards/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE /api/v1/cards/1 - успешное удаление карты")
    void deleteCardInfo_ShouldReturnNoContent() throws Exception {
        // when & then
        mockMvc.perform(delete("/api/v1/cards/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/v1/cards/999 - карта не найдена для удаления")
    void deleteCardInfo_ShouldReturnNotFound_WhenCardNotFound() throws Exception {
        // given
        doThrow(new CardInfoNotFoundException("Card with id 999 not found!"))
                .when(cardInfoService).deleteCardInfo(999L);

        // when & then
        mockMvc.perform(delete("/api/v1/cards/999"))
                .andExpect(status().isNotFound());
    }
}

