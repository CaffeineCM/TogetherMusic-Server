package com.togethermusic.room.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 房间模型，存储于 Redis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class House implements Serializable {

    /** 房间唯一 ID */
    private String id;

    /** 房间名称 */
    private String name;

    /** 房间描述 */
    private String desc;

    /** 创建者 WebSocket sessionId */
    private String creatorSessionId;

    /** 创建者 IP */
    private String remoteAddress;

    /** 创建时间戳（毫秒） */
    private Long createTime;

    /** 房间密码（明文，仅用于加入验证；null 表示无密码） */
    private String password;

    /** 是否需要密码 */
    @Builder.Default
    private Boolean needPwd = false;

    /** 空房间时是否自动销毁 */
    @Builder.Default
    private Boolean canDestroy = true;

    /** 是否为永久房间（永久房间不受 playlistSize 限制） */
    @Builder.Default
    private Boolean enableStatus = false;

    /** 房间公告内容 */
    private String announce;

    /** 管理员密码 */
    private String adminPwd;

    /** 创建人用户ID（拥有最高权限，不可转移） */
    private Long creatorUserId;

    /**
     * 各音乐源的 Token 持有者用户ID（source -> userId）
     * key: 适配器 sourceCode（wy / qq / kg）
     * value: 持有该源授权的用户ID
     */
    @Builder.Default
    private Map<String, Long> tokenHolderUserIds = new HashMap<>();

    /** 默认音乐源: wy, qq, kg */
    private String defaultMusicSource;

    /** 获取指定源的 Token 持有者，null 表示该源未授权 */
    public Long getTokenHolderUserId(String source) {
        if (tokenHolderUserIds == null || source == null) return null;
        return tokenHolderUserIds.get(source);
    }

    /** 设置指定源的 Token 持有者 */
    public void setTokenHolderUserId(String source, Long userId) {
        if (tokenHolderUserIds == null) tokenHolderUserIds = new HashMap<>();
        if (userId == null) {
            tokenHolderUserIds.remove(source);
        } else {
            tokenHolderUserIds.put(source, userId);
        }
    }

    /** 判断房主是否对指定源已授权 */
    public boolean creatorHasAuthorized(String source) {
        if (creatorUserId == null) return false;
        return creatorUserId.equals(getTokenHolderUserId(source));
    }
}
