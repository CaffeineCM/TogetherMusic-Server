package com.togethermusic.chat.model;

/**
 * 聊天消息，广播给房间内所有用户
 */
public record ChatMessage(
        String sessionId,
        String displayName,
        String content,
        long sendTime
) {}
