package com.togethermusic.common.websocket;

/**
 * WebSocket 消息类型，对应 Response.type 字段
 * 客户端根据此字段路由处理逻辑
 */
public enum MessageType {
    /** 当前播放歌曲信息 */
    MUSIC,
    /** 更新后的点歌队列 */
    PICK,
    /** 聊天消息 */
    CHAT,
    /** 系统通知（点歌结果、错误提示等） */
    NOTICE,
    /** 在线人数/用户列表变更 */
    ONLINE,
    /** 房间公告 */
    ANNOUNCEMENT,
    /** 点赞模式开关状态 */
    GOOD_MODEL,
    /** 音量变更 */
    VOLUME,
    /** 播放状态快照（播放/暂停/进度） */
    PLAYBACK,
    /** 创建房间结果 */
    ADD_HOUSE,
    /** 进入房间结果 */
    ENTER_HOUSE,
    /** 房间列表 */
    SEARCH_HOUSE,
    /** 管理员鉴权结果 */
    AUTH_ADMIN,
    /** 用户黑名单 */
    BLACKLIST,
    /** 被踢出通知 */
    KICK,
    /** 房间 Token 授权状态 */
    TOKEN_STATUS
}
