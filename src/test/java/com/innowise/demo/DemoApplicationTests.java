package com.innowise.demo;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import com.innowise.demo.integration.BaseIntegrationTest;

@ActiveProfiles("test")
class DemoApplicationTests extends BaseIntegrationTest {


    @Test
    void contextLoads() {
        // Проверка загрузки контекста Spring Boot. Тест пройден, если контекст стартует без ошибок.
    }

}
