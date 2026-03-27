package com.togethermusic.room.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;

/**
 * WebSocket 会话用户，存储于 Redis Hash
 * 使用 record 保证不可变性，更新时替换整个对象
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SessionUser(
        String sessionId,
        String houseId,
        String displayName,
        String remoteAddress,
        String role,
        Long lastMessageTime,
        Long registeredUserId
) implements Serializable {

    public static final String ROLE_DEFAULT = "default";
    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_ROOT = "root";

    public SessionUser withRole(String newRole) {
        return new SessionUser(sessionId, houseId, displayName, remoteAddress, newRole, lastMessageTime, registeredUserId);
    }

    public SessionUser withDisplayName(String newName) {
        return new SessionUser(sessionId, houseId, newName, remoteAddress, role, lastMessageTime, registeredUserId);
    }

    public SessionUser withLastMessageTime(long time) {
        return new SessionUser(sessionId, houseId, displayName, remoteAddress, role, time, registeredUserId);
    }

    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role) || ROLE_ROOT.equals(role);
    }
}
