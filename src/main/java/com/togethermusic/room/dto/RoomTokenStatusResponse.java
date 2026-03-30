package com.togethermusic.room.dto;

/**
 * 房间 Token 授权状态响应
 */
public record RoomTokenStatusResponse(
        String houseId,
        String defaultMusicSource,
        Long creatorUserId,
        String creatorDisplayName,
        boolean creatorHasAuthorized,
        Long tokenHolderUserId,
        String tokenHolderDisplayName,
        boolean adminCanAuthorize
) {
}
