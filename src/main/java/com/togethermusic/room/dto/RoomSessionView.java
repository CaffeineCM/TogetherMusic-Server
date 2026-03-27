package com.togethermusic.room.dto;

import com.togethermusic.room.model.House;
import com.togethermusic.room.model.SessionUser;

/**
 * 房间详情 + 当前会话在该房间内的角色信息。
 */
public record RoomSessionView(
        String id,
        String name,
        String desc,
        boolean needPwd,
        int onlineCount,
        String creatorSessionId,
        Long creatorUserId,
        String currentSessionId,
        String currentUserRole
) {

    public static RoomSessionView from(House house, int onlineCount, String currentSessionId, String currentUserRole) {
        return new RoomSessionView(
                house.getId(),
                house.getName(),
                house.getDesc(),
                Boolean.TRUE.equals(house.getNeedPwd()),
                onlineCount,
                house.getCreatorSessionId(),
                house.getCreatorUserId(),
                currentSessionId,
                currentUserRole != null ? currentUserRole : SessionUser.ROLE_MEMBER
        );
    }
}
