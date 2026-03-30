package com.togethermusic.room.controller;

import com.togethermusic.common.websocket.MessageBroadcaster;
import com.togethermusic.common.websocket.MessageType;
import com.togethermusic.repository.RoomRedisRepository;
import com.togethermusic.repository.SessionRedisRepository;
import com.togethermusic.room.dto.RoomTokenStatusResponse;
import com.togethermusic.room.dto.SetMusicSourceRequest;
import com.togethermusic.room.dto.TransferTokenRequest;
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

import java.util.Optional;

/**
 * 房间 Token 管理 WebSocket Controller
 * 处理音乐源设置、Token 持有者转移、授权状态查询等
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class RoomTokenController {

    private final RoomRedisRepository roomRepository;
    private final SessionRedisRepository sessionRepository;
    private final RoomService roomService;
    private final UserMusicAccountRepository accountRepository;
    private final UserRepository userRepository;
    private final MessageBroadcaster broadcaster;

    /**
     * 获取当前房间 Token 授权状态
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
     * 房主取消授权
     * SEND /room/unlink-token
     */
    @MessageMapping("/room/unlink-token")
    public void unlinkToken(StompHeaderAccessor accessor) {
        String houseId = getHouseIdFromSession(accessor);
        String sessionId = accessor.getSessionId();
        Long userId = getUserIdFromSession(accessor);

        if (houseId == null) {
            broadcaster.sendToUser(sessionId, MessageType.NOTICE, "您不在任何房间中");
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

        house.setTokenHolderUserId(null);
        roomRepository.save(house);

        log.info("[RoomToken] House {} creator {} unlinked token", houseId, userId);

        // 通知本人状态更新
        broadcaster.sendToUser(sessionId, MessageType.TOKEN_STATUS, buildStatusResponse(house));
        broadcaster.broadcastToRoom(houseId, MessageType.NOTICE, "房主已取消音乐授权");
    }

    /**
     * 设置房间音乐源和 Token 持有者
     * 房主：可随时设置；管理员：仅当房主未授权时可设置
     * SEND /room/set-music-source
     */
    @MessageMapping("/room/set-music-source")
    public void setMusicSource(SetMusicSourceRequest request, StompHeaderAccessor accessor) {
        String houseId = request.getHouseId();
        String sessionId = accessor.getSessionId();
        Long userId = getUserIdFromSession(accessor);

        log.info("[RoomToken] Session {} setting music source for house {}: source={}",
                sessionId, houseId, request.getSource());

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

        // 管理员只有在房主未授权时才能设置
        if (!isCreator && isAdmin) {
            boolean creatorHasAuthorized = house.getCreatorUserId() != null
                    && house.getCreatorUserId().equals(house.getTokenHolderUserId());
            if (creatorHasAuthorized) {
                broadcaster.sendToUser(sessionId, MessageType.NOTICE, "房主已授权，管理员暂不可设置");
                return;
            }
        }

        if (request.getSource() != null) {
            house.setDefaultMusicSource(request.getSource());
        }

        // 确定 Token 持有者
        Long tokenHolderId = null;
        if (Boolean.TRUE.equals(request.getUseMyAccount())) {
            tokenHolderId = userId;
        } else if (request.getTargetUserId() != null && isCreator) {
            // 仅房主可以指定他人账号
            String dbSource = mapSourceCode(request.getSource());
            boolean hasAccount = accountRepository
                    .findByUserIdAndSource(request.getTargetUserId(), dbSource)
                    .isPresent();
            if (!hasAccount) {
                broadcaster.sendToUser(sessionId, MessageType.NOTICE,
                        "目标用户未绑定 " + request.getSource() + " 账号");
                return;
            }
            tokenHolderId = request.getTargetUserId();
        }

        house.setTokenHolderUserId(tokenHolderId);
        roomRepository.save(house);

        broadcaster.sendToUser(sessionId, MessageType.TOKEN_STATUS, buildStatusResponse(house));
        broadcaster.broadcastToRoom(houseId, MessageType.NOTICE,
                "音乐授权已更新: " + (request.getSource() != null ? request.getSource() : house.getDefaultMusicSource()));

        log.info("[RoomToken] House {} music source set to {}, token holder: {}",
                houseId, house.getDefaultMusicSource(), tokenHolderId);
    }

    /**
     * 创建人转移 Token 持有者权限给其他人
     * SEND /room/transfer-token-holder
     */
    @MessageMapping("/room/transfer-token-holder")
    public void transferTokenHolder(TransferTokenRequest request, StompHeaderAccessor accessor) {
        String houseId = request.getHouseId();
        String sessionId = accessor.getSessionId();
        Long userId = getUserIdFromSession(accessor);
        Long targetUserId = request.getTargetUserId();

        log.info("[RoomToken] Session {} transferring token holder of house {} to user {}",
                sessionId, houseId, targetUserId);

        House house = roomRepository.findById(houseId).orElse(null);
        if (house == null) {
            broadcaster.sendToUser(sessionId, MessageType.NOTICE, "房间不存在");
            return;
        }

        if (!isCreator(house, userId)) {
            broadcaster.sendToUser(sessionId, MessageType.NOTICE, "只有房间创建人可以转移 Token 持有者权限");
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

        String defaultSource = house.getDefaultMusicSource();
        if (defaultSource != null) {
            String dbSource = mapSourceCode(defaultSource);
            boolean hasAccount = accountRepository
                    .findByUserIdAndSource(targetUserId, dbSource)
                    .isPresent();
            if (!hasAccount) {
                broadcaster.sendToUser(sessionId, MessageType.NOTICE,
                        "目标用户未绑定 " + defaultSource + " 账号");
                return;
            }
        }

        house.setTokenHolderUserId(targetUserId);
        roomRepository.save(house);

        String targetNickname = userRepository.findById(targetUserId)
                .map(user -> Optional.ofNullable(user.getNickname()).orElse(user.getUsername()))
                .orElse("未知用户");

        broadcaster.broadcastToRoom(houseId, MessageType.NOTICE,
                "Token 持有者已转移给: " + targetNickname);

        log.info("[RoomToken] House {} token holder transferred to user {} ({})",
                houseId, targetUserId, targetNickname);
    }

    /**
     * 获取当前房间的 Token 持有者信息（旧接口，保持兼容）
     * SEND /room/token-holder
     */
    @MessageMapping("/room/token-holder")
    public void getTokenHolder(StompHeaderAccessor accessor) {
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

    // ========== 辅助方法 ==========

    private RoomTokenStatusResponse buildStatusResponse(House house) {
        Long creatorId = house.getCreatorUserId();
        Long tokenHolderId = house.getTokenHolderUserId();

        String creatorDisplayName = null;
        if (creatorId != null) {
            creatorDisplayName = userRepository.findById(creatorId)
                    .map(u -> Optional.ofNullable(u.getNickname()).orElse(u.getUsername()))
                    .orElse(null);
        }

        String tokenHolderDisplayName = null;
        if (tokenHolderId != null && tokenHolderId.equals(creatorId)) {
            tokenHolderDisplayName = creatorDisplayName;
        } else if (tokenHolderId != null) {
            tokenHolderDisplayName = userRepository.findById(tokenHolderId)
                    .map(u -> Optional.ofNullable(u.getNickname()).orElse(u.getUsername()))
                    .orElse(null);
        }

        boolean creatorHasAuthorized = creatorId != null && creatorId.equals(tokenHolderId);
        boolean adminCanAuthorize = !creatorHasAuthorized;

        return new RoomTokenStatusResponse(
                house.getId(),
                house.getDefaultMusicSource(),
                creatorId,
                creatorDisplayName,
                creatorHasAuthorized,
                tokenHolderId,
                tokenHolderDisplayName,
                adminCanAuthorize
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
        if (userId == null || house.getCreatorUserId() == null) {
            return false;
        }
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
}
