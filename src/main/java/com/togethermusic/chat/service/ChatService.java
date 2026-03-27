package com.togethermusic.chat.service;

import com.togethermusic.chat.model.ChatMessage;
import com.togethermusic.common.websocket.MessageBroadcaster;
import com.togethermusic.common.websocket.MessageType;
import com.togethermusic.repository.SessionRedisRepository;
import com.togethermusic.room.model.SessionUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    /** 消息限流间隔（毫秒），同一用户两条消息之间的最小间隔 */
    private static final long RATE_LIMIT_MS = 1000L;

    private final SessionRedisRepository sessionRepo;
    private final MessageBroadcaster broadcaster;

    /**
     * 处理并广播聊天消息
     */
    public void send(String houseId, String sessionId, String content) {
        if (content == null || content.isBlank()) return;

        SessionUser user = sessionRepo.get(houseId, sessionId).orElse(null);
        if (user == null) return;

        // 限流检查
        long now = System.currentTimeMillis();
        if (user.lastMessageTime() != null && (now - user.lastMessageTime()) < RATE_LIMIT_MS) {
            broadcaster.notifyUser(sessionId, "发送太频繁，请稍后再试");
            return;
        }

        // 更新最后发言时间
        sessionRepo.put(houseId, user.withLastMessageTime(now));

        ChatMessage message = new ChatMessage(sessionId, user.displayName(), content, now);
        broadcaster.broadcastToRoom(houseId, MessageType.CHAT, message);
        log.debug("[{}] Chat from {}: {}", houseId, user.displayName(), content);
    }

    /**
     * 用户进入房间时推送公告
     */
    public void sendAnnouncement(String houseId, String sessionId, String announce) {
        if (announce == null || announce.isBlank()) return;
        broadcaster.sendToUser(sessionId, MessageType.ANNOUNCEMENT, announce, "房间公告");
    }

    /**
     * 设置昵称（聊天指令：设置昵称 xxx）
     */
    public void setNickname(String houseId, String sessionId, String nickname) {
        if (nickname == null || nickname.isBlank()) {
            broadcaster.notifyUser(sessionId, "昵称不能为空");
            return;
        }
        String trimmed = nickname.trim();
        if (trimmed.length() > 20) {
            broadcaster.notifyUser(sessionId, "昵称不能超过 20 个字符");
            return;
        }

        sessionRepo.get(houseId, sessionId).ifPresent(user -> {
            sessionRepo.put(houseId, user.withDisplayName(trimmed));
            broadcaster.notifyUser(sessionId, "昵称已设置为：" + trimmed);
        });
    }
}
