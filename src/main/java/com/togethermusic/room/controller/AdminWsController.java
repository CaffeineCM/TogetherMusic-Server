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

    // ---- 兼容旧接口 ----

    @MessageMapping("/auth/admin")
    public void authAdmin(AuthRequest request, SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        broadcaster.notifyUser(sessionId, "管理员权限请由房主在在线用户列表中设置");
    }

    @MessageMapping("/auth/root")
    public void authRoot(AuthRequest request, SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String houseId = houseId(accessor);
        if (houseId == null) return;
        broadcaster.notifyUser(sessionId, "root 角色已停用，房主拥有最高权限");
    }

    // ---- 管理员授权 ----

    @MessageMapping("/user/admin/{targetSessionId}")
    public void grantAdmin(@DestinationVariable String targetSessionId, SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String houseId = houseId(accessor);
        if (!requireOwner(houseId, sessionId)) return;

        SessionUser target = sessionRepo.get(houseId, targetSessionId).orElse(null);
        if (target == null) {
            broadcaster.notifyUser(sessionId, "用户不存在");
            return;
        }
        if (target.isOwner()) {
            broadcaster.notifyUser(sessionId, "房主不需要设置为管理员");
            return;
        }
        if (target.isAdmin()) {
            broadcaster.notifyUser(sessionId, target.displayName() + " 已经是管理员");
            return;
        }

        sessionRepo.put(houseId, target.withRole(SessionUser.ROLE_ADMIN));
        broadcaster.notifyUser(sessionId, "已设置管理员：" + target.displayName());
        broadcaster.sendToUser(targetSessionId, MessageType.NOTICE, null, "你已被房主设置为管理员");
        broadcaster.broadcastToRoom(houseId, MessageType.ONLINE, roomService.getRoomUsers(houseId));
    }

    @MessageMapping("/user/member/{targetSessionId}")
    public void revokeAdmin(@DestinationVariable String targetSessionId, SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String houseId = houseId(accessor);
        if (!requireOwner(houseId, sessionId)) return;

        SessionUser target = sessionRepo.get(houseId, targetSessionId).orElse(null);
        if (target == null) {
            broadcaster.notifyUser(sessionId, "用户不存在");
            return;
        }
        if (target.isOwner()) {
            broadcaster.notifyUser(sessionId, "不能修改房主角色");
            return;
        }
        if (!target.isAdmin()) {
            broadcaster.notifyUser(sessionId, target.displayName() + " 当前不是管理员");
            return;
        }

        sessionRepo.put(houseId, target.withRole(SessionUser.ROLE_MEMBER));
        broadcaster.notifyUser(sessionId, "已取消管理员：" + target.displayName());
        broadcaster.sendToUser(targetSessionId, MessageType.NOTICE, null, "你的管理员权限已被房主取消");
        broadcaster.broadcastToRoom(houseId, MessageType.ONLINE, roomService.getRoomUsers(houseId));
    }

    // ---- 踢人 ----

    @MessageMapping("/user/kick/{targetSessionId}")
    public void kick(@DestinationVariable String targetSessionId, SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String houseId = houseId(accessor);
        if (!requireManager(houseId, sessionId)) return;

        SessionUser target = sessionRepo.get(houseId, targetSessionId).orElse(null);
        if (target == null) {
            broadcaster.notifyUser(sessionId, "用户不存在");
            return;
        }
        SessionUser actor = sessionRepo.get(houseId, sessionId).orElse(null);
        if (!canManageTarget(actor, target)) {
            broadcaster.notifyUser(sessionId, "你不能操作该用户");
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
        if (!requireManager(houseId, sessionId)) return;

        SessionUser target = sessionRepo.get(houseId, targetSessionId).orElse(null);
        if (target == null) {
            broadcaster.notifyUser(sessionId, "用户不存在");
            return;
        }
        SessionUser actor = sessionRepo.get(houseId, sessionId).orElse(null);
        if (!canManageTarget(actor, target)) {
            broadcaster.notifyUser(sessionId, "你不能操作该用户");
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
        broadcaster.sendToUser(sessionId, MessageType.BLACKLIST, blackListRepo.getBlacklistedUsers(houseId), "用户黑名单");
        log.info("[{}] Admin {} blacklisted user {}", houseId, sessionId, targetSessionId);
    }

    @MessageMapping("/user/unblack/{targetSessionId}")
    public void unblackUser(@DestinationVariable String targetSessionId, SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String houseId = houseId(accessor);
        if (!requireManager(houseId, sessionId)) return;

        blackListRepo.unblackUser(houseId, targetSessionId);
        broadcaster.notifyUser(sessionId, "已漂白用户：" + targetSessionId);
        broadcaster.sendToUser(sessionId, MessageType.BLACKLIST, blackListRepo.getBlacklistedUsers(houseId), "用户黑名单");
    }

    @MessageMapping("/user/blacklist")
    public void showBlackUsers(SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String houseId = houseId(accessor);
        if (!requireManager(houseId, sessionId)) return;

        Set<String> list = blackListRepo.getBlacklistedUsers(houseId);
        broadcaster.sendToUser(sessionId, MessageType.BLACKLIST, list, "用户黑名单");
    }

    // ---- 工具方法 ----

    private String houseId(SimpMessageHeaderAccessor accessor) {
        Map<String, Object> attrs = accessor.getSessionAttributes();
        return attrs != null ? (String) attrs.get("houseId") : null;
    }

    private boolean requireManager(String houseId, String sessionId) {
        if (houseId == null) {
            broadcaster.notifyUser(sessionId, "未加入任何房间");
            return false;
        }
        SessionUser user = sessionRepo.get(houseId, sessionId).orElse(null);
        if (user == null || !user.isManager()) {
            broadcaster.notifyUser(sessionId, "你没有权限");
            return false;
        }
        return true;
    }

    private boolean requireOwner(String houseId, String sessionId) {
        if (houseId == null) {
            broadcaster.notifyUser(sessionId, "未加入任何房间");
            return false;
        }
        SessionUser user = sessionRepo.get(houseId, sessionId).orElse(null);
        if (user == null || !user.isOwner()) {
            broadcaster.notifyUser(sessionId, "只有房主可以管理管理员权限");
            return false;
        }
        return true;
    }

    private boolean canManageTarget(SessionUser actor, SessionUser target) {
        if (actor == null || target == null) {
            return false;
        }
        if (actor.sessionId().equals(target.sessionId())) {
            return false;
        }
        if (target.isOwner()) {
            return false;
        }
        if (actor.isOwner()) {
            return true;
        }
        return actor.isAdmin() && !target.isAdmin();
    }

    public record AuthRequest(String password) {}
}
