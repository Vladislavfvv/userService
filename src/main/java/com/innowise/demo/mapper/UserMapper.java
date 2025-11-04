package com.innowise.demo.mapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import com.innowise.demo.dto.CardInfoDto;
import com.innowise.demo.dto.UserDto;
import com.innowise.demo.model.CardInfo;
import com.innowise.demo.model.User;

@Mapper(componentModel = "spring", uses = {CardInfoMapper.class})
public interface UserMapper {

    @Mapping(target = "cards", source = "cards")
    User toEntity(UserDto dto);

    @Mapping(target = "cards", source = "cards")
    UserDto toDto(User user);

    default List<CardInfo> updateCards(User user, List<CardInfoDto> cardDtos) {
        if (cardDtos == null || cardDtos.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Long, CardInfo> existingCardsMap = Optional.ofNullable(user.getCards())
                .orElse(Collections.emptyList())
                .stream()
                .filter(c -> c.getId() != null)
                .collect(Collectors.toMap(CardInfo::getId, c -> c));

        List<CardInfo> updatedCards = new ArrayList<>();

        for (CardInfoDto dto : cardDtos) {
            if (dto.getId() != null && existingCardsMap.containsKey(dto.getId())) {
                CardInfo existingCard = existingCardsMap.get(dto.getId());
                existingCard.setNumber(dto.getNumber());
                existingCard.setHolder(dto.getHolder());
                existingCard.setExpirationDate(dto.getExpirationDate());
                updatedCards.add(existingCard);
            } else {
                CardInfo newCard = new CardInfo();
                newCard.setNumber(dto.getNumber());
                newCard.setHolder(dto.getHolder());
                newCard.setExpirationDate(dto.getExpirationDate());
                newCard.setUser(user);
                updatedCards.add(newCard);
            }
        }

        return updatedCards;
    }
}

