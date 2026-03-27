package com.togethermusic.user.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 网易云二维码登录初始化响应
 */
@Data
@Builder
public class NeteaseQrLoginStartResponse {

    /**
     * 二维码 key，前端轮询时使用
     */
    private String key;

    /**
     * 二维码跳转地址
     */
    private String qrUrl;

    /**
     * Base64 二维码图片
     */
    private String qrImage;
}
