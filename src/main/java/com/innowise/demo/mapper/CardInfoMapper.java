package com.innowise.demo.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import com.innowise.demo.dto.CardInfoDto;
import com.innowise.demo.model.CardInfo;
import com.innowise.demo.model.User;

//@Mapper(componentModel = "spring")
//public interface CardInfoMapper {
//
//    @Mapping(target="user", source = "userId", qualifiedByName = "mapUserById")
//    CardInfo toEntity(CardInfoDto dto);
//
//    @Mapping(target = "userId", source = "user.id")
//    CardInfoDto toDto(CardInfo entity);
//
//
////    default User mapUser(Long userId) {
////        if (userId == null) return null;
////        User user = new User();
////        user.setId(userId);
////        return user;
////    }
//
//    // вспомогательный метод для MapStruct
//    @Named("mapUserById")
//    default User mapUserById(Long userId) {
//        if (userId == null) return null;
//        User user = new User();
//        user.setId(userId);
//        return user;
//    }
//}
/// ///////////////////////////////
//@Mapper(componentModel = "spring")
//public interface CardInfoMapper {
//
//    //@Mapping(target="user", expression = "java(mapUser(dto.getUserId()))")
//
//    //@Mapping(target = "user", expression = "java(dtoToUser(dto))")
//
//    CardInfo toEntity(CardInfoDto dto);
//
//    //@Mapping(target = "userId", source = "user.id")
//
//
//   // @Mapping(target = "userId", expression = "java(entity.getUser() != null ? entity.getUser().getId() : null)")
//    CardInfoDto toDto(CardInfo entity);
//
//    default User dtoToUser(CardInfoDto dto) {
//        if (dto == null || dto.getUserId() == null) return null;
//        User user = new User();
//        user.setId(dto.getUserId());
//        return user;
//    }
//
//    default User mapUser(Long userId) {
//        if (userId == null) return null;
//        User user = new User();
//        user.setId(userId);
//        return user;
//    }
//
//}

@Mapper(componentModel = "spring")
public interface CardInfoMapper {

    //@Mapping(target = "user", expression = "java(dtoToUser(dto))")
    @Mapping(target = "user", source = "userId", qualifiedByName = "mapUser")
    CardInfo toEntity(CardInfoDto dto);

    @Mapping(target = "userId", expression = "java(entity.getUser() != null ? entity.getUser().getId() : null)")
    CardInfoDto toDto(CardInfo entity);

    default User dtoToUser(CardInfoDto dto) {
        if (dto == null || dto.getUserId() == null) return null;
        User user = new User();
        user.setId(dto.getUserId());
        return user;
    }

    @Named("mapUser")
    default User mapUser(Long userId) {
        if (userId == null) return null;
        User user = new User();
        user.setId(userId);
        return user;
    }
}

