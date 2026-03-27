package com.togethermusic.room.controller;

import com.togethermusic.common.websocket.MessageBroadcaster;
import com.togethermusic.common.websocket.MessageType;
import com.togethermusic.repository.RoomRedisRepository;
import com.togethermusic.room.dto.SetMusicSourceRequest;
import com.togethermusic.room.dto.TransferTokenRequest;
import com.togethermusic.room.model.House;
import com.togethermusic.room.service.RoomService;
import com.togethermusic.user.repository.UserMusicAccountRepository;
import com.togethermusic.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 房间 Token 管理 WebSocket Controller
 * 处理音乐源设置、Token 持有者转移等
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class RoomTokenController {

    private final RoomRedisRepository roomRepository;
    private final RoomService roomService;
    private final UserMusicAccountRepository accountRepository;
    private final UserRepository userRepository;
    private final MessageBroadcaster broadcaster;

    /**
     * 设置房间音乐源和 Token 持有者
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

        // 检查权限：只有创建人可以设置
        if (!isCreator(house, userId)) {
            broadcaster.sendToUser(sessionId, MessageType.NOTICE, "只有房间创建人可以设置音乐源");
            return;
        }

        // 设置默认音乐源
        house.setDefaultMusicSource(request.getSource());

        // 确定 Token 持有者
        Long tokenHolderId = null;
        if (Boolean.TRUE.equals(request.getUseMyAccount())) {
            tokenHolderId = userId;
        } else if (request.getTargetUserId() != null) {
            // 检查目标用户是否绑定了对应平台的账号
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

        // 广播更新
        Map<String, Object> data = new HashMap<>();
        data.put("source", request.getSource());
        data.put("tokenHolderId", tokenHolderId);
        broadcaster.broadcastToRoom(houseId, MessageType.NOTICE, "音乐源已更新为: " + request.getSource());

        log.info("[RoomToken] House {} music source set to {}, token holder: {}",
                houseId, request.getSource(), tokenHolderId);
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

        // 检查权限：只有创建人可以转移
        if (!isCreator(house, userId)) {
            broadcaster.sendToUser(sessionId, MessageType.NOTICE, "只有房间创建人可以转移 Token 持有者权限");
            return;
        }

        // 不能转移给自己
        if (targetUserId.equals(userId)) {
            broadcaster.sendToUser(sessionId, MessageType.NOTICE, "Token 持有者已经是您自己");
            return;
        }

        // 检查目标用户是否存在
        if (!userRepository.existsById(targetUserId)) {
            broadcaster.sendToUser(sessionId, MessageType.NOTICE, "目标用户不存在");
            return;
        }

        // 检查目标用户是否绑定了当前默认音乐源的账号
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

        // 更新 Token 持有者
        house.setTokenHolderUserId(targetUserId);
        roomRepository.save(house);

        // 获取目标用户信息
        String targetNickname = userRepository.findById(targetUserId)
                .map(user -> Optional.ofNullable(user.getNickname()).orElse(user.getUsername()))
                .orElse("未知用户");

        // 广播更新
        Map<String, Object> data = new HashMap<>();
        data.put("tokenHolderId", targetUserId);
        data.put("tokenHolderName", targetNickname);
        broadcaster.broadcastToRoom(houseId, MessageType.NOTICE,
                "Token 持有者已转移给: " + targetNickname);

        log.info("[RoomToken] House {} token holder transferred to user {} ({})",
                houseId, targetUserId, targetNickname);
    }

    /**
     * 获取当前房间的 Token 持有者信息
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

        Map<String, Object> data = new HashMap<>();
        data.put("creatorId", house.getCreatorUserId());
        data.put("tokenHolderId", house.getTokenHolderUserId());
        data.put("defaultSource", house.getDefaultMusicSource());

        broadcaster.sendToUser(sessionId, MessageType.NOTICE, data);
    }

    // ========== 辅助方法 ==========

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

    private String mapSourceCode(String adapterSourceCode) {
        return switch (adapterSourceCode) {
            case "wy" -> "netease";
            case "qq" -> "qq";
            case "kg" -> "kugou";
            default -> adapterSourceCode;
        };
    }
}
