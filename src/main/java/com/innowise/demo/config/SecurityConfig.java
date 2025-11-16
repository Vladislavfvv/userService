package com.innowise.demo.config;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)//for define security on methods
@Profile({"keycloak", "auth-service"})
public class SecurityConfig {

    private static final String ROLE_PREFIX = "ROLE_";

    private final JwtDecoder jwtDecoder;

    public SecurityConfig(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/cache/**").hasRole("ADMIN")
                    // Actuator health endpoint доступен без аутентификации
                    .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                    .requestMatchers("/api/v1/users/sync").permitAll() // Разрешаем доступ без JWT для синхронизации (проверка API ключа в контроллере)
                    .requestMatchers("/api/v1/users/**", "/api/v1/cards/**").hasAnyRole("ADMIN", "USER")
                    .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder)
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            );

        return http.build();
    }

    /**
     * Конвертер для извлечения ролей из JWT токена.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopesConverter = new JwtGrantedAuthoritiesConverter();

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Set<GrantedAuthority> authorities = new HashSet<>();
            Collection<GrantedAuthority> scopeAuthorities = scopesConverter.convert(jwt);
            if (scopeAuthorities != null) {
                authorities.addAll(scopeAuthorities);
            }

            extractRealmRoles(jwt.getClaimAsMap("realm_access"), authorities);
            extractResourceRoles(jwt.getClaimAsMap("resource_access"), authorities);

            String singleRole = jwt.getClaimAsString("role");
            if (StringUtils.hasText(singleRole)) {
                authorities.add(new SimpleGrantedAuthority(formatRole(singleRole)));
            }

            @SuppressWarnings("unchecked")
            Collection<String> flatRoles = (Collection<String>) jwt.getClaim("roles");
            if (flatRoles != null) {
                flatRoles.stream()
                        .filter(StringUtils::hasText)
                        .map(this::formatRole)
                        .map(SimpleGrantedAuthority::new)
                        .forEach(authorities::add);
            }

            return authorities;
        });
        return converter;
    }

    private void extractRealmRoles(Map<String, Object> realmAccess,
                                   Set<GrantedAuthority> authorities) {
        if (realmAccess == null) {
            return;
        }
        addRoles(realmAccess.get("roles"), authorities);
    }

    private void extractResourceRoles(Map<String, Object> resourceAccess,
                                      Set<GrantedAuthority> authorities) {
        if (resourceAccess == null) {
            return;
        }
        resourceAccess.values().forEach(value -> {
            if (value instanceof Map<?, ?> map) {
                addRoles(map.get("roles"), authorities);
            }
        });
    }

    private void addRoles(Object source, Set<GrantedAuthority> authorities) {
        if (source instanceof Collection<?> collection) {
            collection.stream()
                    .map(Object::toString)
                    .filter(StringUtils::hasText)
                    .map(this::formatRole)
                    .map(SimpleGrantedAuthority::new)
                    .forEach(authorities::add);
        }
    }

    private String formatRole(String role) {
        String value = role.startsWith(ROLE_PREFIX) ? role : ROLE_PREFIX + role;
        return value.toUpperCase();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*")); // пока все домены
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}


