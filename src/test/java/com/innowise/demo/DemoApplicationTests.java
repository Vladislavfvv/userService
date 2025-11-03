package com.innowise.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import com.innowise.demo.integration.BaseIntegrationTest;

//@Disabled("Temporary")
@SpringBootTest
class DemoApplicationTests extends BaseIntegrationTest {


    @Test
    void contextLoads() {
        // Проверка загрузки контекста Spring Boot. Тест пройден, если контекст стартует без ошибок.
    }

}
