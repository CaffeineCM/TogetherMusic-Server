package com.togethermusic.room.dto;

import lombok.Data;

/**
 * 取消音乐源授权请求
 */
@Data
public class UnlinkTokenRequest {

    /**
     * 要取消授权的音乐源：wy, qq, kg
     */
    private String source;
}
