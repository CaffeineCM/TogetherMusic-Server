package com.togethermusic.room.service;

import com.togethermusic.common.websocket.MessageBroadcaster;
import com.togethermusic.common.websocket.MessageType;
import com.togethermusic.common.util.ClientIpUtils;
import com.togethermusic.music.model.RoomConfig;
import com.togethermusic.repository.ConfigRedisRepository;
import com.togethermusic.repository.RoomRedisRepository;
import com.togethermusic.repository.SessionRedisRepository;
import com.togethermusic.repository.VoteRedisRepository;
import com.togethermusic.room.model.House;
import com.togethermusic.room.model.SessionUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Map;

/**
 * WebSocket 会话生命周期管理
 * 监听 Spring 的 WebSocket 事件，处理用户连接和断开
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionLifecycleService {

    private static final long EMPTY_ROOM_DESTROY_DELAY_SECONDS = 20;

    private final SessionRedisRepository sessionRepository;
    private final RoomRedisRepository roomRepository;
    private final ConfigRedisRepository configRepository;
    private final VoteRedisRepository voteRepository;
    private final MessageBroadcaster broadcaster;
    private final RoomPermissionService roomPermissionService;
    private final ScheduledExecutorService destroyScheduler = Executors.newSingleThreadScheduledExecutor();
    private final Map<String, ScheduledFuture<?>> pendingDestroyTasks = new ConcurrentHashMap<>();

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        Map<String, Object> attrs = accessor.getSessionAttributes();
        if (attrs == null || sessionId == null) return;

        String houseId = (String) attrs.get("houseId");
        if (houseId == null) return;

        Long registeredUserId = (Long) attrs.get("registeredUserId");
        String remoteAddress = (String) attrs.getOrDefault("remoteAddress", "unknown");

        String role = roomRepository.findById(houseId)
                .map(house -> roomPermissionService.resolveRole(house, sessionId, remoteAddress, registeredUserId))
                .orElse(SessionUser.ROLE_MEMBER);

        // 构建显示名：登录用户后续由 RoomService 设置昵称，此处先用 IP 脱敏
        String displayName = ClientIpUtils.buildGuestDisplayName(remoteAddress, sessionId);

        SessionUser user = new SessionUser(
                sessionId, houseId, displayName,
                remoteAddress, role,
                null, registeredUserId
        );
        sessionRepository.put(houseId, user);

        // 广播在线用户列表
        broadcastOnlineUsers(houseId);
        log.info("User connected: session={}, house={}", sessionId, houseId);
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        Map<String, Object> attrs = accessor.getSessionAttributes();
        if (attrs == null || sessionId == null) return;

        String houseId = (String) attrs.get("houseId");
        if (houseId == null) return;

        sessionRepository.remove(houseId, sessionId);
        broadcastOnlineUsers(houseId);

        if (sessionRepository.count(houseId) == 0) {
            scheduleEmptyRoomDestroy(houseId);
        }

        log.info("User disconnected: session={}, house={}", sessionId, houseId);
    }

    public void cancelPendingDestroy(String houseId) {
        ScheduledFuture<?> future = pendingDestroyTasks.remove(houseId);
        if (future != null) {
            future.cancel(false);
            log.info("Cancelled pending destroy for room {}", houseId);
        }
    }

    private void scheduleEmptyRoomDestroy(String houseId) {
        cancelPendingDestroy(houseId);
        ScheduledFuture<?> future = destroyScheduler.schedule(() -> {
            if (sessionRepository.count(houseId) > 0) {
                pendingDestroyTasks.remove(houseId);
                return;
            }
            clearEmptyRoomRuntimeState(houseId);
            roomRepository.findById(houseId).ifPresent(house -> {
                if (Boolean.TRUE.equals(house.getCanDestroy())) {
                    roomRepository.delete(houseId);
                    if (house.getRemoteAddress() != null && !house.getRemoteAddress().isBlank()) {
                        roomRepository.removeIpHouse(house.getRemoteAddress(), houseId);
                    }
                    log.info("Empty room destroyed after grace period: {}", houseId);
                }
            });
            pendingDestroyTasks.remove(houseId);
        }, EMPTY_ROOM_DESTROY_DELAY_SECONDS, TimeUnit.SECONDS);
        pendingDestroyTasks.put(houseId, future);
        log.info("Scheduled room {} for destroy in {}s", houseId, EMPTY_ROOM_DESTROY_DELAY_SECONDS);
    }

    private void clearEmptyRoomRuntimeState(String houseId) {
        roomRepository.clearPlaying(houseId);
        voteRepository.reset(houseId);
        configRepository.setBoolean(houseId, RoomConfig.PUSH_SWITCH, false);
        configRepository.setLong(houseId, RoomConfig.LAST_PUSH_TIME, 0L);
        configRepository.setLong(houseId, RoomConfig.LAST_DURATION, 0L);
        log.info("Cleared runtime state for empty room {}", houseId);
    }

    private void broadcastOnlineUsers(String houseId) {
        List<SessionUser> users = sessionRepository.findAll(houseId);
        broadcaster.broadcastToRoom(houseId, MessageType.ONLINE, users);
    }
}
