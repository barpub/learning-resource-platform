package com.example.platform.utils;

import java.time.Duration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisUtil {
    private final RedisTemplate<String, Object> redisTemplate;

    public RedisUtil(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Object get(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception ex) {
            return null;
        }
    }

    public void set(String key, Object value, long seconds) {
        try {
            redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(seconds));
        } catch (Exception ignored) {
            // Redis is optional for local deployment. The database remains source of truth.
        }
    }

    public void delete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception ignored) {
        }
    }
}
