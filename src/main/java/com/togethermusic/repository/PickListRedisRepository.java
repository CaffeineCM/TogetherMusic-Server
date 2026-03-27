package com.togethermusic.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.togethermusic.music.model.Music;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 点歌队列 + 默认播放列表 Repository
 * 使用 Redis List 存储，队首（index 0）为下一首待播歌曲
 */
@Repository
public class PickListRedisRepository extends BaseRedisRepository {

    private static final TypeReference<Music> MUSIC_TYPE = new TypeReference<>() {};

    public PickListRedisRepository(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        super(redisTemplate, objectMapper);
    }

    // ---- 点歌队列 ----

    public void push(String houseId, Music music) {
        redisTemplate.opsForList().rightPush(RedisKeyConstants.pickList(houseId), toJson(music));
    }

    public Optional<Music> pop(String houseId) {
        String json = redisTemplate.opsForList().leftPop(RedisKeyConstants.pickList(houseId));
        return fromJson(json, MUSIC_TYPE);
    }

    public Optional<Music> peek(String houseId) {
        String json = redisTemplate.opsForList().index(RedisKeyConstants.pickList(houseId), 0);
        return fromJson(json, MUSIC_TYPE);
    }

    public List<Music> getAll(String houseId) {
        List<String> jsons = redisTemplate.opsForList().range(RedisKeyConstants.pickList(houseId), 0, -1);
        if (jsons == null) return List.of();
        return jsons.stream()
                .map(j -> fromJson(j, MUSIC_TYPE))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    public long size(String houseId) {
        Long size = redisTemplate.opsForList().size(RedisKeyConstants.pickList(houseId));
        return size != null ? size : 0L;
    }

    public boolean remove(String houseId, String musicId) {
        List<Music> list = getAll(houseId);
        boolean removed = false;
        for (Music m : list) {
            if (musicId.equals(m.getId())) {
                redisTemplate.opsForList().remove(RedisKeyConstants.pickList(houseId), 1, toJson(m));
                removed = true;
                break;
            }
        }
        return removed;
    }

    public boolean contains(String houseId, String musicId) {
        return getAll(houseId).stream().anyMatch(m -> musicId.equals(m.getId()));
    }

    public void clear(String houseId) {
        redisTemplate.delete(RedisKeyConstants.pickList(houseId));
    }

    public void replaceAll(String houseId, List<Music> musicList) {
        redisTemplate.delete(RedisKeyConstants.pickList(houseId));
        if (!musicList.isEmpty()) {
            String[] jsons = musicList.stream().map(this::toJson).toArray(String[]::new);
            redisTemplate.opsForList().rightPushAll(RedisKeyConstants.pickList(houseId), jsons);
        }
    }

    // ---- 默认播放列表 ----

    public void pushDefault(String houseId, Music music) {
        redisTemplate.opsForList().rightPush(RedisKeyConstants.defaultList(houseId), toJson(music));
    }

    public Optional<Music> popDefault(String houseId) {
        String json = redisTemplate.opsForList().leftPop(RedisKeyConstants.defaultList(houseId));
        return fromJson(json, MUSIC_TYPE);
    }

    public long defaultSize(String houseId) {
        Long size = redisTemplate.opsForList().size(RedisKeyConstants.defaultList(houseId));
        return size != null ? size : 0L;
    }

    public void clearDefault(String houseId) {
        redisTemplate.delete(RedisKeyConstants.defaultList(houseId));
    }
}
