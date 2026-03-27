package com.togethermusic.user.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 网易云二维码登录轮询响应
 */
@Data
@Builder
public class NeteaseQrLoginCheckResponse {

    /**
     * 网易云原始状态码
     * 800: 过期
     * 801: 等待扫码
     * 802: 等待确认
     * 803: 登录成功
     */
    private int code;

    private String message;

    /**
     * 是否已完成授权并入库
     */
    private boolean authorized;

    /**
     * 昵称，仅在成功时尽量返回
     */
    private String nickname;
}
