package com.innowise.demo.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import com.innowise.demo.dto.CardInfoDto;
import com.innowise.demo.model.CardInfo;

@Mapper(componentModel = "spring")
public abstract class CardInfoMapper {

    @Mapping(target = "userId", source = "user.id")
    public abstract CardInfoDto toDto(CardInfo entity);

    /**
     * Преобразование DTO -> Entity без установки пользователя.
     * Привязка пользователя выполняется в сервисе.
     */
    public CardInfo toEntity(CardInfoDto dto) {
        if (dto == null) return null;
        CardInfo entity = new CardInfo();
        entity.setId(dto.getId());
        entity.setNumber(dto.getNumber());
        entity.setHolder(dto.getHolder());
        entity.setExpirationDate(dto.getExpirationDate());
        return entity;
    }
}

