package com.togethermusic.user.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 网易云账号状态响应
 */
@Data
@Builder
public class NeteaseAccountStatusResponse {
    private boolean valid;
    private boolean refreshed;
    private String nickname;
    private String message;
}
