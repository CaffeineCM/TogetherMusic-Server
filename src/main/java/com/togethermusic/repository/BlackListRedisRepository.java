package com.togethermusic.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Set;

/**
 * 黑名单 Repository（用户黑名单 + 音乐黑名单）
 * 使用 Redis Set 存储
 */
@Repository
public class BlackListRedisRepository extends BaseRedisRepository {

    public BlackListRedisRepository(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        super(redisTemplate, objectMapper);
    }

    // ---- 用户黑名单 ----

    public long blackUser(String houseId, String sessionId) {
        Long result = redisTemplate.opsForSet().add(RedisKeyConstants.blackUser(houseId), sessionId);
        return result != null ? result : 0L;
    }

    public long unblackUser(String houseId, String sessionId) {
        Long result = redisTemplate.opsForSet().remove(RedisKeyConstants.blackUser(houseId), sessionId);
        return result != null ? result : 0L;
    }

    public boolean isUserBlacklisted(String houseId, String sessionId) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(RedisKeyConstants.blackUser(houseId), sessionId));
    }

    public Set<String> getBlacklistedUsers(String houseId) {
        Set<String> members = redisTemplate.opsForSet().members(RedisKeyConstants.blackUser(houseId));
        return members != null ? members : Set.of();
    }

    // ---- 音乐黑名单 ----

    public long blackMusic(String houseId, String musicId) {
        Long result = redisTemplate.opsForSet().add(RedisKeyConstants.blackMusic(houseId), musicId);
        return result != null ? result : 0L;
    }

    public long unblackMusic(String houseId, String musicId) {
        Long result = redisTemplate.opsForSet().remove(RedisKeyConstants.blackMusic(houseId), musicId);
        return result != null ? result : 0L;
    }

    public boolean isMusicBlacklisted(String houseId, String musicId) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(RedisKeyConstants.blackMusic(houseId), musicId));
    }

    public Set<String> getBlacklistedMusic(String houseId) {
        Set<String> members = redisTemplate.opsForSet().members(RedisKeyConstants.blackMusic(houseId));
        return members != null ? members : Set.of();
    }
}
