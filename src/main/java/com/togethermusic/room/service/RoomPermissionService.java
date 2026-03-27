package com.togethermusic.room.service;

import com.togethermusic.room.model.House;
import com.togethermusic.room.model.SessionUser;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 房间权限判定服务。
 * 角色与游客/注册用户身份分开处理：游客也可以是房主或管理员。
 */
@Service
public class RoomPermissionService {

    public String resolveRole(House house, String sessionId, String remoteAddress, Long registeredUserId) {
        if (house == null) {
            return SessionUser.ROLE_MEMBER;
        }
        if (registeredUserId != null && Objects.equals(house.getCreatorUserId(), registeredUserId)) {
            return SessionUser.ROLE_OWNER;
        }
        if (house.getCreatorUserId() == null) {
            if (Objects.equals(house.getCreatorSessionId(), sessionId)) {
                return SessionUser.ROLE_OWNER;
            }
            // 游客房主在重连后 sessionId 可能变化，这里退化为按创建 IP 识别。
            if (remoteAddress != null && Objects.equals(house.getRemoteAddress(), remoteAddress)) {
                return SessionUser.ROLE_OWNER;
            }
        }
        return SessionUser.ROLE_MEMBER;
    }
}
