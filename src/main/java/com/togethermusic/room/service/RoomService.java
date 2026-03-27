package com.togethermusic.room.service;

import com.togethermusic.common.code.ErrorCode;
import com.togethermusic.common.exception.BusinessException;
import com.togethermusic.config.TogetherMusicProperties;
import com.togethermusic.repository.RoomRedisRepository;
import com.togethermusic.repository.SessionRedisRepository;
import com.togethermusic.room.dto.CreateRoomRequest;
import com.togethermusic.room.dto.JoinRoomRequest;
import com.togethermusic.room.dto.RoomSessionView;
import com.togethermusic.room.dto.RoomSummary;
import com.togethermusic.room.model.House;
import com.togethermusic.room.model.SessionUser;
import com.togethermusic.user.repository.UserMusicAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRedisRepository roomRepository;
    private final SessionRedisRepository sessionRepository;
    private final UserMusicAccountRepository accountRepository;
    private final TogetherMusicProperties properties;
    private final RoomPermissionService roomPermissionService;

    /**
     * 创建房间
     * @param request 创建请求
     * @param creatorSessionId 创建者 sessionId
     * @param remoteAddress 创建者 IP
     * @param creatorUserId 创建者用户ID（已登录用户）
     * @param defaultSource 默认音乐源
     */
    public House createRoom(CreateRoomRequest request, String creatorSessionId, String remoteAddress,
                           Long creatorUserId, String defaultSource) {
        // IP 创建数量限制
        long ipCount = roomRepository.countIpHouses(remoteAddress);
        if (ipCount >= properties.getRoom().getIpHouseLimit()) {
            throw new BusinessException(ErrorCode.ROOM_LIMIT_EXCEEDED,
                    "每个 IP 最多创建 " + properties.getRoom().getIpHouseLimit() + " 个房间");
        }
        // 系统总房间数限制
        if (roomRepository.count() >= properties.getRoom().getMaxHouseSize()) {
            throw new BusinessException(ErrorCode.ROOM_LIMIT_EXCEEDED, "系统房间数已达上限");
        }

        String houseId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        boolean needPwd = StringUtils.hasText(request.password());
        boolean canDestroy = !Boolean.TRUE.equals(request.keepRoom());

        // 确定 Token 持有者：如果创建者绑定了默认音乐源的账号，则使用创建者的账号
        Long tokenHolderId = null;
        if (creatorUserId != null && defaultSource != null) {
            String dbSource = mapSourceCode(defaultSource);
            boolean hasAccount = accountRepository
                    .findByUserIdAndSource(creatorUserId, dbSource)
                    .isPresent();
            if (hasAccount) {
                tokenHolderId = creatorUserId;
            }
        }

        House house = House.builder()
                .id(houseId)
                .name(request.name())
                .desc(request.desc())
                .creatorSessionId(creatorSessionId)
                .remoteAddress(remoteAddress)
                .createTime(System.currentTimeMillis())
                .password(request.password())
                .needPwd(needPwd)
                .canDestroy(canDestroy)
                .enableStatus(false)
                .adminPwd(request.adminPwd())
                .creatorUserId(creatorUserId)
                .tokenHolderUserId(tokenHolderId)
                .defaultMusicSource(defaultSource)
                .build();

        roomRepository.save(house);
        roomRepository.addIpHouse(remoteAddress, houseId);
        log.info("Room created: id={}, name={}, creator={}, tokenHolder={}, keepRoom={}",
                houseId, request.name(), creatorUserId, tokenHolderId, Boolean.TRUE.equals(request.keepRoom()));
        return house;
    }

    /**
     * 创建房间（向后兼容，未登录用户使用）
     */
    public House createRoom(CreateRoomRequest request, String creatorSessionId, String remoteAddress) {
        return createRoom(request, creatorSessionId, remoteAddress, null, "wy");
    }

    /**
     * 加入房间（密码验证）
     */
    public House joinRoom(JoinRoomRequest request, String sessionId, String remoteAddress) {
        House house = roomRepository.findById(request.houseId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));

        if (Boolean.TRUE.equals(house.getNeedPwd())) {
            if (!StringUtils.hasText(request.password()) ||
                    !request.password().equals(house.getPassword())) {
                throw new BusinessException(ErrorCode.ROOM_WRONG_PASSWORD);
            }
        }

        // 检查用户是否在黑名单
        // （黑名单检查由 WebSocket 握手拦截器或 SessionLifecycleService 处理）

        return house;
    }

    /**
     * 获取房间列表摘要
     */
    public List<RoomSummary> listRooms() {
        return roomRepository.findAll().stream()
                .map(house -> new RoomSummary(
                        house.getId(),
                        house.getName(),
                        house.getDesc(),
                        Boolean.TRUE.equals(house.getNeedPwd()),
                        (int) sessionRepository.count(house.getId())
                ))
                .toList();
    }

    /**
     * 获取房间详情
     */
    public House getRoom(String houseId) {
        return roomRepository.findById(houseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));
    }

    /**
     * 获取房间在线用户列表
     */
    public List<SessionUser> getRoomUsers(String houseId) {
        return sessionRepository.findAll(houseId);
    }

    /**
     * 更新用户在房间内的显示名（登录用户进入房间后同步昵称）
     */
    public void updateDisplayName(String houseId, String sessionId, String displayName) {
        sessionRepository.get(houseId, sessionId).ifPresent(user -> {
            sessionRepository.put(houseId, user.withDisplayName(displayName));
        });
    }

    public RoomSessionView buildRoomSessionView(House house, String currentSessionId, String currentUserRole) {
        return RoomSessionView.from(
                house,
                (int) sessionRepository.count(house.getId()),
                currentSessionId,
                currentUserRole
        );
    }

    public String resolveRole(House house, String sessionId, String remoteAddress, Long registeredUserId) {
        return roomPermissionService.resolveRole(house, sessionId, remoteAddress, registeredUserId);
    }

    /**
     * 将适配器 sourceCode 映射到数据库存储的 source 值
     */
    private String mapSourceCode(String adapterSourceCode) {
        return switch (adapterSourceCode) {
            case "wy" -> "netease";
            case "qq" -> "qq";
            case "kg" -> "kugou";
            default -> adapterSourceCode;
        };
    }
}
