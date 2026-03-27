package com.togethermusic.common.websocket;

import com.togethermusic.common.response.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * WebSocket 消息广播工具
 * 封装 SimpMessagingTemplate，统一消息格式和目标地址
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 向房间内所有用户广播消息
     */
    public void broadcastToRoom(String houseId, MessageType type, Object data) {
        broadcastToRoom(houseId, type, data, null);
    }

    public void broadcastToRoom(String houseId, MessageType type, Object data, String message) {
        try {
            Response<Object> response = Response.success(data, message != null ? message : "", type.name());
            messagingTemplate.convertAndSend("/topic/" + houseId, response);
        } catch (Exception e) {
            log.error("Failed to broadcast to room {}: {}", houseId, e.getMessage());
        }
    }

    /**
     * 向指定用户单播消息
     */
    public void sendToUser(String sessionId, MessageType type, Object data) {
        sendToUser(sessionId, type, data, null);
    }

    public void sendToUser(String sessionId, MessageType type, Object data, String message) {
        try {
            Response<Object> response = Response.success(data, message != null ? message : "", type.name());
            SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
            headerAccessor.setSessionId(sessionId);
            headerAccessor.setLeaveMutable(true);
            MessageHeaders headers = headerAccessor.getMessageHeaders();
            messagingTemplate.convertAndSendToUser(sessionId, "/queue/reply", response, headers);
            log.info("WS sendToUser: session={}, type={}", sessionId, type);
        } catch (Exception e) {
            log.error("Failed to send to user {}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * 向指定用户发送通知（NOTICE 类型快捷方法）
     */
    public void notifyUser(String sessionId, String message) {
        sendToUser(sessionId, MessageType.NOTICE, null, message);
    }

    /**
     * 向房间广播通知
     */
    public void notifyRoom(String houseId, String message) {
        broadcastToRoom(houseId, MessageType.NOTICE, null, message);
    }
}
