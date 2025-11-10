package com.innowise.demo.security;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import com.innowise.demo.model.CardInfo;
import com.innowise.demo.model.User;
import com.innowise.demo.repository.CardInfoRepository;
import com.innowise.demo.repository.UserRepository;

import lombok.RequiredArgsConstructor;

//Этот компонент умеет вытаскивать текущий идентификатор пользователя из JWT
// (email, preferred_username, sub), различать админа и проверять владение пользователем/картой
//В AccessManager мы последовательно достаем идентификатор пользователя:
//        сперва email,
//        если нет — preferred_username(удобное имя пользователя, чаще всего логин. Его можно менять, поэтому это не строгий идентификатор),
//        если тоже нет — sub - UUID.
@Component("accessManager")
@RequiredArgsConstructor
public class AccessManager {

    private final UserRepository userRepository;
    private final CardInfoRepository cardInfoRepository;

    public boolean canAccessUser(Long userId, Authentication authentication) {
        if (userId == null) {
            return hasAdminRole(authentication);
        }
        if (hasAdminRole(authentication)) {
            return true;
        }
        return userRepository.findById(userId)
                .map(User::getEmail)
                .map(email -> emailEqualsCurrentUser(email, authentication))
                .orElse(false);
    }

    public boolean canAccessUserByEmail(String email, Authentication authentication) {
        if (email == null) {
            return false;
        }
        if (hasAdminRole(authentication)) {
            return true;
        }
        return emailEqualsCurrentUser(email, authentication);
    }

    public boolean canAccessCard(Long cardId, Authentication authentication) {
        if (cardId == null) {
            return false;
        }
        if (hasAdminRole(authentication)) {
            return true;
        }
        return cardInfoRepository.findById(cardId)
                .map(CardInfo::getUser)
                .map(owner -> owner != null && owner.getEmail() != null
                        && emailEqualsCurrentUser(owner.getEmail(), authentication))
                .orElse(false);
    }

    public boolean isAdmin(Authentication authentication) {
        return hasAdminRole(authentication);
    }

    public Optional<String> currentUserIdentifier(Authentication authentication) {
        return Optional.ofNullable(resolveCurrentIdentifier(authentication));
    }

    private boolean emailEqualsCurrentUser(String email, Authentication authentication) {
        String current = resolveCurrentIdentifier(authentication);
        return current != null && email.equalsIgnoreCase(current);
    }

    private boolean hasAdminRole(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }

    private String resolveCurrentIdentifier(Authentication authentication) {
        if (authentication == null) {
            return null;
        }

        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            Jwt jwt = jwtAuthenticationToken.getToken();
            return Optional.ofNullable(jwt.getClaimAsString("email"))
                    .or(() -> Optional.ofNullable(jwt.getClaimAsString("preferred_username")))
                    .or(() -> Optional.ofNullable(jwt.getSubject()))
                    .orElse(null);
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }

        String name = authentication.getName();
        return name != null && !name.isBlank() ? name : null;
    }
}

