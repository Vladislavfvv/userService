package com.innowise.demo.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import com.innowise.demo.dto.CardInfoDto;
import com.innowise.demo.model.CardInfo;

@Mapper(componentModel = "spring", uses = {UserIdMapper.class})
public interface CardInfoMapper {

    @Mapping(target = "userId", source = "user.id")
    CardInfoDto toDto(CardInfo entity);

    @Mapping(target = "user", source = "userId")
    CardInfo toEntity(CardInfoDto dto);
}

