package com.togethermusic.music.service;

import com.togethermusic.common.code.ErrorCode;
import com.togethermusic.common.exception.BusinessException;
import com.togethermusic.common.websocket.MessageBroadcaster;
import com.togethermusic.common.websocket.MessageType;
import com.togethermusic.config.TogetherMusicProperties;
import com.togethermusic.music.adapter.RoomAwareAdapterRouter;
import com.togethermusic.music.dto.PlaybackSnapshot;
import com.togethermusic.music.dto.MusicDiscoveryContext;
import com.togethermusic.music.dto.MusicPlaylistSummary;
import com.togethermusic.music.dto.MusicToplistSummary;
import com.togethermusic.music.model.Music;
import com.togethermusic.music.model.RoomConfig;
import com.togethermusic.repository.BlackListRedisRepository;
import com.togethermusic.repository.ConfigRedisRepository;
import com.togethermusic.repository.PickListRedisRepository;
import com.togethermusic.repository.RoomRedisRepository;
import com.togethermusic.repository.SessionRedisRepository;
import com.togethermusic.repository.VoteRedisRepository;
import com.togethermusic.room.model.SessionUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class MusicService {

    private final PickListRedisRepository pickListRepo;
    private final BlackListRedisRepository blackListRepo;
    private final ConfigRedisRepository configRepo;
    private final RoomRedisRepository roomRepo;
    private final SessionRedisRepository sessionRepo;
    private final VoteRedisRepository voteRepo;
    private final RoomAwareAdapterRouter adapterRouter;
    private final MessageBroadcaster broadcaster;
    private final TogetherMusicProperties properties;

    // ---- 点歌 ----

    /**
     * 点歌：搜索 → 校验 → 加入队列 → 广播
     */
    public void pick(String houseId, String sessionId, String keyword, String source, String quality) {
        // 禁止点歌检查（管理员不受限）
        SessionUser user = sessionRepo.get(houseId, sessionId).orElseThrow();
        if (!user.isManager()) {
            Boolean searchEnabled = configRepo.getBoolean(houseId, RoomConfig.SEARCH_ENABLED);
            if (Boolean.FALSE.equals(searchEnabled)) {
                throw new BusinessException(ErrorCode.MUSIC_SEARCH_DISABLED);
            }
        }

        // 队列长度限制
        long maxSize = properties.getMusic().getPlaylistSize();
        if (pickListRepo.size(houseId) >= maxSize) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "播放列表已满，最多 " + maxSize + " 首");
        }

        // 搜索歌曲（使用房间感知的适配器路由）
        var adapterContext = adapterRouter.getAdapterForRoom(houseId, source);
        Music music = adapterContext.search(keyword, quality);
        if (music == null) {
            throw new BusinessException(ErrorCode.MUSIC_NOT_FOUND);
        }

        validateAndAdd(houseId, sessionId, music, user.displayName());
    }

    /**
     * 按 ID 点歌（前端搜索后选择具体歌曲）
     */
    public void pickById(String houseId, String sessionId, String musicId, String source, String quality) {
        SessionUser user = sessionRepo.get(houseId, sessionId).orElseThrow();
        if (!user.isManager()) {
            Boolean searchEnabled = configRepo.getBoolean(houseId, RoomConfig.SEARCH_ENABLED);
            if (Boolean.FALSE.equals(searchEnabled)) {
                throw new BusinessException(ErrorCode.MUSIC_SEARCH_DISABLED);
            }
        }

        long maxSize = properties.getMusic().getPlaylistSize();
        if (pickListRepo.size(houseId) >= maxSize) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "播放列表已满，最多 " + maxSize + " 首");
        }

        var adapterContext = adapterRouter.getAdapterForRoom(houseId, source);
        Music music = adapterContext.getById(musicId, quality);
        if (music == null) {
            if ("kg".equalsIgnoreCase(source) || "kugou".equalsIgnoreCase(source)) {
                throw new BusinessException(
                        ErrorCode.MUSIC_NOT_FOUND,
                        "该歌曲当前不可播放，可能需要会员或当前账号暂无播放权限"
                );
            }
            throw new BusinessException(ErrorCode.MUSIC_NOT_FOUND);
        }

        validateAndAdd(houseId, sessionId, music, user.displayName());
    }

    public BatchPickResult pickPlaylist(String houseId, String sessionId, String playlistId, String source) {
        SessionUser user = sessionRepo.get(houseId, sessionId).orElseThrow();
        if (!user.isManager()) {
            Boolean searchEnabled = configRepo.getBoolean(houseId, RoomConfig.SEARCH_ENABLED);
            if (Boolean.FALSE.equals(searchEnabled)) {
                throw new BusinessException(ErrorCode.MUSIC_SEARCH_DISABLED);
            }
        }

        long maxSize = properties.getMusic().getPlaylistSize();
        List<Music> currentList = pickListRepo.getAll(houseId);
        int remaining = (int) Math.max(0, maxSize - currentList.size());
        if (remaining <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "播放列表已满，最多 " + maxSize + " 首");
        }

        var adapterContext = adapterRouter.getAdapterForRoom(houseId, source);
        List<Music> songs = adapterContext.getPlaylist(playlistId);
        if (songs == null || songs.isEmpty()) {
            throw new BusinessException(ErrorCode.MUSIC_NOT_FOUND, "歌单暂无可加入歌曲");
        }

        Set<String> existingIds = new HashSet<>();
        currentList.stream().map(Music::getId).forEach(existingIds::add);

        int added = 0;
        int duplicate = 0;
        int blacklisted = 0;
        int overflow = 0;
        long now = System.currentTimeMillis();

        for (Music music : songs) {
            if (music == null || music.getId() == null || music.getId().isBlank()) {
                continue;
            }
            if (blackListRepo.isMusicBlacklisted(houseId, music.getId())) {
                blacklisted++;
                continue;
            }
            if (existingIds.contains(music.getId())) {
                duplicate++;
                continue;
            }
            if (remaining <= 0) {
                overflow++;
                continue;
            }

            music.setPickTime(now + added);
            music.setPickedBy(sessionId);
            pickListRepo.push(houseId, music);
            existingIds.add(music.getId());
            remaining--;
            added++;
        }

        if (added == 0) {
            if (duplicate > 0 && blacklisted == 0 && overflow == 0) {
                throw new BusinessException(ErrorCode.MUSIC_ALREADY_IN_LIST, "歌单歌曲已在播放列表中");
            }
            if (overflow > 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "播放列表剩余空间不足");
            }
            if (blacklisted > 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "歌单歌曲不可加入播放列表");
            }
            throw new BusinessException(ErrorCode.MUSIC_NOT_FOUND, "歌单暂无可加入歌曲");
        }

        broadcaster.broadcastToRoom(houseId, MessageType.PICK, getPickList(houseId), "点歌列表");
        return new BatchPickResult(added, duplicate, blacklisted, overflow);
    }

    private void validateAndAdd(String houseId, String sessionId, Music music, String displayName) {
        if (blackListRepo.isMusicBlacklisted(houseId, music.getId())) {
            throw new BusinessException(ErrorCode.MUSIC_BLACKLISTED);
        }
        if (pickListRepo.contains(houseId, music.getId())) {
            throw new BusinessException(ErrorCode.MUSIC_ALREADY_IN_LIST);
        }

        music.setPickTime(System.currentTimeMillis());
        music.setPickedBy(sessionId);
        pickListRepo.push(houseId, music);

        log.info("[{}] Music picked: {} - {} by {}", houseId, music.getName(), music.getArtist(), displayName);
        broadcaster.broadcastToRoom(houseId, MessageType.PICK, getPickList(houseId), "点歌列表");
        broadcaster.notifyUser(sessionId, "点歌成功：" + music.getName());
    }

    // ---- 点赞 ----

    public void like(String houseId, String sessionId, String musicId) {
        Boolean goodModel = configRepo.getBoolean(houseId, RoomConfig.GOOD_MODEL);
        if (!Boolean.TRUE.equals(goodModel)) {
            broadcaster.notifyUser(sessionId, "当前不是点赞模式");
            return;
        }

        List<Music> list = pickListRepo.getAll(houseId);
        Optional<Music> target = list.stream().filter(m -> musicId.equals(m.getId())).findFirst();
        if (target.isEmpty()) {
            broadcaster.notifyUser(sessionId, "播放列表中未找到此歌曲");
            return;
        }

        Music music = target.get();
        if (music.getLikedUserIds().contains(sessionId)) {
            broadcaster.notifyUser(sessionId, "已点赞过 " + music.getName() + "，总票数 " + music.getLikedUserIds().size());
            return;
        }

        music.getLikedUserIds().add(sessionId);
        music.setLikeTime(System.currentTimeMillis());

        // 重新排序并保存
        List<Music> sorted = sortByLikes(list);
        pickListRepo.replaceAll(houseId, sorted);
        broadcaster.broadcastToRoom(houseId, MessageType.PICK, buildPickListResponse(houseId), "点歌列表");
    }

    // ---- 删除 ----

    public void delete(String houseId, String sessionId, String musicId) {
        SessionUser user = sessionRepo.get(houseId, sessionId).orElse(null);
        boolean isAdmin = user != null && user.isManager();

        // 非管理员只能删除自己点的歌
        if (!isAdmin) {
            List<Music> list = pickListRepo.getAll(houseId);
            boolean ownedByCaller = list.stream()
                    .anyMatch(m -> musicId.equals(m.getId()) && sessionId.equals(m.getPickedBy()));
            if (!ownedByCaller) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "只能删除自己点的歌曲");
            }
        }

        if (!pickListRepo.remove(houseId, musicId)) {
            throw new BusinessException(ErrorCode.MUSIC_NOT_FOUND, "播放列表中未找到此歌曲");
        }

        broadcaster.broadcastToRoom(houseId, MessageType.PICK, buildPickListResponse(houseId), "删除后的播放列表");
        broadcaster.notifyUser(sessionId, "删除成功");
    }

    // ---- 置顶（管理员） ----

    public void top(String houseId, String musicId) {
        List<Music> list = pickListRepo.getAll(houseId);
        Optional<Music> target = list.stream().filter(m -> musicId.equals(m.getId())).findFirst();
        if (target.isEmpty()) {
            throw new BusinessException(ErrorCode.MUSIC_NOT_FOUND, "播放列表中未找到此歌曲");
        }

        Music music = target.get();
        music.setTopTime(System.currentTimeMillis());

        List<Music> reordered = new java.util.ArrayList<>(list);
        reordered.remove(music);
        reordered.add(0, music);
        pickListRepo.replaceAll(houseId, reordered);

        broadcaster.broadcastToRoom(houseId, MessageType.PICK, buildPickListResponse(houseId), "置顶后的播放列表");
    }

    // ---- 清空（管理员） ----

    public void clear(String houseId) {
        pickListRepo.clear(houseId);
        broadcaster.broadcastToRoom(houseId, MessageType.PICK, List.of(), "清空后的播放列表");
    }

    // ---- 黑名单（管理员） ----

    public void blackMusic(String houseId, String musicId) {
        long added = blackListRepo.blackMusic(houseId, musicId);
        if (added == 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "音乐已在黑名单中");
        }
        // 同时从播放列表移除
        pickListRepo.remove(houseId, musicId);
        broadcaster.broadcastToRoom(houseId, MessageType.PICK, buildPickListResponse(houseId), "拉黑后的播放列表");
    }

    public void unblackMusic(String houseId, String musicId) {
        long removed = blackListRepo.unblackMusic(houseId, musicId);
        if (removed == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "音乐不在黑名单中");
        }
    }

    // ---- 投票切歌 ----

    public void vote(String houseId, String sessionId) {
        Boolean switchEnabled = configRepo.getBoolean(houseId, RoomConfig.SWITCH_ENABLED);
        SessionUser user = sessionRepo.get(houseId, sessionId).orElse(null);
        boolean isAdmin = user != null && user.isManager();

        if (!isAdmin && Boolean.FALSE.equals(switchEnabled)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前禁止切歌");
        }

        if (isAdmin) {
            // 管理员直接触发切歌
            configRepo.setBoolean(houseId, RoomConfig.PUSH_SWITCH, true);
            broadcaster.notifyRoom(houseId, "管理员切歌");
            return;
        }

        long added = voteRepo.vote(houseId, sessionId);
        if (added == 0) {
            long count = voteRepo.count(houseId);
            long total = sessionRepo.count(houseId);
            broadcaster.notifyUser(sessionId, "你已投过票，当前 " + count + "/" + total);
            return;
        }

        long voteCount = voteRepo.count(houseId);
        long onlineCount = sessionRepo.count(houseId);
        float voteRate = Optional.ofNullable(configRepo.getFloat(houseId, RoomConfig.VOTE_RATE))
                .orElse(properties.getMusic().getVoteRate());

        broadcaster.notifyRoom(houseId, voteCount + "/" + onlineCount + " 投票切歌");

        if (voteCount >= onlineCount * voteRate) {
            configRepo.setBoolean(houseId, RoomConfig.PUSH_SWITCH, true);
            voteRepo.reset(houseId);
            log.info("[{}] Vote passed: {}/{}", houseId, voteCount, onlineCount);
        } else if (voteCount == 1) {
            broadcaster.notifyRoom(houseId, "有人希望切歌，如果支持请发起投票切歌");
        }
    }

    // ---- 播放模式（管理员） ----

    public void setGoodModel(String houseId, boolean enabled) {
        configRepo.setBoolean(houseId, RoomConfig.GOOD_MODEL, enabled);
        broadcaster.broadcastToRoom(houseId, MessageType.GOOD_MODEL,
                enabled ? "GOOD" : "EXITGOOD", enabled ? "进入点赞模式" : "退出点赞模式");
    }

    public void setMusicCircle(String houseId, boolean enabled) {
        configRepo.setBoolean(houseId, RoomConfig.MUSIC_CIRCLE, enabled);
        if (enabled) configRepo.setBoolean(houseId, RoomConfig.LIST_CIRCLE, false);
        broadcaster.notifyRoom(houseId, enabled ? "进入单曲循环模式" : "退出单曲循环模式");
    }

    public void setListCircle(String houseId, boolean enabled) {
        configRepo.setBoolean(houseId, RoomConfig.LIST_CIRCLE, enabled);
        if (enabled) configRepo.setBoolean(houseId, RoomConfig.MUSIC_CIRCLE, false);
        broadcaster.notifyRoom(houseId, enabled ? "进入列表循环模式" : "退出列表循环模式");
    }

    public void setRandomModel(String houseId, boolean enabled) {
        configRepo.setBoolean(houseId, RoomConfig.RANDOM_MODEL, enabled);
        broadcaster.notifyRoom(houseId, enabled ? "进入随机播放模式" : "退出随机播放模式");
    }

    public void setSearchEnabled(String houseId, boolean enabled) {
        configRepo.setBoolean(houseId, RoomConfig.SEARCH_ENABLED, enabled);
        broadcaster.notifyRoom(houseId, enabled ? "已启用点歌" : "已禁止点歌");
    }

    public void setSwitchEnabled(String houseId, boolean enabled) {
        configRepo.setBoolean(houseId, RoomConfig.SWITCH_ENABLED, enabled);
        broadcaster.notifyRoom(houseId, enabled ? "已启用切歌" : "已禁止切歌");
    }

    public void setVolume(String houseId, int volume) {
        configRepo.set(houseId, RoomConfig.VOLUME, String.valueOf(volume));
        broadcaster.broadcastToRoom(houseId, MessageType.VOLUME, volume, "音量已调整为 " + volume);
    }

    public PlaybackSnapshot getPlaybackSnapshot(String houseId) {
        var music = roomRepo.getPlaying(houseId).orElse(null);
        if (music == null) {
            return PlaybackSnapshot.builder()
                    .music(null)
                    .status("idle")
                    .positionMs(0L)
                    .updatedAt(0L)
                    .serverTime(System.currentTimeMillis())
                    .build();
        }

        String status = configRepo.get(houseId, RoomConfig.PLAYBACK_STATUS)
                .filter(value -> !value.isBlank())
                .orElse("playing");
        long storedPosition = Optional.ofNullable(configRepo.getLong(houseId, RoomConfig.PLAYBACK_POSITION)).orElse(0L);
        long updatedAt = Optional.ofNullable(configRepo.getLong(houseId, RoomConfig.PLAYBACK_UPDATED_AT))
                .orElse(Optional.ofNullable(music.getPushTime()).orElse(System.currentTimeMillis()));
        long now = System.currentTimeMillis();

        return PlaybackSnapshot.builder()
                .music(music)
                .status(status)
                .positionMs(clampPosition(music, storedPosition))
                .updatedAt(updatedAt)
                .serverTime(now)
                .build();
    }

    public void pausePlayback(String houseId) {
        var snapshot = getPlaybackSnapshot(houseId);
        if (snapshot.music() == null || "paused".equals(snapshot.status())) {
            return;
        }

        long now = System.currentTimeMillis();
        long positionMs = resolvePosition(
                snapshot.music(),
                snapshot.status(),
                snapshot.positionMs(),
                snapshot.updatedAt(),
                now
        );
        configRepo.set(houseId, RoomConfig.PLAYBACK_STATUS, "paused");
        configRepo.setLong(houseId, RoomConfig.PLAYBACK_POSITION, positionMs);
        configRepo.setLong(houseId, RoomConfig.PLAYBACK_UPDATED_AT, now);
        broadcastPlaybackSnapshot(houseId, "已暂停播放");
    }

    public void resumePlayback(String houseId) {
        var snapshot = getPlaybackSnapshot(houseId);
        if (snapshot.music() == null) {
            return;
        }

        long now = System.currentTimeMillis();
        configRepo.set(houseId, RoomConfig.PLAYBACK_STATUS, "playing");
        configRepo.setLong(houseId, RoomConfig.PLAYBACK_POSITION, snapshot.positionMs());
        configRepo.setLong(houseId, RoomConfig.PLAYBACK_UPDATED_AT, now);
        broadcastPlaybackSnapshot(houseId, "继续播放");
    }

    public void seekPlayback(String houseId, long positionMs) {
        var snapshot = getPlaybackSnapshot(houseId);
        if (snapshot.music() == null) {
            return;
        }

        long now = System.currentTimeMillis();
        long clampedPosition = clampPosition(snapshot.music(), positionMs);
        configRepo.setLong(houseId, RoomConfig.PLAYBACK_POSITION, clampedPosition);
        configRepo.setLong(houseId, RoomConfig.PLAYBACK_UPDATED_AT, now);
        configRepo.set(houseId, RoomConfig.PLAYBACK_STATUS, snapshot.status());
        broadcastPlaybackSnapshot(houseId, "同步播放进度");
    }

    public void setVoteRate(String houseId, float rate) {
        configRepo.setFloat(houseId, RoomConfig.VOTE_RATE, rate);
        broadcaster.notifyRoom(houseId, "投票率已修改为 " + rate);
    }

    // ---- 默认播放列表（管理员） ----

    public int addDefaultPlaylist(String houseId, String playlistId, String source) {
        var adapterContext = adapterRouter.getAdapterForRoom(houseId, source);
        List<Music> songs = adapterContext.getPlaylist(playlistId);
        songs.forEach(m -> pickListRepo.pushDefault(houseId, m));
        return songs.size();
    }

    public void clearDefaultPlaylist(String houseId) {
        pickListRepo.clearDefault(houseId);
        broadcaster.notifyRoom(houseId, "默认播放列表已清空");
    }

    // ---- 查询 ----

    public List<Music> getPickList(String houseId) {
        return buildPickListResponse(houseId);
    }

    public List<Music> searchCandidates(String houseId, String keyword, String source, String quality) {
        var adapterContext = adapterRouter.getAdapterForRoom(houseId, source);
        return adapterContext.searchSongs(keyword, quality).stream()
                .limit(30)
                .toList();
    }

    public MusicDiscoveryContext getDiscoveryContext(String houseId, Long currentUserId, String source) {
        boolean canViewHostPlaylists = supportsUserPlaylists(source)
                && adapterRouter.getTokenHolderInfo(houseId)
                .map(info -> currentUserId != null && info.isTokenHolder(currentUserId))
                .orElse(false);
        return MusicDiscoveryContext.builder()
                .canViewHostPlaylists(canViewHostPlaylists)
                .playlistSource(source)
                .build();
    }

    public List<MusicPlaylistSummary> getRecommendedPlaylists(String houseId, String source) {
        return adapterRouter.getAdapterForRoom(houseId, source).getRecommendedPlaylists();
    }

    public List<MusicPlaylistSummary> getHostPlaylists(String houseId, Long currentUserId, String source) {
        if (!supportsUserPlaylists(source)) {
            return List.of();
        }

        var holderInfo = adapterRouter.getTokenHolderInfo(houseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "当前房间未配置房主音乐账号"));

        if (currentUserId == null || !holderInfo.isTokenHolder(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有当前房间的 Token 持有者可以查看收藏歌单");
        }

        var adapterContext = adapterRouter.getAdapterForRoom(houseId, source);
        return adapterContext.getUserPlaylists();
    }

    public List<Music> getPlaylistDetail(String houseId, String playlistId, String source) {
        return adapterRouter.getAdapterForRoom(houseId, source).getPlaylist(playlistId);
    }

    public List<MusicToplistSummary> getToplists(String houseId, String source) {
        return adapterRouter.getAdapterForRoom(houseId, source).getToplists();
    }

    public String getBlackMusicList(String houseId) {
        return String.join(", ", blackListRepo.getBlacklistedMusic(houseId));
    }

    public void resetPlaybackState(String houseId) {
        configRepo.set(houseId, RoomConfig.PLAYBACK_STATUS, "idle");
        configRepo.setLong(houseId, RoomConfig.PLAYBACK_POSITION, 0L);
        configRepo.setLong(houseId, RoomConfig.PLAYBACK_UPDATED_AT, 0L);
    }

    public void broadcastPlaybackSnapshot(String houseId, String message) {
        broadcaster.broadcastToRoom(houseId, MessageType.PLAYBACK, getPlaybackSnapshot(houseId), message);
    }

    // ---- 内部工具 ----

    private List<Music> buildPickListResponse(String houseId) {
        return pickListRepo.getAll(houseId).stream()
                .map(m -> {
                    // 列表不传输歌词（数据量大）
                    Music copy = copyWithoutLyric(m);
                    return copy;
                })
                .toList();
    }

    private Music copyWithoutLyric(Music m) {
        return Music.builder()
                .id(m.getId()).name(m.getName()).artist(m.getArtist())
                .duration(m.getDuration()).url(m.getUrl())
                .pictureUrl(m.getPictureUrl()).source(m.getSource())
                .quality(m.getQuality()).pickTime(m.getPickTime())
                .pushTime(m.getPushTime()).likedUserIds(m.getLikedUserIds())
                .likeTime(m.getLikeTime()).topTime(m.getTopTime())
                .pickedBy(m.getPickedBy())
                .build();
    }

    private List<Music> sortByLikes(List<Music> list) {
        return list.stream()
                .sorted(Comparator
                        .comparingLong((Music m) -> m.getTopTime() != null ? m.getTopTime() : 0L).reversed()
                        .thenComparingInt((Music m) -> m.getLikedUserIds().size()).reversed()
                        .thenComparingLong((Music m) -> m.getLikeTime() != null ? m.getLikeTime() : 0L).reversed()
                        .thenComparingLong(Music::getPickTime))
                .toList();
    }

    private boolean supportsUserPlaylists(String source) {
        return "wy".equalsIgnoreCase(source);
    }

    private long resolvePosition(Music music, String status, long storedPosition, long updatedAt, long now) {
        long base = storedPosition;
        if ("playing".equals(status) && updatedAt > 0) {
            base += Math.max(0, now - updatedAt);
        }
        return clampPosition(music, base);
    }

    private long clampPosition(Music music, long positionMs) {
        long duration = Optional.ofNullable(music.getDuration()).orElse(0L);
        if (duration <= 0) {
            return Math.max(0L, positionMs);
        }
        return Math.max(0L, Math.min(positionMs, duration));
    }

    public record BatchPickResult(int added, int duplicate, int blacklisted, int overflow) {}
}
