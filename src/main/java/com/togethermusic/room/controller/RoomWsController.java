package com.togethermusic.room.controller;

import com.togethermusic.common.code.ErrorCode;
import com.togethermusic.common.response.Response;
import com.togethermusic.common.util.ClientIpUtils;
import com.togethermusic.common.websocket.MessageBroadcaster;
import com.togethermusic.common.websocket.MessageType;
import com.togethermusic.repository.RoomRedisRepository;
import com.togethermusic.repository.SessionRedisRepository;
import com.togethermusic.room.dto.CreateRoomRequest;
import com.togethermusic.room.dto.JoinRoomRequest;
import com.togethermusic.room.dto.RoomSessionView;
import com.togethermusic.room.model.House;
import com.togethermusic.room.model.SessionUser;
import com.togethermusic.music.service.MusicService;
import com.togethermusic.room.service.RoomService;
import com.togethermusic.room.service.SessionLifecycleService;
import com.togethermusic.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class RoomWsController {

    private final RoomService roomService;
    private final RoomRedisRepository roomRepository;
    private final SessionRedisRepository sessionRepository;
    private final MessageBroadcaster broadcaster;
    private final UserRepository userRepository;
    private final MusicService musicService;
    private final SessionLifecycleService sessionLifecycleService;

    @MessageMapping("/house/add")
    @SendToUser(value = "/queue/reply", broadcast = false)
    public Response<?> createRoom(CreateRoomRequest request, SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        Map<String, Object> attrs = accessor.getSessionAttributes();
        String remoteAddress = attrs != null ? (String) attrs.getOrDefault("remoteAddress", "unknown") : "unknown";
        log.info("WS createRoom received: session={}, name={}", sessionId, request.name());

        try {
            // 获取登录用户ID（如果有）
            Long creatorUserId = attrs != null ? (Long) attrs.get("registeredUserId") : null;
            if (creatorUserId == null) {
                return new Response<>(
                        ErrorCode.UNAUTHORIZED.getCode(),
                        "创建房间前请先登录",
                        null,
                        MessageType.ADD_HOUSE.name()
                );
            }
            // 默认音乐源（可从配置或请求中获取）
            String defaultSource = "wy";

            House house = roomService.createRoom(request, sessionId, remoteAddress, creatorUserId, defaultSource);

            // 将 houseId 写入 session attributes，供后续消息处理使用
            if (attrs != null) {
                attrs.put("houseId", house.getId());
            }

            String currentRole = ensureSessionJoined(house, sessionId, attrs);

            // 如果是登录用户，同步昵称
            syncNicknameIfLoggedIn(accessor, house.getId(), sessionId);

            broadcaster.broadcastToRoom(house.getId(), MessageType.ONLINE, roomService.getRoomUsers(house.getId()));
            syncCurrentPlaying(sessionId, house.getId());
            RoomSessionView roomView = roomService.buildRoomSessionView(house, sessionId, currentRole);

            log.info("WS createRoom success: session={}, houseId={}", sessionId, house.getId());
            return Response.success(roomView, "房间创建成功", MessageType.ADD_HOUSE.name());
        } catch (Exception e) {
            log.warn("WS createRoom failed: session={}, error={}", sessionId, e.getMessage());
            return new Response<>(
                    ErrorCode.INTERNAL_ERROR.getCode(),
                    e.getMessage(),
                    null,
                    MessageType.ADD_HOUSE.name()
            );
        }
    }

    @MessageMapping("/house/enter")
    @SendToUser(value = "/queue/reply", broadcast = false)
    public Response<?> joinRoom(JoinRoomRequest request, SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        Map<String, Object> attrs = accessor.getSessionAttributes();
        String remoteAddress = attrs != null ? (String) attrs.getOrDefault("remoteAddress", "unknown") : "unknown";
        log.info("WS joinRoom received: session={}, houseId={}", sessionId, request.houseId());

        try {
            House house = roomService.joinRoom(request, sessionId, remoteAddress);

            if (attrs != null) {
                attrs.put("houseId", house.getId());
            }

            String currentRole = ensureSessionJoined(house, sessionId, attrs);

            syncNicknameIfLoggedIn(accessor, house.getId(), sessionId);

            // 推送房间公告
            if (house.getAnnounce() != null) {
                broadcaster.sendToUser(sessionId, MessageType.ANNOUNCEMENT, house.getAnnounce());
            }

            broadcaster.broadcastToRoom(house.getId(), MessageType.ONLINE, roomService.getRoomUsers(house.getId()));
            syncCurrentPlaying(sessionId, house.getId());
            RoomSessionView roomView = roomService.buildRoomSessionView(house, sessionId, currentRole);

            log.info("WS joinRoom success: session={}, houseId={}", sessionId, house.getId());
            return Response.success(roomView, "进入房间成功", MessageType.ENTER_HOUSE.name());
        } catch (Exception e) {
            log.warn("WS joinRoom failed: session={}, houseId={}, error={}", sessionId, request.houseId(), e.getMessage());
            return new Response<>(
                    ErrorCode.INTERNAL_ERROR.getCode(),
                    e.getMessage(),
                    null,
                    MessageType.ENTER_HOUSE.name()
            );
        }
    }

    @MessageMapping("/house/search")
    @SendToUser(value = "/queue/reply", broadcast = false)
    public Response<?> listRooms(SimpMessageHeaderAccessor accessor) {
        return Response.success(roomService.listRooms(), "", MessageType.SEARCH_HOUSE.name());
    }

    @MessageMapping("/house/houseuser")
    @SendToUser(value = "/queue/reply", broadcast = false)
    public Response<?> listRoomUsers(SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        Map<String, Object> attrs = accessor.getSessionAttributes();
        String houseId = attrs != null ? (String) attrs.get("houseId") : null;

        if (houseId == null) {
            return new Response<>(
                    ErrorCode.BAD_REQUEST.getCode(),
                    "未加入任何房间",
                    null,
                    MessageType.ONLINE.name()
            );
        }

        List<SessionUser> users = roomService.getRoomUsers(houseId);
        return Response.success(users, "", MessageType.ONLINE.name());
    }

    private void syncNicknameIfLoggedIn(SimpMessageHeaderAccessor accessor, String houseId, String sessionId) {
        Map<String, Object> attrs = accessor.getSessionAttributes();
        if (attrs == null) return;
        Long registeredUserId = (Long) attrs.get("registeredUserId");
        if (registeredUserId != null) {
            resolveDisplayName(registeredUserId, null).ifPresent(displayName ->
                    roomService.updateDisplayName(houseId, sessionId, displayName)
            );
        }
    }

    private String ensureSessionJoined(House house, String sessionId, Map<String, Object> attrs) {
        String houseId = house.getId();
        sessionLifecycleService.cancelPendingDestroy(houseId);

        String remoteAddress = attrs != null ? (String) attrs.getOrDefault("remoteAddress", "unknown") : "unknown";
        Long registeredUserId = attrs != null ? (Long) attrs.get("registeredUserId") : null;
        String role = roomService.resolveRole(house, sessionId, remoteAddress, registeredUserId);
        String displayName = resolveDisplayName(registeredUserId, remoteAddress)
                .orElseGet(() -> ClientIpUtils.buildGuestDisplayName(remoteAddress, sessionId));
        SessionUser user = new SessionUser(sessionId, houseId, displayName, remoteAddress, role, null, registeredUserId);
        sessionRepository.put(houseId, user);
        return role;
    }

    private void syncCurrentPlaying(String sessionId, String houseId) {
        broadcaster.sendToUser(sessionId, MessageType.PLAYBACK, musicService.getPlaybackSnapshot(houseId), "当前播放状态");
        broadcaster.sendToUser(sessionId, MessageType.PICK, musicService.getPickList(houseId), "点歌列表");
    }

    private java.util.Optional<String> resolveDisplayName(Long registeredUserId, String remoteAddress) {
        if (registeredUserId == null) {
            return java.util.Optional.empty();
        }
        return userRepository.findById(registeredUserId)
                .map(user -> {
                    if (user.getNickname() != null && !user.getNickname().isBlank()) {
                        return user.getNickname();
                    }
                    if (user.getUsername() != null && !user.getUsername().isBlank()) {
                        return user.getUsername();
                    }
                    return remoteAddress != null ? ClientIpUtils.buildGuestDisplayName(remoteAddress, null) : "匿名用户";
                });
    }
}
