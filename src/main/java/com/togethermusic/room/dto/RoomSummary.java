package com.togethermusic.room.dto;

/**
 * 房间列表摘要，用于公开展示
 */
public record RoomSummary(
        String id,
        String name,
        String desc,
        boolean needPwd,
        int onlineCount
) {}
