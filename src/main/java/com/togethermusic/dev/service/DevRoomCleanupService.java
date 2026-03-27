package com.togethermusic.dev.service;

import com.togethermusic.repository.RedisKeyConstants;
import com.togethermusic.repository.RoomRedisRepository;
import com.togethermusic.room.model.House;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DevRoomCleanupService {

    private final RoomRedisRepository roomRepository;
    private final RedisTemplate<String, String> redisTemplate;

    public CleanupResult clearAllRoomData() {
        var rooms = roomRepository.findAll();
        Set<String> keysToDelete = new HashSet<>();

        for (House house : rooms) {
            String houseId = house.getId();
            keysToDelete.add(RedisKeyConstants.playing(houseId));
            keysToDelete.add(RedisKeyConstants.pickList(houseId));
            keysToDelete.add(RedisKeyConstants.defaultList(houseId));
            keysToDelete.add(RedisKeyConstants.config(houseId));
            keysToDelete.add(RedisKeyConstants.session(houseId));
            keysToDelete.add(RedisKeyConstants.blackUser(houseId));
            keysToDelete.add(RedisKeyConstants.blackMusic(houseId));
            keysToDelete.add(RedisKeyConstants.vote(houseId));

            if (house.getRemoteAddress() != null && !house.getRemoteAddress().isBlank()) {
                keysToDelete.add(RedisKeyConstants.ipHouses(house.getRemoteAddress()));
            }
        }

        Set<String> ipKeys = redisTemplate.keys(RedisKeyConstants.ipHouses("*"));
        if (ipKeys != null) {
            keysToDelete.addAll(ipKeys);
        }

        long roomCount = roomRepository.count();
        redisTemplate.delete(RedisKeyConstants.HOUSES);
        if (!keysToDelete.isEmpty()) {
            redisTemplate.delete(keysToDelete);
        }

        log.warn("Dev cleanup executed: removed {} room(s), {} redis key(s)", roomCount, keysToDelete.size() + 1);
        return new CleanupResult(roomCount, keysToDelete.size() + 1);
    }

    public record CleanupResult(long roomsCleared, long redisKeysCleared) {}
}
