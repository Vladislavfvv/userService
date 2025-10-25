package com.innowise.demo.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import com.innowise.demo.dto.CardInfoDto;
import com.innowise.demo.model.CardInfo;
import com.innowise.demo.model.User;

@Mapper(componentModel = "spring")
public interface CardInfoMapper {

    @Mapping(target="user", expression = "java(mapUser(dto.getUserId()))")
    CardInfo toEntity(CardInfoDto dto);

    @Mapping(target = "userId", source = "user.id")


    default User mapUser(Long userId) {
        if (userId == null) return null;
        User user = new User();
        user.setId(userId);
        return user;
    }
}
