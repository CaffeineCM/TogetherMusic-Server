package com.togethermusic.music.controller;

import com.togethermusic.common.websocket.MessageBroadcaster;
import com.togethermusic.music.service.MusicService;
import com.togethermusic.repository.SessionRedisRepository;
import com.togethermusic.room.model.SessionUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.Map;

/**
 * 音乐 WebSocket 控制器
 * 所有消息处理统一从 session attributes 取 houseId 和 sessionId
 * 权限校验委托给 MusicService
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class MusicWsController {

    private final MusicService musicService;
    private final SessionRedisRepository sessionRepo;
    private final MessageBroadcaster broadcaster;

    // ---- 点歌 ----

    @MessageMapping("/music/pick")
    public void pick(PickRequest request, SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String houseId = houseId(accessor);
        if (houseId == null) { broadcaster.notifyUser(sessionId, "未加入任何房间"); return; }

        try {
            if (request.id() != null) {
                musicService.pickById(houseId, sessionId, request.id(), request.source(), request.quality());
            } else {
                musicService.pick(houseId, sessionId, request.name(), request.source(), request.quality());
            }
        } catch (Exception e) {
            broadcaster.notifyUser(sessionId, e.getMessage());
        }
    }

    @MessageMapping("/music/pickPlaylist")
    public void pickPlaylist(PickPlaylistRequest request, SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String houseId = houseId(accessor);
        if (houseId == null) { broadcaster.notifyUser(sessionId, "未加入任何房间"); return; }

        try {
            var result = musicService.pickPlaylist(houseId, sessionId, request.playlistId(), request.source());
            broadcaster.notifyUser(
                    sessionId,
                    "已加入 " + result.added() + " 首"
                            + (result.duplicate() > 0 ? "，跳过重复 " + result.duplicate() + " 首" : "")
                            + (result.blacklisted() > 0 ? "，跳过黑名单 " + result.blacklisted() + " 首" : "")
                            + (result.overflow() > 0 ? "，超出队列容量 " + result.overflow() + " 首" : "")
            );
        } catch (Exception e) {
            broadcaster.notifyUser(sessionId, e.getMessage());
        }
    }

    // ---- 点赞 ----

    @MessageMapping("/music/good/{musicId}")
    public void like(@DestinationVariable String musicId, SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String houseId = houseId(accessor);
        if (houseId == null) return;
        musicService.like(houseId, sessionId, musicId);
    }

    // ---- 投票切歌 ----

    @MessageMapping("/music/skip/vote")
    public void vote(SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String houseId = houseId(accessor);
        if (houseId == null) { broadcaster.notifyUser(sessionId, "未加入任何房间"); return; }
        try {
            musicService.vote(houseId, sessionId);
        } catch (Exception e) {
            broadcaster.notifyUser(sessionId, e.getMessage());
        }
    }

    // ---- 删除 ----

    @MessageMapping("/music/delete")
    public void delete(MusicIdRequest request, SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String houseId = houseId(accessor);
        if (houseId == null) return;
        try {
            musicService.delete(houseId, sessionId, request.id());
        } catch (Exception e) {
            broadcaster.notifyUser(sessionId, e.getMessage());
        }
    }

    // ---- 管理员操作 ----

    @MessageMapping("/music/top")
    public void top(MusicIdRequest request, SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String houseId = houseId(accessor);
        if (!requireManager(houseId, sessionId)) return;
        try {
            musicService.top(houseId, request.id());
        } catch (Exception e) {
            broadcaster.notifyUser(sessionId, e.getMessage());
        }
    }

    @MessageMapping("/music/clear")
    public void clear(SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String houseId = houseId(accessor);
        if (!requireManager(houseId, sessionId)) return;
        musicService.clear(houseId);
        broadcaster.notifyUser(sessionId, "清空列表成功");
    }

    @MessageMapping("/music/black")
    public void blackMusic(MusicIdRequest request, SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String houseId = houseId(accessor);
        if (!requireManager(houseId, sessionId)) return;
        try {
            musicService.blackMusic(houseId, request.id());
            broadcaster.notifyUser(sessionId, "音乐拉黑成功");
        } catch (Exception e) {
            broadcaster.notifyUser(sessionId, e.getMessage());
        }
    }

    @MessageMapping("/music/unblack")
    public void unblackMusic(MusicIdRequest request, SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String houseId = houseId(accessor);
        if (!requireManager(houseId, sessionId)) return;
        try {
            musicService.unblackMusic(houseId, request.id());
            broadcaster.notifyUser(sessionId, "音乐漂白成功");
        } catch (Exception e) {
            broadcaster.notifyUser(sessionId, e.getMessage());
        }
    }

    @MessageMapping("/music/banchoose/{ban}")
    public void banChoose(@DestinationVariable boolean ban, SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String houseId = houseId(accessor);
        if (!requireManager(houseId, sessionId)) return;
        musicService.setSearchEnabled(houseId, !ban);
    }

    @MessageMapping("/music/banswitch/{ban}")
    public void banSwitch(@DestinationVariable boolean ban, SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String houseId = houseId(accessor);
        if (!requireManager(houseId, sessionId)) return;
        musicService.setSwitchEnabled(houseId, !ban);
    }

    @MessageMapping("/music/goodmodel/{enabled}")
    public void goodModel(@DestinationVariable boolean enabled, SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String houseId = houseId(accessor);
        if (!requireManager(houseId, sessionId)) return;
        musicService.setGoodModel(houseId, enabled);
    }

    @MessageMapping("/music/musiccirclemodel/{enabled}")
    public void musicCircle(@DestinationVariable boolean enabled, SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String houseId = houseId(accessor);
        if (!requireManager(houseId, sessionId)) return;
        musicService.setMusicCircle(houseId, enabled);
    }

    @MessageMapping("/music/listcirclemodel/{enabled}")
    public void listCircle(@DestinationVariable boolean enabled, SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String houseId = houseId(accessor);
        if (!requireManager(houseId, sessionId)) return;
        musicService.setListCircle(houseId, enabled);
    }

    @MessageMapping("/music/randommodel/{enabled}")
    public void randomModel(@DestinationVariable boolean enabled, SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String houseId = houseId(accessor);
        if (!requireManager(houseId, sessionId)) return;
        musicService.setRandomModel(houseId, enabled);
    }

    @MessageMapping("/music/volume/{volume}")
    public void volume(@DestinationVariable int volume, SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String houseId = houseId(accessor);
        if (!requireManager(houseId, sessionId)) return;
        musicService.setVolume(houseId, volume);
    }

    @MessageMapping("/music/playback/pause")
    public void pausePlayback(SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String houseId = houseId(accessor);
        if (houseId == null) { broadcaster.notifyUser(sessionId, "未加入任何房间"); return; }
        musicService.pausePlayback(houseId);
    }

    @MessageMapping("/music/playback/resume")
    public void resumePlayback(SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String houseId = houseId(accessor);
        if (houseId == null) { broadcaster.notifyUser(sessionId, "未加入任何房间"); return; }
        musicService.resumePlayback(houseId);
    }

    @MessageMapping("/music/playback/seek/{positionMs}")
    public void seekPlayback(@DestinationVariable long positionMs, SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String houseId = houseId(accessor);
        if (houseId == null) { broadcaster.notifyUser(sessionId, "未加入任何房间"); return; }
        musicService.seekPlayback(houseId, positionMs);
    }

    @MessageMapping("/music/voterate/{rate}")
    public void voteRate(@DestinationVariable float rate, SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String houseId = houseId(accessor);
        if (!requireManager(houseId, sessionId)) return;
        if (rate <= 0 || rate > 1) {
            broadcaster.notifyUser(sessionId, "投票率需在 (0, 1] 区间");
            return;
        }
        musicService.setVoteRate(houseId, rate);
    }

    @MessageMapping("/music/setDefaultPlaylist")
    public void setDefaultPlaylist(DefaultPlaylistRequest request, SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String houseId = houseId(accessor);
        if (!requireManager(houseId, sessionId)) return;
        int count = musicService.addDefaultPlaylist(houseId, request.playlistId(), request.source());
        broadcaster.notifyUser(sessionId, "已添加 " + count + " 首歌至默认播放列表");
    }

    @MessageMapping("/music/clearDefaultPlaylist")
    public void clearDefaultPlaylist(SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String houseId = houseId(accessor);
        if (!requireManager(houseId, sessionId)) return;
        musicService.clearDefaultPlaylist(houseId);
        broadcaster.notifyUser(sessionId, "默认播放列表已清空");
    }

    @MessageMapping("/music/blackmusic")
    public void showBlackMusic(SimpMessageHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        String houseId = houseId(accessor);
        if (!requireManager(houseId, sessionId)) return;
        String list = musicService.getBlackMusicList(houseId);
        broadcaster.notifyUser(sessionId, list.isBlank() ? "暂无拉黑列表" : list);
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

    // ---- 内部 DTO（record） ----

    public record PickRequest(String id, String name, String source, String quality) {}
    public record PickPlaylistRequest(String playlistId, String source) {}
    public record MusicIdRequest(String id) {}
    public record DefaultPlaylistRequest(String playlistId, String source) {}
}
