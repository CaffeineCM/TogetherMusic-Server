package com.togethermusic.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 投票切歌 Repository
 * 使用 Redis Set 存储本轮参与投票的 sessionId，天然保证单次投票
 */
@Repository
public class VoteRedisRepository extends BaseRedisRepository {

    public VoteRedisRepository(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        super(redisTemplate, objectMapper);
    }

    /**
     * 投票，返回本次是否为新增投票（0 表示已投过）
     */
    public long vote(String houseId, String sessionId) {
        Long result = redisTemplate.opsForSet().add(RedisKeyConstants.vote(houseId), sessionId);
        return result != null ? result : 0L;
    }

    /**
     * 获取当前投票数
     */
    public long count(String houseId) {
        Long size = redisTemplate.opsForSet().size(RedisKeyConstants.vote(houseId));
        return size != null ? size : 0L;
    }

    /**
     * 重置投票（切歌后清空）
     */
    public void reset(String houseId) {
        redisTemplate.delete(RedisKeyConstants.vote(houseId));
    }
}
