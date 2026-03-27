package com.togethermusic.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;

/**
 * 房间运行时配置 Repository
 * 使用 Redis Hash 存储，每个字段独立读写，避免整体序列化的并发问题
 */
@Repository
public class ConfigRedisRepository extends BaseRedisRepository {

    public ConfigRedisRepository(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        super(redisTemplate, objectMapper);
    }

    public void set(String houseId, String field, String value) {
        redisTemplate.opsForHash().put(RedisKeyConstants.config(houseId), field, value);
    }

    public Optional<String> get(String houseId, String field) {
        Object value = redisTemplate.opsForHash().get(RedisKeyConstants.config(houseId), field);
        return Optional.ofNullable(value).map(Object::toString);
    }

    public Map<Object, Object> getAll(String houseId) {
        return redisTemplate.opsForHash().entries(RedisKeyConstants.config(houseId));
    }

    public void setAll(String houseId, Map<String, String> fields) {
        redisTemplate.opsForHash().putAll(RedisKeyConstants.config(houseId), fields);
    }

    public Boolean getBoolean(String houseId, String field) {
        return get(houseId, field).map(Boolean::parseBoolean).orElse(null);
    }

    public void setBoolean(String houseId, String field, boolean value) {
        set(houseId, field, String.valueOf(value));
    }

    public Float getFloat(String houseId, String field) {
        return get(houseId, field).map(Float::parseFloat).orElse(null);
    }

    public void setFloat(String houseId, String field, float value) {
        set(houseId, field, String.valueOf(value));
    }

    public Long getLong(String houseId, String field) {
        return get(houseId, field).map(Long::parseLong).orElse(null);
    }

    public void setLong(String houseId, String field, long value) {
        set(houseId, field, String.valueOf(value));
    }

    public void delete(String houseId) {
        redisTemplate.delete(RedisKeyConstants.config(houseId));
    }
}
