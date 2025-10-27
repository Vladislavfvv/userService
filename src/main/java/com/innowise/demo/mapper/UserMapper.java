package com.innowise.demo.mapper;


import java.util.List;
import java.util.stream.Collectors;

import org.mapstruct.BeanMapping;
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

    //@Mapping(target = "cardIds", expression = "java(user.getCards() != null ? user.getCards().stream().map(c -> c.getId()).collect(Collectors.toList()) : null)")
    // @Mapping(target = "birthDate", source = "birthDate") // поле LocalDate может иметь разное имя
    @Mapping(target = "cards", source = "cards", qualifiedByName = "mapCardListToDto")
    //@Mapping(target = "cards", source = "cards") // временно игнорируем
    //@Mapping(target = "cards", ignore = true) // временно игнорируем
    public abstract UserDto toDto(User user);

    //@Mapping(target = "birthDate", source = "birthDate")
    //@Mapping(target = "cards", ignore = true) // карты устанавливаются отдельно

    //@Mapping(target = "cards", source = "cards" , expression = "java(dto.getCards() != null ? new ArrayList<>(dto.getCards().stream().map(c -> cardMapper.toEntity(c)).toList()) : null)")
    @Mapping(target = "cards", expression = "java(dto.getCards() != null ? new java.util.ArrayList<>(dto.getCards().stream().map(cardInfoMapper::toEntity).toList()) : null)")
    public abstract User toEntity(UserDto dto);
//    @BeanMapping(ignoreByDefault = true)//говорим MapStruct: «по умолчанию игнорировать все поля, явно мапим только нужные»
//    @Mapping(target = "id", source = "id")
//    @Mapping(target = "name", source = "name")
//    @Mapping(target = "surname", source = "surname")
//    @Mapping(target = "birthDate", source = "birthDate")
//    @Mapping(target = "email", source = "email")


    // public abstract User toEntity(UserDto dto);

    // Вспомогательные методы для маппинга карт
//    @Named("mapCardListToDto")
//    default List<CardInfoDto> mapCardListToDto(List<CardInfo> cards) {
//        if (cards == null) return null;
//        return cards.stream().collect(Collectors.toList());
//    }


    @Named("mapCardListToDto")
    protected List<CardInfoDto> mapCardListToDto(List<CardInfo> cards) {
        if (cards == null) return null;
        return cards.stream()
                .map(cardInfoMapper::toDto)
                .collect(Collectors.toList());
    }


    public CardInfoMapper getCardInfoMapper() {
        return cardInfoMapper;
    }

}
