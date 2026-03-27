package com.togethermusic.common.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 通用
    SUCCESS(200, "成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或 Token 无效"),
    FORBIDDEN(403, "权限不足"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "资源已存在"),
    INTERNAL_ERROR(500, "服务器内部错误"),
    SERVICE_UNAVAILABLE(503, "服务不可用"),

    // 用户相关 1xxx
    USER_NOT_FOUND(1001, "用户不存在"),
    USER_ALREADY_EXISTS(1002, "用户名或邮箱已存在"),
    PASSWORD_TOO_SHORT(1003, "密码长度不能少于8位"),
    WRONG_CREDENTIALS(1004, "用户名或密码错误"),

    // 房间相关 2xxx
    ROOM_NOT_FOUND(2001, "房间不存在"),
    ROOM_FULL(2002, "房间已满"),
    ROOM_WRONG_PASSWORD(2003, "房间密码错误"),
    ROOM_LIMIT_EXCEEDED(2004, "超出创建房间数量限制"),

    // 音乐相关 3xxx
    MUSIC_NOT_FOUND(3001, "音乐不存在或无法播放"),
    MUSIC_ALREADY_IN_LIST(3002, "音乐已在播放列表中"),
    MUSIC_BLACKLISTED(3003, "音乐已被拉黑"),
    MUSIC_SEARCH_DISABLED(3004, "当前禁止点歌"),

    // 文件相关 4xxx
    FILE_FORMAT_NOT_SUPPORTED(4001, "不支持的文件格式"),
    FILE_TOO_LARGE(4002, "文件大小超出限制"),
    FILE_NOT_FOUND(4003, "文件不存在");

    private final int code;
    private final String message;
}
