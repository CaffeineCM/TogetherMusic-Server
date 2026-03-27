package com.togethermusic.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Optional;

/**
 * Redis Repository 基类，提供 JSON 序列化/反序列化工具方法
 */
@Slf4j
@RequiredArgsConstructor
public abstract class BaseRedisRepository {

    protected final RedisTemplate<String, String> redisTemplate;
    protected final ObjectMapper objectMapper;

    protected <T> String toJson(T obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize object to JSON", e);
        }
    }

    protected <T> Optional<T> fromJson(String json, Class<T> clazz) {
        if (json == null) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(json, clazz));
        } catch (Exception e) {
            log.warn("Failed to deserialize JSON to {}: {}", clazz.getSimpleName(), e.getMessage());
            return Optional.empty();
        }
    }

    protected <T> Optional<T> fromJson(String json, TypeReference<T> typeRef) {
        if (json == null) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(json, typeRef));
        } catch (Exception e) {
            log.warn("Failed to deserialize JSON: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
