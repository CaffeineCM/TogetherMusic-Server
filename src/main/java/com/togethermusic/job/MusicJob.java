package com.togethermusic.job;

import com.togethermusic.common.websocket.MessageBroadcaster;
import com.togethermusic.common.websocket.MessageType;
import com.togethermusic.config.TogetherMusicProperties;
import com.togethermusic.music.adapter.MusicSourceRegistry;
import com.togethermusic.music.adapter.RoomAwareAdapterRouter;
import com.togethermusic.music.dto.PlaybackSnapshot;
import com.togethermusic.music.model.Music;
import com.togethermusic.music.model.RoomConfig;
import com.togethermusic.music.service.MusicService;
import com.togethermusic.repository.ConfigRedisRepository;
import com.togethermusic.repository.PickListRedisRepository;
import com.togethermusic.repository.RoomRedisRepository;
import com.togethermusic.repository.VoteRedisRepository;
import com.togethermusic.room.model.House;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * 切歌定时任务
 * 每 5 秒检查所有活跃房间，判断是否需要切歌
 *
 * 切歌触发条件：
 * 1. pushSwitch == true（手动触发：管理员切歌 / 投票通过）
 * 2. 当前时间 - lastPushTime >= lastDuration（歌曲自然播放结束）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MusicJob {

    private final RoomRedisRepository roomRepo;
    private final PickListRedisRepository pickListRepo;
    private final ConfigRedisRepository configRepo;
    private final VoteRedisRepository voteRepo;
    private final MusicSourceRegistry registry;
    private final RoomAwareAdapterRouter adapterRouter;
    private final MusicService musicService;
    private final MessageBroadcaster broadcaster;
    private final TogetherMusicProperties properties;

    private final Random random = new Random();

    @Scheduled(fixedDelay = 5000)
    public void checkAndSwitch() {
        List<House> rooms = roomRepo.findAll();
        for (House room : rooms) {
            try {
                processRoom(room.getId());
            } catch (Exception e) {
                log.error("[{}] Error in MusicJob: {}", room.getId(), e.getMessage());
            }
        }
    }

    private void processRoom(String houseId) {
        long now = System.currentTimeMillis();

        Boolean pushSwitch = configRepo.getBoolean(houseId, RoomConfig.PUSH_SWITCH);
        Long lastPushTime = configRepo.getLong(houseId, RoomConfig.LAST_PUSH_TIME);
        Long lastDuration = configRepo.getLong(houseId, RoomConfig.LAST_DURATION);

        boolean manualSwitch = Boolean.TRUE.equals(pushSwitch);
        boolean naturalEnd = lastPushTime != null && lastDuration != null
                && (now - lastPushTime) >= lastDuration;

        if (!manualSwitch && !naturalEnd) return;

        // 重置切歌开关
        configRepo.setBoolean(houseId, RoomConfig.PUSH_SWITCH, false);

        Music next = resolveNextMusic(houseId);
        if (next == null) {
            // 无歌可播，广播空状态
            roomRepo.clearPlaying(houseId);
            configRepo.set(houseId, RoomConfig.PLAYBACK_STATUS, "idle");
            configRepo.setLong(houseId, RoomConfig.PLAYBACK_POSITION, 0L);
            configRepo.setLong(houseId, RoomConfig.PLAYBACK_UPDATED_AT, 0L);
            broadcaster.broadcastToRoom(houseId, MessageType.MUSIC, null, "暂无歌曲");
            broadcaster.broadcastToRoom(
                    houseId,
                    MessageType.PLAYBACK,
                    PlaybackSnapshot.builder()
                            .music(null)
                            .status("idle")
                            .positionMs(0L)
                            .updatedAt(0L)
                            .serverTime(System.currentTimeMillis())
                            .build(),
                    "暂无歌曲"
            );
            broadcaster.broadcastToRoom(houseId, MessageType.PICK, musicService.getPickList(houseId), "点歌列表");
            return;
        }

        // 刷新过期播放链接
        refreshUrlIfExpired(houseId, next);

        // 设置推送时间（客户端用于计算进度）
        next.setPushTime(System.currentTimeMillis());

        // 更新当前播放
        roomRepo.setPlaying(houseId, next);

        // 更新配置：记录推送时间和时长，供下次切歌判断
        configRepo.setLong(houseId, RoomConfig.LAST_PUSH_TIME, next.getPushTime());
        configRepo.setLong(houseId, RoomConfig.LAST_DURATION,
                next.getDuration() != null ? next.getDuration() : 180_000L);
        configRepo.set(houseId, RoomConfig.PLAYBACK_STATUS, "playing");
        configRepo.setLong(houseId, RoomConfig.PLAYBACK_POSITION, 0L);
        configRepo.setLong(houseId, RoomConfig.PLAYBACK_UPDATED_AT, next.getPushTime());

        // 重置投票
        voteRepo.reset(houseId);

        log.info("[{}] Now playing: {} - {}", houseId, next.getName(), next.getArtist());
        broadcaster.broadcastToRoom(houseId, MessageType.MUSIC, next, "当前播放");
        broadcaster.broadcastToRoom(
                houseId,
                MessageType.PLAYBACK,
                com.togethermusic.music.dto.PlaybackSnapshot.builder()
                        .music(next)
                        .status("playing")
                        .positionMs(0L)
                        .updatedAt(next.getPushTime())
                        .serverTime(System.currentTimeMillis())
                        .build(),
                "当前播放"
        );
        broadcaster.broadcastToRoom(houseId, MessageType.PICK, musicService.getPickList(houseId), "点歌列表");
    }

    /**
     * 根据播放模式决定下一首歌曲
     */
    private Music resolveNextMusic(String houseId) {
        Boolean musicCircle = configRepo.getBoolean(houseId, RoomConfig.MUSIC_CIRCLE);
        Boolean listCircle = configRepo.getBoolean(houseId, RoomConfig.LIST_CIRCLE);
        Boolean randomModel = configRepo.getBoolean(houseId, RoomConfig.RANDOM_MODEL);

        // 单曲循环：重新播放当前歌曲
        if (Boolean.TRUE.equals(musicCircle)) {
            Optional<Music> playing = roomRepo.getPlaying(houseId);
            if (playing.isPresent()) {
                Music m = playing.get();
                m.getLikedUserIds().clear(); // 重置点赞
                return m;
            }
        }

        long queueSize = pickListRepo.size(houseId);

        if (queueSize > 0) {
            Music next;
            if (Boolean.TRUE.equals(randomModel)) {
                // 随机播放：随机取一首
                List<Music> all = pickListRepo.getAll(houseId);
                next = all.get(random.nextInt(all.size()));
                pickListRepo.remove(houseId, next.getId());
            } else {
                // 顺序播放：取队首
                next = pickListRepo.pop(houseId).orElse(null);
            }

            // 列表循环：将当前歌曲重新加入队尾
            if (Boolean.TRUE.equals(listCircle)) {
                roomRepo.getPlaying(houseId).ifPresent(current -> {
                    current.getLikedUserIds().clear();
                    current.setTopTime(null);
                    current.setPickTime(System.currentTimeMillis());
                    pickListRepo.push(houseId, current);
                });
            }

            return next;
        }

        // 队列为空，从默认播放列表取
        if (pickListRepo.defaultSize(houseId) > 0) {
            return pickListRepo.popDefault(houseId).orElse(null);
        }

        return null;
    }

    /**
     * 播放链接过期时重新获取
     */
    private void refreshUrlIfExpired(String houseId, Music music) {
        long expireTime = properties.getMusic().getExpireTime();
        boolean missingUrl = music.getUrl() == null || music.getUrl().isBlank();
        boolean expired = music.getUrlExpireTime() != null
                ? System.currentTimeMillis() >= music.getUrlExpireTime()
                : (System.currentTimeMillis() - music.getPickTime()) >= expireTime;

        if (!missingUrl && !expired) return;

        try {
            // 使用房间感知的适配器路由刷新 URL
            var adapterContext = adapterRouter.getAdapterForRoom(houseId, music.getSource());
            Music refreshed = adapterContext.getById(music.getId(), music.getQuality());
            if (refreshed != null && refreshed.getUrl() != null) {
                music.setUrl(refreshed.getUrl());
                music.setUrlExpireTime(refreshed.getUrlExpireTime());
                if (refreshed.getLyric() != null && !refreshed.getLyric().isBlank()) {
                    music.setLyric(refreshed.getLyric());
                }
                music.setPickTime(System.currentTimeMillis());
                log.info("[{}] Refreshed URL for: {}", houseId, music.getName());
            }
        } catch (Exception e) {
            log.warn("[{}] Failed to refresh URL for {}: {}", houseId, music.getName(), e.getMessage());
        }
    }
}
