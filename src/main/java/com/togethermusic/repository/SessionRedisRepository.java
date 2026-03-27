package com.togethermusic.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.togethermusic.room.model.SessionUser;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * WebSocket 会话用户 Repository
 * Key: tm:session:{houseId}，Hash 结构，sessionId -> SessionUser JSON
 */
@Repository
public class SessionRedisRepository extends BaseRedisRepository {

    public SessionRedisRepository(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        super(redisTemplate, objectMapper);
    }

    public void put(String houseId, SessionUser user) {
        redisTemplate.opsForHash().put(
                RedisKeyConstants.session(houseId),
                user.sessionId(),
                toJson(user)
        );
    }

    public Optional<SessionUser> get(String houseId, String sessionId) {
        Object value = redisTemplate.opsForHash().get(RedisKeyConstants.session(houseId), sessionId);
        return fromJson(value != null ? value.toString() : null, SessionUser.class);
    }

    public void remove(String houseId, String sessionId) {
        redisTemplate.opsForHash().delete(RedisKeyConstants.session(houseId), sessionId);
    }

    public List<SessionUser> findAll(String houseId) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(RedisKeyConstants.session(houseId));
        return entries.values().stream()
                .map(v -> fromJson(v.toString(), SessionUser.class))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    public long count(String houseId) {
        return redisTemplate.opsForHash().size(RedisKeyConstants.session(houseId));
    }

    public boolean exists(String houseId, String sessionId) {
        return Boolean.TRUE.equals(redisTemplate.opsForHash().hasKey(RedisKeyConstants.session(houseId), sessionId));
    }

    public void deleteAll(String houseId) {
        redisTemplate.delete(RedisKeyConstants.session(houseId));
    }
}
