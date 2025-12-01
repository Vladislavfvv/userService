package com.innowise.demo.config;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.innowise.demo.model.User;
import com.innowise.demo.repository.UserRepository;

/**
 * Проверка наличия администратора при запуске приложения.
 * Логирует информацию о наличии админа в us_db.
 */
@Configuration
@Profile("!test") // Не выполняется в тестах
public class AdminInitializer {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);
    private static final String ADMIN_EMAIL = "admin@tut.by";

    @Bean
    public ApplicationRunner adminInitializerRunner(UserRepository userRepository) {
        return args -> {
            // Проверяем, существует ли админ в us_db
            Optional<User> adminUser = userRepository.findByEmailNativeQuery(ADMIN_EMAIL);
            
            if (adminUser.isEmpty()) {
                log.warn("Admin user {} not found in us_db. Admin should be created via SQL script or registration.", ADMIN_EMAIL);
                return;
            }

            User admin = adminUser.get();
            log.info("Admin user {} found in us_db (id: {})", ADMIN_EMAIL, admin.getId());
        };
    }
}

