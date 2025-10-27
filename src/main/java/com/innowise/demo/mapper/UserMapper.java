package com.innowise.demo.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;
import com.innowise.demo.dto.CardInfoDto;
import com.innowise.demo.dto.UserDto;
import com.innowise.demo.model.CardInfo;
import com.innowise.demo.model.User;

@Mapper(componentModel = "spring", uses = {CardInfoMapper.class})
public abstract class UserMapper {

    @Autowired
    protected CardInfoMapper cardInfoMapper;

    @Mapping(target = "cards", ignore = true) // управление картами вручную в сервисе
    public abstract User toEntity(UserDto dto);

    @Mapping(target = "cards", source = "cards", qualifiedByName = "mapCardListToDto")
    public abstract UserDto toDto(User user);

    @Named("mapCardListToDto")
    protected List<CardInfoDto> mapCardListToDto(List<CardInfo> cards) {
        if (cards == null) return null;
        return cards.stream()
                .map(cardInfoMapper::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Вспомогательный метод для обновления списка карт пользователя.
     * Используется внутри UserService.updateUser.
     */
    public List<CardInfo> updateCards(User user, List<CardInfoDto> cardDtos) {
        if (cardDtos == null) return new ArrayList<>();

        Map<Long, CardInfo> existingCardsMap = user.getCards().stream()
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
                CardInfo newCard = cardInfoMapper.toEntity(dto);
                newCard.setUser(user);
                updatedCards.add(newCard);
            }
        }

        return updatedCards;
    }
}

