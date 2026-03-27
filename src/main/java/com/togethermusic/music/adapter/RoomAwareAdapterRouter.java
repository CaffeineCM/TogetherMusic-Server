package com.togethermusic.music.adapter;

import com.togethermusic.music.model.Music;
import com.togethermusic.repository.RoomRedisRepository;
import com.togethermusic.room.model.House;
import com.togethermusic.user.entity.UserMusicAccount;
import com.togethermusic.user.repository.UserMusicAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 房间感知的适配器路由器
 * 根据房间的 tokenHolderUserId 获取对应用户的音乐平台 Token，
 * 并路由到相应的适配器实例
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoomAwareAdapterRouter {

    private final MusicSourceRegistry registry;
    private final UserMusicAccountRepository accountRepository;
    private final RoomRedisRepository roomRepository;

    /**
     * 获取房间指定音乐源的适配器上下文（包含用户 Token）
     *
     * @param houseId 房间ID
     * @param source  音乐源代码：wy, qq, kg, upload
     * @return 适配器上下文，包含适配器实例和用户 Token（如果有）
     */
    public AdapterContext getAdapterForRoom(String houseId, String source) {
        House house = roomRepository.findById(houseId).orElse(null);
        if (house == null) {
            log.warn("[Router] House not found: {}", houseId);
            return AdapterContext.of(registry.get(source), null);
        }

        Long tokenHolderId = house.getTokenHolderUserId();
        if (tokenHolderId == null) {
            log.debug("[Router] House {} has no token holder, using system default", houseId);
            return AdapterContext.of(registry.get(source), null);
        }

        // 查询用户绑定的 Token
        Optional<UserMusicAccount> accountOpt = accountRepository
                .findByUserIdAndSource(tokenHolderId, mapSourceCode(source));

        if (accountOpt.isEmpty()) {
            log.warn("[Router] Token holder {} has no {} account bound, using system default",
                    tokenHolderId, source);
            return AdapterContext.of(registry.get(source), null);
        }

        UserMusicAccount account = accountOpt.get();
        if (!account.isValid()) {
            log.warn("[Router] Account for user {} source {} is invalid or expired, using system default",
                    tokenHolderId, source);
            return AdapterContext.of(registry.get(source), null);
        }

        log.debug("[Router] Using user {}'s {} token for house {}",
                tokenHolderId, source, houseId);
        return AdapterContext.of(registry.get(source), account.getAuthToken());
    }

    /**
     * 获取系统默认适配器（不带用户 Token）
     */
    public MusicSourceAdapter getSystemAdapter(String source) {
        return registry.get(source);
    }

    /**
     * 检查房间是否配置了指定音乐源的用户 Token
     */
    public boolean hasUserToken(String houseId, String source) {
        House house = roomRepository.findById(houseId).orElse(null);
        if (house == null || house.getTokenHolderUserId() == null) {
            return false;
        }

        return accountRepository
                .findByUserIdAndSource(house.getTokenHolderUserId(), mapSourceCode(source))
                .map(UserMusicAccount::isValid)
                .orElse(false);
    }

    /**
     * 获取房间的 Token 持有者信息
     */
    public Optional<TokenHolderInfo> getTokenHolderInfo(String houseId) {
        House house = roomRepository.findById(houseId).orElse(null);
        if (house == null || house.getTokenHolderUserId() == null) {
            return Optional.empty();
        }

        return Optional.of(new TokenHolderInfo(
                house.getTokenHolderUserId(),
                house.getCreatorUserId()
        ));
    }

    /**
     * 将适配器 sourceCode 映射到数据库存储的 source 值
     */
    private String mapSourceCode(String adapterSourceCode) {
        return switch (adapterSourceCode) {
            case "wy" -> "netease";
            case "qq" -> "qq";
            case "kg" -> "kugou";
            default -> adapterSourceCode;
        };
    }

    /**
     * 适配器上下文，包含适配器实例和用户 Token
     */
    public record AdapterContext(MusicSourceAdapter adapter, String userToken) {
        public static AdapterContext of(MusicSourceAdapter adapter, String userToken) {
            return new AdapterContext(adapter, userToken);
        }

        /**
         * 使用上下文中的 Token 执行搜索
         */
        public Music search(String keyword, String quality) {
            return adapter.search(keyword, quality, userToken);
        }

        /**
         * 使用上下文中的 Token 获取歌曲详情
         */
        public Music getById(String id, String quality) {
            return adapter.getById(id, quality, userToken);
        }

        public java.util.List<Music> searchSongs(String keyword, String quality) {
            return adapter.searchSongs(keyword, quality, userToken);
        }

        /**
         * 使用上下文中的 Token 获取歌单
         */
        public java.util.List<Music> getPlaylist(String playlistId) {
            return adapter.getPlaylist(playlistId, userToken);
        }

        public java.util.List<com.togethermusic.music.dto.MusicPlaylistSummary> getRecommendedPlaylists() {
            return adapter.getRecommendedPlaylists(userToken);
        }

        public java.util.List<com.togethermusic.music.dto.MusicPlaylistSummary> getUserPlaylists() {
            return adapter.getUserPlaylists(userToken);
        }

        public java.util.List<com.togethermusic.music.dto.MusicToplistSummary> getToplists() {
            return adapter.getToplists(userToken);
        }
    }

    /**
     * Token 持有者信息
     */
    public record TokenHolderInfo(Long tokenHolderUserId, Long creatorUserId) {
        /**
         * 检查指定用户是否是 Token 持有者
         */
        public boolean isTokenHolder(Long userId) {
            return tokenHolderUserId.equals(userId);
        }

        /**
         * 检查指定用户是否是创建人
         */
        public boolean isCreator(Long userId) {
            return creatorUserId != null && creatorUserId.equals(userId);
        }
    }
}
