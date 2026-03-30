package com.togethermusic.room.controller;

import com.togethermusic.common.websocket.MessageBroadcaster;
import com.togethermusic.common.websocket.MessageType;
import com.togethermusic.repository.RoomRedisRepository;
import com.togethermusic.repository.SessionRedisRepository;
import com.togethermusic.room.dto.RoomTokenStatusResponse;
import com.togethermusic.room.dto.RoomTokenStatusResponse.SourceAuthStatus;
import com.togethermusic.room.dto.SetMusicSourceRequest;
import com.togethermusic.room.dto.TransferTokenRequest;
import com.togethermusic.room.dto.UnlinkTokenRequest;
import com.togethermusic.room.model.House;
import com.togethermusic.room.model.SessionUser;
import com.togethermusic.room.service.RoomService;
import com.togethermusic.user.repository.UserMusicAccountRepository;
import com.togethermusic.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 房间 Token 管理 WebSocket Controller
 * 每个音乐源（wy / qq / kg）独立维护 Token 持有者
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class RoomTokenController {

    private static final List<String> SUPPORTED_SOURCES = List.of("wy", "qq", "kg");

    private final RoomRedisRepository roomRepository;
    private final SessionRedisRepository sessionRepository;
    private final RoomService roomService;
    private final UserMusicAccountRepository accountRepository;
    private final UserRepository userRepository;
    private final MessageBroadcaster broadcaster;

    /**
     * 获取当前房间各源的 Token 授权状态
     * SEND /room/token-status
     */
    @MessageMapping("/room/token-status")
    public void getTokenStatus(StompHeaderAccessor accessor) {
        String houseId = getHouseIdFromSession(accessor);
        String sessionId = accessor.getSessionId();

        if (houseId == null) {
            broadcaster.sendToUser(sessionId, MessageType.NOTICE, "您不在任何房间中");
            return;
        }

        House house = roomRepository.findById(houseId).orElse(null);
        if (house == null) {
            broadcaster.sendToUser(sessionId, MessageType.NOTICE, "房间不存在");
            return;
        }

        broadcaster.sendToUser(sessionId, MessageType.TOKEN_STATUS, buildStatusResponse(house));
    }

    /**
     * 房主取消指定源的授权
     * SEND /room/unlink-token  body: { source: "wy" }
     */
    @MessageMapping("/room/unlink-token")
    public void unlinkToken(UnlinkTokenRequest request, StompHeaderAccessor accessor) {
        String houseId = getHouseIdFromSession(accessor);
        String sessionId = accessor.getSessionId();
        Long userId = getUserIdFromSession(accessor);
        String source = request.getSource();

        if (houseId == null) {
            broadcaster.sendToUser(sessionId, MessageType.NOTICE, "您不在任何房间中");
            return;
        }
        if (source == null || source.isBlank()) {
            broadcaster.sendToUser(sessionId, MessageType.NOTICE, "请指定要取消授权的音乐源");
            return;
        }

        House house = roomRepository.findById(houseId).orElse(null);
        if (house == null) {
            broadcaster.sendToUser(sessionId, MessageType.NOTICE, "房间不存在");
            return;
        }

        if (!isCreator(house, userId)) {
            broadcaster.sendToUser(sessionId, MessageType.NOTICE, "只有房主可以取消授权");
            return;
        }

        house.setTokenHolderUserId(source, null);
        roomRepository.save(house);

        log.info("[RoomToken] House {} creator {} unlinked token for source {}", houseId, userId, source);

        broadcaster.sendToUser(sessionId, MessageType.TOKEN_STATUS, buildStatusResponse(house));
        broadcaster.broadcastToRoom(houseId, MessageType.NOTICE,
                "房主已取消 " + sourceLabel(source) + " 授权");
    }

    /**
     * 设置指定源的 Token 持有者（房主/管理员）
     * 房主：随时可设置任意源
     * 管理员：仅当该源房主未授权时可设置自己
     * SEND /room/set-music-source  body: { houseId, source, useMyAccount, targetUserId? }
     */
    @MessageMapping("/room/set-music-source")
    public void setMusicSource(SetMusicSourceRequest request, StompHeaderAccessor accessor) {
        String houseId = request.getHouseId();
        String sessionId = accessor.getSessionId();
        Long userId = getUserIdFromSession(accessor);
        String source = request.getSource();

        log.info("[RoomToken] Session {} setting token for house {} source {}", sessionId, houseId, source);

        if (source == null || source.isBlank()) {
            broadcaster.sendToUser(sessionId, MessageType.NOTICE, "请指定音乐源");
            return;
        }

        House house = roomRepository.findById(houseId).orElse(null);
        if (house == null) {
            broadcaster.sendToUser(sessionId, MessageType.NOTICE, "房间不存在");
            return;
        }

        boolean isCreator = isCreator(house, userId);
        boolean isAdmin = isAdmin(houseId, sessionId);

        if (!isCreator && !isAdmin) {
            broadcaster.sendToUser(sessionId, MessageType.NOTICE, "只有房主或管理员可以设置音乐授权");
            return;
        }

        // 管理员只有在该源房主未授权时才能设置
        if (!isCreator && isAdmin && house.creatorHasAuthorized(source)) {
            broadcaster.sendToUser(sessionId, MessageType.NOTICE,
                    "房主已为 " + sourceLabel(source) + " 授权，管理员暂不可设置");
            return;
        }

        // 确定 Token 持有者
        Long tokenHolderId = null;
        if (Boolean.TRUE.equals(request.getUseMyAccount())) {
            // 验证自己确实绑定了该源的账号
            boolean hasAccount = accountRepository
                    .findByUserIdAndSource(userId, mapSourceCode(source))
                    .isPresent();
            if (!hasAccount) {
                broadcaster.sendToUser(sessionId, MessageType.NOTICE,
                        "您未绑定 " + sourceLabel(source) + " 账号，请先在个人中心完成授权");
                return;
            }
            tokenHolderId = userId;
        } else if (request.getTargetUserId() != null && isCreator) {
            // 仅房主可以指定他人账号
            boolean hasAccount = accountRepository
                    .findByUserIdAndSource(request.getTargetUserId(), mapSourceCode(source))
                    .isPresent();
            if (!hasAccount) {
                broadcaster.sendToUser(sessionId, MessageType.NOTICE,
                        "目标用户未绑定 " + sourceLabel(source) + " 账号");
                return;
            }
            tokenHolderId = request.getTargetUserId();
        }

        house.setTokenHolderUserId(source, tokenHolderId);

        // 如果设置了 source，同时更新默认音乐源
        if (Boolean.TRUE.equals(request.getUseMyAccount()) || request.getTargetUserId() != null) {
            house.setDefaultMusicSource(source);
        }

        roomRepository.save(house);

        broadcaster.sendToUser(sessionId, MessageType.TOKEN_STATUS, buildStatusResponse(house));
        broadcaster.broadcastToRoom(houseId, MessageType.NOTICE,
                sourceLabel(source) + " 授权已更新");

        log.info("[RoomToken] House {} source {} token holder set to {}", houseId, source, tokenHolderId);
    }

    /**
     * 创建人转移指定源的 Token 持有者权限
     * SEND /room/transfer-token-holder  body: { houseId, source, targetUserId }
     */
    @MessageMapping("/room/transfer-token-holder")
    public void transferTokenHolder(TransferTokenRequest request, StompHeaderAccessor accessor) {
        String houseId = request.getHouseId();
        String sessionId = accessor.getSessionId();
        Long userId = getUserIdFromSession(accessor);
        Long targetUserId = request.getTargetUserId();
        String source = request.getSource();

        log.info("[RoomToken] Session {} transferring {} token of house {} to user {}",
                sessionId, source, houseId, targetUserId);

        House house = roomRepository.findById(houseId).orElse(null);
        if (house == null) {
            broadcaster.sendToUser(sessionId, MessageType.NOTICE, "房间不存在");
            return;
        }

        if (!isCreator(house, userId)) {
            broadcaster.sendToUser(sessionId, MessageType.NOTICE, "只有房间创建人可以转移 Token 持有者权限");
            return;
        }

        if (source == null || source.isBlank()) {
            broadcaster.sendToUser(sessionId, MessageType.NOTICE, "请指定音乐源");
            return;
        }

        if (targetUserId.equals(userId)) {
            broadcaster.sendToUser(sessionId, MessageType.NOTICE, "Token 持有者已经是您自己");
            return;
        }

        if (!userRepository.existsById(targetUserId)) {
            broadcaster.sendToUser(sessionId, MessageType.NOTICE, "目标用户不存在");
            return;
        }

        boolean hasAccount = accountRepository
                .findByUserIdAndSource(targetUserId, mapSourceCode(source))
                .isPresent();
        if (!hasAccount) {
            broadcaster.sendToUser(sessionId, MessageType.NOTICE,
                    "目标用户未绑定 " + sourceLabel(source) + " 账号");
            return;
        }

        house.setTokenHolderUserId(source, targetUserId);
        roomRepository.save(house);

        String targetNickname = userRepository.findById(targetUserId)
                .map(user -> Optional.ofNullable(user.getNickname()).orElse(user.getUsername()))
                .orElse("未知用户");

        broadcaster.broadcastToRoom(houseId, MessageType.NOTICE,
                sourceLabel(source) + " Token 持有者已转移给: " + targetNickname);

        log.info("[RoomToken] House {} source {} token holder transferred to user {} ({})",
                houseId, source, targetUserId, targetNickname);
    }

    /**
     * 获取当前房间的 Token 信息（旧接口，保持兼容）
     * SEND /room/token-holder
     */
    @MessageMapping("/room/token-holder")
    public void getTokenHolder(StompHeaderAccessor accessor) {
        getTokenStatus(accessor);
    }

    // ========== 辅助方法 ==========

    private RoomTokenStatusResponse buildStatusResponse(House house) {
        Long creatorId = house.getCreatorUserId();

        String creatorDisplayName = null;
        if (creatorId != null) {
            creatorDisplayName = userRepository.findById(creatorId)
                    .map(u -> Optional.ofNullable(u.getNickname()).orElse(u.getUsername()))
                    .orElse(null);
        }

        Map<String, SourceAuthStatus> sourceStatuses = new LinkedHashMap<>();
        for (String source : SUPPORTED_SOURCES) {
            Long tokenHolderId = house.getTokenHolderUserId(source);
            boolean creatorHasAuthorized = house.creatorHasAuthorized(source);
            boolean adminCanAuthorize = !creatorHasAuthorized;

            String tokenHolderDisplayName = null;
            if (tokenHolderId != null) {
                if (tokenHolderId.equals(creatorId)) {
                    tokenHolderDisplayName = creatorDisplayName;
                } else {
                    tokenHolderDisplayName = userRepository.findById(tokenHolderId)
                            .map(u -> Optional.ofNullable(u.getNickname()).orElse(u.getUsername()))
                            .orElse(null);
                }
            }

            sourceStatuses.put(source, new SourceAuthStatus(
                    creatorHasAuthorized,
                    tokenHolderId,
                    tokenHolderDisplayName,
                    adminCanAuthorize
            ));
        }

        return new RoomTokenStatusResponse(
                house.getId(),
                house.getDefaultMusicSource(),
                creatorId,
                creatorDisplayName,
                sourceStatuses
        );
    }

    private Long getUserIdFromSession(StompHeaderAccessor accessor) {
        Object userId = accessor.getSessionAttributes().get("registeredUserId");
        return userId != null ? Long.valueOf(userId.toString()) : null;
    }

    private String getHouseIdFromSession(StompHeaderAccessor accessor) {
        Object houseId = accessor.getSessionAttributes().get("houseId");
        return houseId != null ? houseId.toString() : null;
    }

    private boolean isCreator(House house, Long userId) {
        if (userId == null || house.getCreatorUserId() == null) return false;
        return house.getCreatorUserId().equals(userId);
    }

    private boolean isAdmin(String houseId, String sessionId) {
        return sessionRepository.get(houseId, sessionId)
                .map(SessionUser::isAdmin)
                .orElse(false);
    }

    private String mapSourceCode(String adapterSourceCode) {
        if (adapterSourceCode == null) return null;
        return switch (adapterSourceCode) {
            case "wy" -> "netease";
            case "qq" -> "qq";
            case "kg" -> "kugou";
            default -> adapterSourceCode;
        };
    }

    private String sourceLabel(String source) {
        if (source == null) return "未知";
        return switch (source) {
            case "wy" -> "网易云";
            case "qq" -> "QQ 音乐";
            case "kg" -> "酷狗";
            default -> source;
        };
    }
}
