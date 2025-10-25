package com.innowise.demo.mapper;


import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import com.innowise.demo.dto.UserDto;
import com.innowise.demo.model.User;

@Mapper(componentModel = "spring")
public interface UserMapper {
    @Mapping(target = "cardIds", expression = "java(user.getCards() != null ? user.getCards().stream().map(c -> c.getId()).collect(Collectors.toList()) : null)")
    UserDto toDto(User user);

    @Mapping(target = "cards", ignore = true) // карты устанавливаются отдельно
    User toEntity(UserDto dto);
}
