package com.togethermusic.room.dto;

import lombok.Data;

/**
 * 转移 Token 持有者权限请求
 */
@Data
public class TransferTokenRequest {

    /**
     * 房间ID
     */
    private String houseId;

    /**
     * 目标用户ID（新的 Token 持有者）
     */
    private Long targetUserId;
}
