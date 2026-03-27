package com.togethermusic.music.adapter;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.togethermusic.config.TogetherMusicProperties;
import com.togethermusic.music.adapter.netease.NetEaseBuiltinClient;
import com.togethermusic.music.dto.MusicPlaylistSummary;
import com.togethermusic.music.dto.MusicToplistSummary;
import com.togethermusic.music.model.Music;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 网易云音乐适配器
 * 依赖 NeteaseCloudMusicApi（Node.js 服务）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NetEaseAdapter extends AbstractMusicAdapter {

    private final TogetherMusicProperties properties;
    private final NetEaseBuiltinClient builtinClient;

    @Override
    public String sourceCode() {
        return "wy";
    }

    @Override
    public Music search(String keyword, String quality, String userToken) {
        List<Music> songs = searchSongs(keyword, quality, userToken);
        if (!songs.isEmpty()) {
            return getById(songs.get(0).getId(), quality, userToken);
        }

        String fallbackId = builtinClient.searchFirstSongId(keyword);
        return fallbackId != null ? getById(fallbackId, quality, userToken) : null;
    }

    @Override
    public List<Music> searchSongs(String keyword, String quality, String userToken) {
        String baseUrl = properties.getMusicApi().getNetease();
        List<Music> result = new ArrayList<>();

        postWithCookieRetry(
                baseUrl + "/search",
                Map.of("keywords", keyword, "limit", 12, "offset", 0),
                userToken,
                json -> {
                    if (!Integer.valueOf(200).equals(json.getInteger("code"))) return null;
                    JSONObject searchResult = json.getJSONObject("result");
                    if (searchResult == null || searchResult.getIntValue("songCount") == 0) return null;
                    JSONArray songs = searchResult.getJSONArray("songs");
                    if (songs == null) return null;
                    for (int i = 0; i < songs.size(); i++) {
                        JSONObject song = songs.getJSONObject(i);
                        String id = song.getString("id");
                        if (id != null) {
                            result.add(parseSongDetail(song, id));
                        }
                    }
                    return result;
                }
        );

        return result;
    }

    @Override
    public Music getById(String id, String quality, String userToken) {
        String baseUrl = properties.getMusicApi().getNetease();
        String effectiveQuality = quality != null ? quality : "320k";

        // 获取歌曲详情
        Optional<Music> musicOpt = postWithCookieRetry(
                baseUrl + "/song/detail",
                Map.of("ids", id),
                userToken,
                json -> {
                    if (!Integer.valueOf(200).equals(json.getInteger("code"))) return null;
                    JSONArray songs = json.getJSONArray("songs");
                    if (songs == null || songs.isEmpty()) return null;
                    return parseSongDetail(songs.getJSONObject(0), id);
                }
        );

        Music music = musicOpt.orElseGet(() -> builtinClient.getSongDetail(id));
        if (music == null) return null;

        // 获取播放链接
        String url = fetchPlayUrl(baseUrl, id, effectiveQuality, userToken);
        if (url == null || url.isBlank()) {
            log.warn("[wy] No playable URL for id={}", id);
            return null;
        }
        music.setUrl(url);
        music.setQuality(effectiveQuality);

        // 获取歌词
        music.setLyric(fetchLyric(baseUrl, id, userToken));

        return music;
    }

    @Override
    public List<Music> getPlaylist(String playlistId, String userToken) {
        String baseUrl = properties.getMusicApi().getNetease();
        List<Music> result = new ArrayList<>();

        postWithCookieRetry(
                baseUrl + "/playlist/detail",
                Map.of("id", playlistId),
                userToken,
                json -> {
                    if (!Integer.valueOf(200).equals(json.getInteger("code"))) return null;
                    JSONObject playlist = json.getJSONObject("playlist");
                    if (playlist == null) return null;
                    JSONArray tracks = playlist.getJSONArray("tracks");
                    if (tracks == null) return null;
                    for (int i = 0; i < tracks.size(); i++) {
                        JSONObject track = tracks.getJSONObject(i);
                        String id = track.getString("id");
                        if (id != null) result.add(parseSongDetail(track, id));
                    }
                    return result;
                }
        );

        return result.isEmpty() ? builtinClient.getPlaylist(playlistId) : result;
    }

    @Override
    public List<MusicPlaylistSummary> getRecommendedPlaylists(String userToken) {
        String baseUrl = properties.getMusicApi().getNetease();
        List<MusicPlaylistSummary> result = new ArrayList<>();

        postWithCookieRetry(
                baseUrl + "/top/playlist",
                Map.of("cat", "全部", "order", "hot", "limit", 24, "offset", 0),
                userToken,
                json -> {
                    if (!Integer.valueOf(200).equals(json.getInteger("code"))) return null;
                    JSONArray playlists = json.getJSONArray("playlists");
                    if (playlists == null) return null;
                    for (int i = 0; i < playlists.size(); i++) {
                        MusicPlaylistSummary summary = parsePlaylistSummary(playlists.getJSONObject(i));
                        if (summary != null) {
                            result.add(summary);
                        }
                    }
                    return result;
                }
        );

        if (!result.isEmpty()) {
            return result;
        }

        postWithCookieRetry(
                baseUrl + "/personalized",
                Map.of("limit", 24),
                userToken,
                json -> {
                    if (!Integer.valueOf(200).equals(json.getInteger("code"))) return null;
                    JSONArray playlists = json.getJSONArray("result");
                    if (playlists == null) return null;
                    for (int i = 0; i < playlists.size(); i++) {
                        MusicPlaylistSummary summary = parsePlaylistSummary(playlists.getJSONObject(i));
                        if (summary != null) {
                            result.add(summary);
                        }
                    }
                    return result;
                }
        );

        return result;
    }

    @Override
    public List<MusicPlaylistSummary> getUserPlaylists(String userToken) {
        if (userToken == null || userToken.isBlank()) {
            return List.of();
        }

        String baseUrl = properties.getMusicApi().getNetease();
        String userId = postWithCookieRetry(
                baseUrl + "/login/status",
                Map.of(),
                userToken,
                json -> {
                    JSONObject data = json.getJSONObject("data");
                    JSONObject account = data != null ? data.getJSONObject("account") : null;
                    return account != null ? account.getString("id") : null;
                }
        ).orElse(null);

        if (userId == null || userId.isBlank()) {
            return List.of();
        }

        List<MusicPlaylistSummary> result = new ArrayList<>();
        postWithCookieRetry(
                baseUrl + "/user/playlist",
                Map.of("uid", userId, "limit", 50, "offset", 0),
                userToken,
                json -> {
                    if (!Integer.valueOf(200).equals(json.getInteger("code"))) return null;
                    JSONArray playlists = json.getJSONArray("playlist");
                    if (playlists == null) return null;
                    for (int i = 0; i < playlists.size(); i++) {
                        MusicPlaylistSummary summary = parsePlaylistSummary(playlists.getJSONObject(i));
                        if (summary != null) {
                            result.add(summary);
                        }
                    }
                    return result;
                }
        );
        return result;
    }

    @Override
    public List<MusicToplistSummary> getToplists(String userToken) {
        String baseUrl = properties.getMusicApi().getNetease();
        List<MusicToplistSummary> result = new ArrayList<>();

        postWithCookieRetry(
                baseUrl + "/toplist/detail",
                Map.of(),
                userToken,
                json -> {
                    JSONObject data = json.getJSONObject("data");
                    JSONArray lists = data != null ? data.getJSONArray("list") : json.getJSONArray("list");
                    if (lists == null) return null;
                    for (int i = 0; i < lists.size(); i++) {
                        JSONObject item = lists.getJSONObject(i);
                        String id = item.getString("id");
                        if (id == null) continue;
                        result.add(MusicToplistSummary.builder()
                                .id(id)
                                .name(item.getString("name"))
                                .coverUrl(item.getString("coverImgUrl"))
                                .description(item.getString("description"))
                                .updateFrequency(item.getString("updateFrequency"))
                                .source("wy")
                                .build());
                    }
                    return result;
                }
        );

        return result;
    }

    private Music parseSongDetail(JSONObject song, String id) {
        Music music = new Music();
        music.setSource("wy");
        music.setId(id);
        music.setName(song.getString("name"));
        music.setArtist(joinArtists(song.getJSONArray("ar"), "name"));
        music.setDuration(song.getLong("dt"));

        JSONObject al = song.getJSONObject("al");
        if (al != null) {
            music.setPictureUrl(al.getString("picUrl"));
        }
        return music;
    }

    private MusicPlaylistSummary parsePlaylistSummary(JSONObject playlist) {
        if (playlist == null) {
            return null;
        }
        JSONObject creator = playlist.getJSONObject("creator");
        return MusicPlaylistSummary.builder()
                .id(playlist.getString("id"))
                .name(playlist.getString("name"))
                .coverUrl(playlist.getString("coverImgUrl"))
                .creatorName(creator != null ? creator.getString("nickname") : null)
                .trackCount(playlist.getLong("trackCount"))
                .playCount(playlist.getLong("playCount"))
                .description(playlist.getString("description"))
                .source("wy")
                .build();
    }

    private String fetchPlayUrl(String baseUrl, String id, String quality, String userToken) {
        // br: 128000 / 320000 / 999000(flac)
        int br = switch (quality) {
            case "flac" -> 999000;
            case "128k" -> 128000;
            default -> 320000;
        };

        String url = postWithCookieRetry(
                baseUrl + "/song/url",
                Map.of("id", id, "br", br),
                userToken,
                json -> {
                    if (!Integer.valueOf(200).equals(json.getInteger("code"))) return null;
                    JSONArray data = json.getJSONArray("data");
                    if (data == null || data.isEmpty()) return null;
                    return data.getJSONObject(0).getString("url");
                }
        ).orElse(null);
        return url != null ? url : builtinClient.getTrackUrl(id, quality, userToken);
    }

    private String fetchLyric(String baseUrl, String id, String userToken) {
        String lyric = postWithCookieRetry(
                baseUrl + "/lyric",
                Map.of("id", id),
                userToken,
                json -> {
                    if (!Integer.valueOf(200).equals(json.getInteger("code"))) return "";
                    JSONObject lrc = json.getJSONObject("lrc");
                    return lrc != null ? lrc.getString("lyric") : "";
                }
        ).orElse("");
        return lyric != null && !lyric.isBlank() ? lyric : builtinClient.getLyric(id);
    }

    private <T> Optional<T> postWithCookieRetry(
            String url,
            Map<String, Object> params,
            String userToken,
            java.util.function.Function<JSONObject, T> parser
    ) {
        for (int i = 0; i < MAX_RETRY; i++) {
            try {
                var request = Unirest.post(url);
                params.forEach(request::queryString);
                if (userToken != null && !userToken.isBlank()) {
                    request.queryString("cookie", userToken);
                }
                HttpResponse<String> response = request.asString();
                if (response.getStatus() == 200) {
                    JSONObject json = JSONObject.parseObject(response.getBody());
                    T result = parser.apply(json);
                    if (result != null) {
                        return Optional.of(result);
                    }
                }
            } catch (Exception e) {
                log.warn("[{}] POST {} failed (attempt {}): {}", sourceCode(), url, i + 1, e.getMessage());
            }
        }
        return Optional.empty();
    }
}
