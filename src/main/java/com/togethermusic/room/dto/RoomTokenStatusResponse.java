package com.togethermusic.room.dto;

import java.util.Map;

/**
 * 房间 Token 授权状态响应（按音乐源独立返回）
 */
public record RoomTokenStatusResponse(
        String houseId,
        String defaultMusicSource,
        Long creatorUserId,
        String creatorDisplayName,
        Map<String, SourceAuthStatus> sources
) {
    /**
     * 单个音乐源的授权状态
     */
    public record SourceAuthStatus(
            /** 房主是否已为此源授权 */
            boolean creatorHasAuthorized,
            /** 当前此源的 Token 持有者 userId */
            Long tokenHolderUserId,
            /** 当前 Token 持有者显示名 */
            String tokenHolderDisplayName,
            /** 管理员是否可为此源授权（房主未授权时为 true） */
            boolean adminCanAuthorize
    ) {
    }
}
