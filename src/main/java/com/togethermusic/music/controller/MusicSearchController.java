package com.togethermusic.music.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.togethermusic.common.code.ErrorCode;
import com.togethermusic.common.exception.BusinessException;
import com.togethermusic.common.response.Response;
import com.togethermusic.music.dto.MusicDiscoveryContext;
import com.togethermusic.music.dto.MusicPlaylistSummary;
import com.togethermusic.music.dto.MusicToplistSummary;
import com.togethermusic.music.model.Music;
import com.togethermusic.music.service.MusicService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/music")
@RequiredArgsConstructor
public class MusicSearchController {

    private final MusicService musicService;

    /**
     * 搜索歌曲，供前端展示候选列表
     * GET /api/v1/music/search?keyword=xxx&source=wy&quality=320k
     */
    @GetMapping("/search")
    public Response<java.util.List<Music>> search(
            @RequestParam String houseId,
            @RequestParam String keyword,
            @RequestParam(defaultValue = "wy") String source,
            @RequestParam(defaultValue = "320k") String quality
    ) {
        if (!StringUtils.hasText(keyword)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "关键词不能为空");
        }

        var results = musicService.searchCandidates(houseId, keyword, source, quality);
        if (results.isEmpty()) {
            return Response.failure(ErrorCode.MUSIC_NOT_FOUND.getCode(),
                    ErrorCode.MUSIC_NOT_FOUND.getMessage());
        }
        return Response.success(results);
    }

    @GetMapping("/discovery/context")
    public Response<MusicDiscoveryContext> discoveryContext(@RequestParam String houseId) {
        Long currentUserId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        return Response.success(musicService.getDiscoveryContext(houseId, currentUserId));
    }

    @GetMapping("/playlists/recommended")
    public Response<java.util.List<MusicPlaylistSummary>> recommendedPlaylists(@RequestParam String houseId) {
        return Response.success(musicService.getRecommendedPlaylists(houseId));
    }

    @GetMapping("/playlists/host-favorites")
    public Response<java.util.List<MusicPlaylistSummary>> hostPlaylists(@RequestParam String houseId) {
        Long currentUserId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        return Response.success(musicService.getHostPlaylists(houseId, currentUserId));
    }

    @GetMapping("/playlists/{playlistId}")
    public Response<java.util.List<Music>> playlistDetail(
            @PathVariable String playlistId,
            @RequestParam String houseId
    ) {
        return Response.success(musicService.getPlaylistDetail(houseId, playlistId));
    }

    @GetMapping("/toplists")
    public Response<java.util.List<MusicToplistSummary>> toplists(@RequestParam String houseId) {
        return Response.success(musicService.getToplists(houseId));
    }
}
