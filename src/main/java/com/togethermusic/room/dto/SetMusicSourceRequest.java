package com.togethermusic.room.dto;

import lombok.Data;

/**
 * 设置房间音乐源请求
 */
@Data
public class SetMusicSourceRequest {

    /**
     * 房间ID
     */
    private String houseId;

    /**
     * 音乐源：wy, qq, kg
     */
    private String source;

    /**
     * 是否使用自己的账号
     */
    private Boolean useMyAccount;

    /**
     * 指定使用某个用户的账号（创建人权限）
     */
    private Long targetUserId;
}
