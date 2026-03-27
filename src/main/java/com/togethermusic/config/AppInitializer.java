package com.togethermusic.config;

import com.togethermusic.repository.RoomRedisRepository;
import com.togethermusic.room.model.House;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 应用启动后执行初始化逻辑
 * 从 Redis 恢复房间状态，记录当前活跃房间数
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppInitializer implements ApplicationRunner {

    private final RoomRedisRepository roomRepository;

    @Override
    public void run(ApplicationArguments args) {
        List<House> rooms = roomRepository.findAll();
        log.info("Together Music started. Restored {} room(s) from Redis.", rooms.size());
        rooms.forEach(room ->
                log.debug("  - Room: id={}, name={}", room.getId(), room.getName())
        );
    }
}
