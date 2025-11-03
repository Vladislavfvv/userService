package com.innowise.demo.mapper;

import jakarta.persistence.EntityNotFoundException;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;
import com.innowise.demo.dto.CardInfoDto;
import com.innowise.demo.model.CardInfo;
import com.innowise.demo.model.User;
import com.innowise.demo.repository.CardInfoRepository;
import com.innowise.demo.repository.UserRepository;

@Mapper(componentModel = "spring")

public abstract class CardInfoMapper {

    protected UserRepository userRepository;

    @Mapping(target = "userId", source = "user.id")
    public abstract CardInfoDto toDto(CardInfo entity);

    /**
     * Создаёт CardInfo из DTO без удаления старых объектов.
     * User будет назначен вручную в сервисе.
     */
    @Mapping(target = "user", expression = "java(mapUser(dto.getUserId()))")
    public CardInfo toEntity(CardInfoDto dto) {
        if (dto == null) return null;
        CardInfo entity = new CardInfo();
        entity.setId(dto.getId());
        entity.setNumber(dto.getNumber());
        entity.setHolder(dto.getHolder());
        entity.setExpirationDate(dto.getExpirationDate());
        // User привязываем вручную в сервисе, чтобы не ломать связь
        return entity;
    }

//    /**
//     * Вспомогательный метод для поиска User по id (для CardInfoDto.userId)
//     */
//    public User mapUser(Long userId) {
//        if (userId == null) return null;
//        return userRepository.findById(userId)
//                .orElseThrow(() -> new EntityNotFoundException("User with id " + userId + " not found"));
//    }
}

