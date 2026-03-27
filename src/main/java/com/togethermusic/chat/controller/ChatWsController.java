package com.togethermusic.chat.controller;

import com.togethermusic.chat.service.ChatService;
import com.togethermusic.common.websocket.MessageBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatWsController {

    private final ChatService chatService;
    private final MessageBroadcaster broadcaster;

    @MessageMapping("/chat/send")
    public void send(ChatRequest request, SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String houseId = houseId(accessor);
        if (houseId == null) {
            broadcaster.notifyUser(sessionId, "未加入任何房间");
            return;
        }

        String content = request.content();
        if (content == null || content.isBlank()) return;

        // 处理聊天指令
        if (content.startsWith("设置昵称 ")) {
            chatService.setNickname(houseId, sessionId, content.substring(5));
            return;
        }

        chatService.send(houseId, sessionId, content);
    }

    private String houseId(SimpMessageHeaderAccessor accessor) {
        Map<String, Object> attrs = accessor.getSessionAttributes();
        return attrs != null ? (String) attrs.get("houseId") : null;
    }

    public record ChatRequest(String content) {}
}
