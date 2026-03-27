package com.togethermusic.user.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 音乐平台账号信息 VO
 */
@Data
@Builder
public class MusicAccountVO {

    /**
     * 记录ID
     */
    private Long id;

    /**
     * 音乐平台来源：netease, qq, kugou
     */
    private String source;

    /**
     * 是否有效
     */
    private Boolean isActive;

    /**
     * 是否过期
     */
    private Boolean isExpired;

    /**
     * 过期时间
     */
    private OffsetDateTime expiresAt;

    /**
     * 绑定时间
     */
    private OffsetDateTime createdAt;

    /**
     * 更新时间
     */
    private OffsetDateTime updatedAt;
}
