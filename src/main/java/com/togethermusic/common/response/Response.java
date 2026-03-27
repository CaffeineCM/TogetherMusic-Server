package com.togethermusic.common.response;

import com.togethermusic.common.code.ErrorCode;

/**
 * 统一响应体
 *
 * @param code    状态码（200 成功，其他为错误码）
 * @param message 提示信息
 * @param data    响应数据
 * @param type    WebSocket 消息路由类型（REST 接口为 null）
 */
public record Response<T>(int code, String message, T data, String type) {

    public static <T> Response<T> success(T data) {
        return new Response<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data, null);
    }

    public static <T> Response<T> success(T data, String message) {
        return new Response<>(ErrorCode.SUCCESS.getCode(), message, data, null);
    }

    public static <T> Response<T> success(T data, String message, String type) {
        return new Response<>(ErrorCode.SUCCESS.getCode(), message, data, type);
    }

    public static <T> Response<T> failure(String message) {
        return new Response<>(ErrorCode.INTERNAL_ERROR.getCode(), message, null, null);
    }

    public static <T> Response<T> failure(int code, String message) {
        return new Response<>(code, message, null, null);
    }

    public static <T> Response<T> failure(ErrorCode errorCode) {
        return new Response<>(errorCode.getCode(), errorCode.getMessage(), null, null);
    }
}
