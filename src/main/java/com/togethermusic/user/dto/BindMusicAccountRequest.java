package com.togethermusic.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 绑定音乐平台账号请求
 */
@Data
public class BindMusicAccountRequest {

    /**
     * 音乐平台来源：netease, qq, kugou
     */
    @NotBlank(message = "平台来源不能为空")
    private String source;

    /**
     * 认证令牌
     */
    @NotBlank(message = "认证令牌不能为空")
    private String authToken;

    /**
     * 刷新令牌（可选）
     */
    private String refreshToken;

    /**
     * 令牌过期时间（可选）
     */
    private OffsetDateTime expiresAt;
}
