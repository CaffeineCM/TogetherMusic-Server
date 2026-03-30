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
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;

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
    public Response<MusicDiscoveryContext> discoveryContext(
            @RequestParam String houseId,
            @RequestParam(defaultValue = "wy") String source
    ) {
        Long currentUserId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        return Response.success(musicService.getDiscoveryContext(houseId, currentUserId, source));
    }

    @GetMapping("/playlists/recommended")
    public Response<java.util.List<MusicPlaylistSummary>> recommendedPlaylists(
            @RequestParam String houseId,
            @RequestParam(defaultValue = "wy") String source
    ) {
        return Response.success(musicService.getRecommendedPlaylists(houseId, source));
    }

    @GetMapping("/playlists/host-favorites")
    public Response<java.util.List<MusicPlaylistSummary>> hostPlaylists(
            @RequestParam String houseId,
            @RequestParam(defaultValue = "wy") String source
    ) {
        Long currentUserId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        return Response.success(musicService.getHostPlaylists(houseId, currentUserId, source));
    }

    @GetMapping("/playlists/{playlistId}")
    public Response<java.util.List<Music>> playlistDetail(
            @PathVariable String playlistId,
            @RequestParam String houseId,
            @RequestParam(defaultValue = "wy") String source
    ) {
        return Response.success(musicService.getPlaylistDetail(houseId, playlistId, source));
    }

    @GetMapping("/toplists")
    public Response<java.util.List<MusicToplistSummary>> toplists(
            @RequestParam String houseId,
            @RequestParam(defaultValue = "wy") String source
    ) {
        return Response.success(musicService.getToplists(houseId, source));
    }

    @GetMapping("/image")
    public ResponseEntity<byte[]> imageProxy(@RequestParam String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }

        if (!isAllowedImageHost(uri)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        HttpResponse<byte[]> response = Unirest.get(uri.toString())
                .header("User-Agent",
                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                                + "(KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36")
                .header("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                .header("Referer", "https://music.163.com/")
                .asBytes();

        if (response.getStatus() < 200 || response.getStatus() >= 300) {
            return ResponseEntity.status(response.getStatus()).build();
        }

        String contentType = response.getHeaders().getFirst("Content-Type");
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (StringUtils.hasText(contentType)) {
            try {
                mediaType = MediaType.parseMediaType(contentType);
            } catch (Exception ignored) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
                .body(response.getBody());
    }

    private boolean isAllowedImageHost(URI uri) {
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (!StringUtils.hasText(scheme) || !StringUtils.hasText(host)) {
            return false;
        }

        String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
        if (!"http".equals(normalizedScheme) && !"https".equals(normalizedScheme)) {
            return false;
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT);
        return normalizedHost.endsWith(".music.126.net")
                || normalizedHost.equals("music.126.net")
                || normalizedHost.endsWith(".gtimg.cn")
                || normalizedHost.equals("gtimg.cn")
                || normalizedHost.endsWith(".kugou.com")
                || normalizedHost.equals("kugou.com");
    }
}
