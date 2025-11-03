package com.innowise.demo.controller;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/cache")
@RequiredArgsConstructor
public class CacheController {
    private final RedisTemplate<String, Object> redisTemplate;
    private static final String MESSAGE_KEY = "message";
    /**
     * Получить список всех ключей, которые сейчас хранятся в Redis.
     */
    @GetMapping("/keys")
    public Map<String, Object> getAllCacheKeys() {
        Set<String> keys = redisTemplate.keys("*");
        int count = keys != null ? keys.size() : 0;

        log.info("[CACHE] Получено {} ключей из Redis", count);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("count", count);
        response.put("keys", keys != null ? keys : Collections.emptySet());
        return response;
    }

    /**
     * Получить конкретное значение по ключу.
     */
    @GetMapping("/value")
    public Map<String, Object> getCacheValue(@RequestParam String key) {
        Object value = redisTemplate.opsForValue().get(key);

        if (value == null) {
            log.warn(" [CACHE] Ключ '{}' не найден в Redis", key);
            return Map.of(
                    "key", key,
                    "found", false,
                    "MESSAGE_KEY", "Значение не найдено в кэше"
            );
        }
        log.info(" [CACHE] Получено значение по ключу '{}'", key);
        return Map.of(
                "key", key,
                "found", true,
                "value", value
        );
    }
    /**
     * Очистить весь кэш Redis.
     */
    @DeleteMapping("/clear")
    public Map<String, Object> clearAllCache() {
        Set<String> keys = redisTemplate.keys("*");

        if (keys == null || keys.isEmpty()) {
            log.info(" [CACHE] Кэш уже пуст.");
            return Map.of(
                    "cleared", false,
                    "MESSAGE_KEY", "Кэш уже пуст."
            );
        }

        redisTemplate.delete(keys);
        log.warn(" [CACHE] Очистка кэша завершена. Удалено {} ключей.", keys.size());

        return Map.of(
                "cleared", true,
                "deletedKeysCount", keys.size(),
                "MESSAGE_KEY", "Кэш успешно очищен."
        );
    }
}
