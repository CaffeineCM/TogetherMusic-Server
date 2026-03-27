package com.togethermusic.music.adapter;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.togethermusic.config.TogetherMusicProperties;
import com.togethermusic.music.model.Music;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * QQ 音乐适配器
 * 依赖 QQMusicApi（Node.js 服务）
 * API 响应 result=100 表示成功
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QQMusicAdapter extends AbstractMusicAdapter {

    private final TogetherMusicProperties properties;

    @Override
    public String sourceCode() {
        return "qq";
    }

    @Override
    public Music search(String keyword, String quality, String userToken) {
        List<Music> songs = searchSongs(keyword, quality, userToken);
        if (!songs.isEmpty()) {
            return getById(songs.get(0).getId(), quality, userToken);
        }

        String baseUrl = properties.getMusicApi().getQq();
        String url = baseUrl + "/search?key=" + encode(keyword) + "&pageNo=1&pageSize=1";

        Optional<String> songMid = getWithRetry(url, userToken, json -> {
            if (!Integer.valueOf(100).equals(json.getInteger("result"))) return null;
            JSONArray list = json.getJSONObject("data").getJSONArray("list");
            if (list == null || list.isEmpty()) return null;
            return list.getJSONObject(0).getString("songmid");
        });

        return songMid.map(mid -> getById(mid, quality, userToken)).orElse(null);
    }

    @Override
    public List<Music> searchSongs(String keyword, String quality, String userToken) {
        String baseUrl = properties.getMusicApi().getQq();
        String url = baseUrl + "/search?key=" + encode(keyword) + "&pageNo=1&pageSize=12";
        List<Music> result = new ArrayList<>();

        getWithRetry(url, userToken, json -> {
            if (!Integer.valueOf(100).equals(json.getInteger("result"))) return null;
            JSONArray list = json.getJSONObject("data").getJSONArray("list");
            if (list == null || list.isEmpty()) return null;
            for (int i = 0; i < list.size(); i++) {
                JSONObject item = list.getJSONObject(i);
                String songMid = item.getString("songmid");
                if (songMid == null) continue;
                Music music = new Music();
                music.setSource("qq");
                music.setId(songMid);
                music.setName(item.getString("songname"));
                music.setArtist(joinArtists(item.getJSONArray("singer"), "name"));
                music.setDuration(item.getLongValue("interval") * 1000);
                String albumMid = item.getString("albummid");
                if (albumMid != null && !albumMid.isBlank()) {
                    music.setPictureUrl("https://y.gtimg.cn/music/photo_new/T002R300x300M000" + albumMid + ".jpg");
                }
                result.add(music);
            }
            return result;
        });

        return result;
    }

    @Override
    public Music getById(String id, String quality, String userToken) {
        String baseUrl = properties.getMusicApi().getQq();

        Optional<Music> musicOpt = getWithRetry(
                baseUrl + "/song?songmid=" + id,
                userToken,
                json -> {
                    if (!Integer.valueOf(100).equals(json.getInteger("result"))) return null;
                    JSONObject data = json.getJSONObject("data");
                    if (data == null) return null;
                    return parseSong(data, id, quality, userToken);
                }
        );

        if (musicOpt.isEmpty()) return null;
        Music music = musicOpt.get();

        // 获取歌词
        music.setLyric(fetchLyric(baseUrl, id, userToken));
        return music;
    }

    @Override
    public List<Music> getPlaylist(String playlistId, String userToken) {
        String baseUrl = properties.getMusicApi().getQq();
        List<Music> result = new ArrayList<>();

        getWithRetry(baseUrl + "/playlist?id=" + playlistId, userToken, json -> {
            if (!Integer.valueOf(100).equals(json.getInteger("result"))) return null;
            JSONArray list = json.getJSONObject("data").getJSONArray("list");
            if (list == null) return null;
            for (int i = 0; i < list.size(); i++) {
                JSONObject item = list.getJSONObject(i);
                String mid = item.getString("songmid");
                if (mid != null) {
                    Music m = getById(mid, "320k", userToken);
                    if (m != null) result.add(m);
                }
            }
            return result;
        });

        return result;
    }

    private Music parseSong(JSONObject data, String id, String quality, String userToken) {
        JSONObject trackInfo = data.getJSONObject("track_info");
        if (trackInfo == null) return null;

        Music music = new Music();
        music.setSource("qq");
        music.setId(id);
        music.setName(trackInfo.getString("name"));
        music.setArtist(joinArtists(trackInfo.getJSONArray("singer"), "name"));
        music.setDuration(trackInfo.getLong("interval") * 1000);

        JSONObject file = trackInfo.getJSONObject("file");
        if (file != null) {
            music.setMediaMid(file.getString("media_mid"));
        }

        JSONObject album = trackInfo.getJSONObject("album");
        if (album != null) {
            String albummid = album.getString("mid");
            String picUrl = "https://y.gtimg.cn/music/photo_new/T002R300x300M000" + albummid + ".jpg";
            music.setPictureUrl(picUrl);
        }

        // 获取播放链接
        String playUrl = fetchPlayUrl(data, music.getName(), music.getArtist(), quality, userToken);
        if (playUrl == null || playUrl.isBlank()) {
            log.warn("[qq] No playable URL for id={}", id);
            return null;
        }
        music.setUrl(playUrl);
        music.setQuality(quality);
        return music;
    }

    private String fetchPlayUrl(JSONObject data, String name, String artist, String quality, String userToken) {
        // QQ 音乐 API 有时直接在 song 响应里返回 url
        String directUrl = data.getString("url");
        if (directUrl != null && !directUrl.isBlank()) return directUrl;

        // 否则通过 /song/url 接口获取
        String baseUrl = properties.getMusicApi().getQq();
        JSONObject trackInfo = data.getJSONObject("track_info");
        if (trackInfo == null) return null;
        String songmid = trackInfo.getString("songmid");

        return getWithRetry(
                baseUrl + "/song/url?id=" + songmid + "&type=" + qualityType(quality),
                userToken,
                json -> {
                    if (!Integer.valueOf(100).equals(json.getInteger("result"))) return null;
                    return json.getJSONObject("data").getString("url");
                }
        ).orElse(null);
    }

    private String fetchLyric(String baseUrl, String id, String userToken) {
        return getWithRetry(
                baseUrl + "/lyric?songmid=" + id,
                userToken,
                json -> {
                    if (!Integer.valueOf(100).equals(json.getInteger("result"))) return "";
                    JSONObject lyricData = json.getJSONObject("data");
                    return lyricData != null ? lyricData.getString("lyric") : "";
                }
        ).orElse("");
    }

    private String qualityType(String quality) {
        return switch (quality != null ? quality : "320k") {
            case "flac" -> "flac";
            case "128k" -> "128";
            default -> "320";
        };
    }

    private String encode(String s) {
        try {
            return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }
}
