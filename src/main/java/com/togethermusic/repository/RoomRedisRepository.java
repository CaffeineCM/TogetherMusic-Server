package com.togethermusic.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.togethermusic.music.model.Music;
import com.togethermusic.room.model.House;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 房间 Repository
 * tm:houses — Hash，存所有房间（houseId -> House JSON）
 * tm:playing:{houseId} — String，存当前播放歌曲 JSON
 * tm:ip:house:{ip} — Set，存该 IP 创建的 houseId
 */
@Repository
public class RoomRedisRepository extends BaseRedisRepository {

    private static final TypeReference<House> HOUSE_TYPE = new TypeReference<>() {};
    private static final TypeReference<Music> MUSIC_TYPE = new TypeReference<>() {};

    public RoomRedisRepository(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        super(redisTemplate, objectMapper);
    }

    // ---- 房间管理 ----

    public void save(House house) {
        redisTemplate.opsForHash().put(RedisKeyConstants.HOUSES, house.getId(), toJson(house));
    }

    public Optional<House> findById(String houseId) {
        Object value = redisTemplate.opsForHash().get(RedisKeyConstants.HOUSES, houseId);
        return fromJson(value != null ? value.toString() : null, HOUSE_TYPE);
    }

    public List<House> findAll() {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(RedisKeyConstants.HOUSES);
        return entries.values().stream()
                .map(v -> fromJson(v.toString(), HOUSE_TYPE))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    public void delete(String houseId) {
        redisTemplate.opsForHash().delete(RedisKeyConstants.HOUSES, houseId);
    }

    public boolean exists(String houseId) {
        return Boolean.TRUE.equals(redisTemplate.opsForHash().hasKey(RedisKeyConstants.HOUSES, houseId));
    }

    public long count() {
        return redisTemplate.opsForHash().size(RedisKeyConstants.HOUSES);
    }

    // ---- 当前播放歌曲 ----

    public void setPlaying(String houseId, Music music) {
        redisTemplate.opsForValue().set(RedisKeyConstants.playing(houseId), toJson(music));
    }

    public Optional<Music> getPlaying(String houseId) {
        String json = redisTemplate.opsForValue().get(RedisKeyConstants.playing(houseId));
        return fromJson(json, MUSIC_TYPE);
    }

    public void clearPlaying(String houseId) {
        redisTemplate.delete(RedisKeyConstants.playing(houseId));
    }

    // ---- IP 房间数限制 ----

    public void addIpHouse(String ip, String houseId) {
        redisTemplate.opsForSet().add(RedisKeyConstants.ipHouses(ip), houseId);
    }

    public void removeIpHouse(String ip, String houseId) {
        redisTemplate.opsForSet().remove(RedisKeyConstants.ipHouses(ip), houseId);
    }

    public long countIpHouses(String ip) {
        Long size = redisTemplate.opsForSet().size(RedisKeyConstants.ipHouses(ip));
        return size != null ? size : 0L;
    }

    public Set<String> getIpHouses(String ip) {
        Set<String> members = redisTemplate.opsForSet().members(RedisKeyConstants.ipHouses(ip));
        return members != null ? members : Set.of();
    }
}
