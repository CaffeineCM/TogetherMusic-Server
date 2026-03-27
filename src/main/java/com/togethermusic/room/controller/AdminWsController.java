package com.togethermusic.room.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.togethermusic.common.websocket.MessageBroadcaster;
import com.togethermusic.common.websocket.MessageType;
import com.togethermusic.repository.BlackListRedisRepository;
import com.togethermusic.repository.SessionRedisRepository;
import com.togethermusic.room.model.House;
import com.togethermusic.room.model.SessionUser;
import com.togethermusic.room.service.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.Map;
import java.util.Set;

/**
 * 管理员 WebSocket 控制器
 * 处理管理员鉴权、踢人、用户黑名单等操作
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class AdminWsController {

    private final SessionRedisRepository sessionRepo;
    private final BlackListRedisRepository blackListRepo;
    private final RoomService roomService;
    private final MessageBroadcaster broadcaster;

    // ---- 管理员鉴权 ----

    @MessageMapping("/auth/admin")
    public void authAdmin(AuthRequest request, SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String houseId = houseId(accessor);
        if (houseId == null) return;

        House house = roomService.getRoom(houseId);
        String adminPwd = house.getAdminPwd();

        if (adminPwd == null || adminPwd.isBlank()) {
            broadcaster.notifyUser(sessionId, "该房间未设置管理员密码");
            return;
        }

        if (!adminPwd.equals(request.password())) {
            broadcaster.notifyUser(sessionId, "管理员密码错误");
            return;
        }

        sessionRepo.get(houseId, sessionId).ifPresent(user -> {
            SessionUser admin = user.withRole(SessionUser.ROLE_ADMIN);
            sessionRepo.put(houseId, admin);
            broadcaster.sendToUser(sessionId, MessageType.AUTH_ADMIN, "admin", "已获得管理员权限");
            log.info("[{}] User {} became admin", houseId, sessionId);
        });
    }

    @MessageMapping("/auth/root")
    public void authRoot(AuthRequest request, SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String houseId = houseId(accessor);
        if (houseId == null) return;

        House house = roomService.getRoom(houseId);
        // root 密码与 admin 密码相同，但角色更高
        String adminPwd = house.getAdminPwd();

        if (adminPwd == null || adminPwd.isBlank()) {
            broadcaster.notifyUser(sessionId, "该房间未设置管理员密码");
            return;
        }

        if (!adminPwd.equals(request.password())) {
            broadcaster.notifyUser(sessionId, "密码错误");
            return;
        }

        sessionRepo.get(houseId, sessionId).ifPresent(user -> {
            SessionUser root = user.withRole(SessionUser.ROLE_ROOT);
            sessionRepo.put(houseId, root);
            broadcaster.sendToUser(sessionId, MessageType.AUTH_ADMIN, "root", "已获得 root 权限");
            log.info("[{}] User {} became root", houseId, sessionId);
        });
    }

    // ---- 踢人 ----

    @MessageMapping("/user/kick/{targetSessionId}")
    public void kick(@DestinationVariable String targetSessionId, SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String houseId = houseId(accessor);
        if (!requireAdmin(houseId, sessionId)) return;

        SessionUser target = sessionRepo.get(houseId, targetSessionId).orElse(null);
        if (target == null) {
            broadcaster.notifyUser(sessionId, "用户不存在");
            return;
        }

        // 通知被踢用户
        broadcaster.sendToUser(targetSessionId, MessageType.KICK, null, "你已被管理员踢出房间");

        // 如果是登录用户，使其 Token 失效（强制下线）
        if (target.registeredUserId() != null) {
            try {
                StpUtil.kickout(target.registeredUserId());
            } catch (Exception e) {
                log.warn("Failed to kickout token for user {}: {}", target.registeredUserId(), e.getMessage());
            }
        }

        // 从会话中移除
        sessionRepo.remove(houseId, targetSessionId);
        broadcaster.notifyUser(sessionId, "已踢出用户：" + target.displayName());
        log.info("[{}] Admin {} kicked user {}", houseId, sessionId, targetSessionId);
    }

    // ---- 用户黑名单 ----

    @MessageMapping("/user/black/{targetSessionId}")
    public void blackUser(@DestinationVariable String targetSessionId, SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String houseId = houseId(accessor);
        if (!requireAdmin(houseId, sessionId)) return;

        SessionUser target = sessionRepo.get(houseId, targetSessionId).orElse(null);
        if (target == null) {
            broadcaster.notifyUser(sessionId, "用户不存在");
            return;
        }

        // 拉黑 sessionId 和 IP
        blackListRepo.blackUser(houseId, targetSessionId);
        if (target.remoteAddress() != null) {
            blackListRepo.blackUser(houseId, target.remoteAddress());
        }

        // 通知并踢出
        broadcaster.sendToUser(targetSessionId, MessageType.KICK, null, "你已被管理员拉黑");
        sessionRepo.remove(houseId, targetSessionId);
        broadcaster.notifyUser(sessionId, "已拉黑用户：" + target.displayName());
        log.info("[{}] Admin {} blacklisted user {}", houseId, sessionId, targetSessionId);
    }

    @MessageMapping("/user/unblack/{targetSessionId}")
    public void unblackUser(@DestinationVariable String targetSessionId, SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String houseId = houseId(accessor);
        if (!requireAdmin(houseId, sessionId)) return;

        blackListRepo.unblackUser(houseId, targetSessionId);
        broadcaster.notifyUser(sessionId, "已漂白用户：" + targetSessionId);
    }

    @MessageMapping("/user/blacklist")
    public void showBlackUsers(SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String houseId = houseId(accessor);
        if (!requireAdmin(houseId, sessionId)) return;

        Set<String> list = blackListRepo.getBlacklistedUsers(houseId);
        String result = list.isEmpty() ? "暂无用户黑名单" : String.join(", ", list);
        broadcaster.notifyUser(sessionId, result);
    }

    // ---- 工具方法 ----

    private String houseId(SimpMessageHeaderAccessor accessor) {
        Map<String, Object> attrs = accessor.getSessionAttributes();
        return attrs != null ? (String) attrs.get("houseId") : null;
    }

    private boolean requireAdmin(String houseId, String sessionId) {
        if (houseId == null) {
            broadcaster.notifyUser(sessionId, "未加入任何房间");
            return false;
        }
        SessionUser user = sessionRepo.get(houseId, sessionId).orElse(null);
        if (user == null || !user.isAdmin()) {
            broadcaster.notifyUser(sessionId, "你没有权限");
            return false;
        }
        return true;
    }

    public record AuthRequest(String password) {}
}
