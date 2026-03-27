package com.togethermusic.music.adapter;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.togethermusic.config.TogetherMusicProperties;
import com.togethermusic.music.model.Music;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 酷狗音乐适配器
 * 依赖酷狗音乐 API 服务（Node.js，配置在 together-music.music-api.kugou）
 *
 * 酷狗 API 核心概念：
 * - hash：歌曲唯一标识，搜索结果中返回，用于获取播放链接
 * - album_id：专辑 ID，与 hash 配合使用
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KuGouAdapter extends AbstractMusicAdapter {

    private final TogetherMusicProperties properties;

    @Override
    public String sourceCode() {
        return "kg";
    }

    @Override
    public Music search(String keyword, String quality, String userToken) {
        List<Music> songs = searchSongs(keyword, quality, userToken);
        if (!songs.isEmpty()) {
            return getById(songs.get(0).getId(), quality, userToken);
        }

        String baseUrl = properties.getMusicApi().getKugou();

        // 搜索接口：/search?keyword=xxx&page=1&pagesize=1
        Optional<JSONObject> firstResult = getWithRetry(
                baseUrl + "/search?keyword=" + encode(keyword) + "&page=1&pagesize=1",
                userToken,
                json -> {
                    if (!Integer.valueOf(1).equals(json.getInteger("status"))) return null;
                    JSONObject data = json.getJSONObject("data");
                    if (data == null) return null;
                    JSONArray lists = data.getJSONArray("lists");
                    if (lists == null || lists.isEmpty()) return null;
                    return lists.getJSONObject(0);
                }
        );

        if (firstResult.isEmpty()) return null;

        JSONObject item = firstResult.get();
        String hash = item.getString("FileHash");
        String albumId = item.getString("AlbumID");

        if (hash == null || hash.isBlank()) return null;

        Music music = buildFromSearchItem(item, quality);
        String playUrl = fetchPlayUrl(baseUrl, hash, albumId, quality, userToken);
        if (playUrl == null || playUrl.isBlank()) {
            log.warn("[kg] No playable URL for hash={}", hash);
            return null;
        }
        music.setUrl(playUrl);
        music.setId(hash); // 使用 hash 作为 ID
        return music;
    }

    @Override
    public List<Music> searchSongs(String keyword, String quality, String userToken) {
        String baseUrl = properties.getMusicApi().getKugou();
        List<Music> result = new ArrayList<>();

        getWithRetry(
                baseUrl + "/search?keyword=" + encode(keyword) + "&page=1&pagesize=12",
                userToken,
                json -> {
                    if (!Integer.valueOf(1).equals(json.getInteger("status"))) return null;
                    JSONObject data = json.getJSONObject("data");
                    if (data == null) return null;
                    JSONArray lists = data.getJSONArray("lists");
                    if (lists == null) return null;
                    for (int i = 0; i < lists.size(); i++) {
                        JSONObject item = lists.getJSONObject(i);
                        String hash = item.getString("FileHash");
                        if (hash == null || hash.isBlank()) continue;
                        Music music = buildFromSearchItem(item, quality);
                        music.setId(hash);
                        result.add(music);
                    }
                    return result;
                }
        );

        return result;
    }

    @Override
    public Music getById(String id, String quality, String userToken) {
        // id 即 hash
        String baseUrl = properties.getMusicApi().getKugou();

        // 通过 hash 获取歌曲详情
        Optional<Music> musicOpt = getWithRetry(
                baseUrl + "/song/detail?hash=" + id,
                userToken,
                json -> {
                    if (!Integer.valueOf(1).equals(json.getInteger("status"))) return null;
                    JSONObject data = json.getJSONObject("data");
                    if (data == null) return null;
                    return buildFromDetail(data, id, quality);
                }
        );

        if (musicOpt.isEmpty()) return null;
        Music music = musicOpt.get();

        String albumId = music.getMediaMid(); // 借用 mediaMid 字段暂存 albumId
        String playUrl = fetchPlayUrl(baseUrl, id, albumId, quality, userToken);
        if (playUrl == null || playUrl.isBlank()) {
            log.warn("[kg] No playable URL for hash={}", id);
            return null;
        }
        music.setUrl(playUrl);
        music.setMediaMid(null); // 清除临时字段
        return music;
    }

    @Override
    public List<Music> getPlaylist(String playlistId, String userToken) {
        // 酷狗歌单暂不支持，返回空列表
        log.info("[kg] Playlist fetch not supported yet for id={}", playlistId);
        return new ArrayList<>();
    }

    private Music buildFromSearchItem(JSONObject item, String quality) {
        Music music = new Music();
        music.setSource("kg");
        music.setQuality(quality != null ? quality : "320k");

        // 酷狗搜索结果字段：FileName = "歌手 - 歌名"
        String filename = item.getString("FileName");
        if (filename != null && filename.contains(" - ")) {
            String[] parts = filename.split(" - ", 2);
            music.setArtist(parts[0].trim());
            music.setName(parts[1].trim());
        } else {
            music.setName(filename != null ? filename : "未知歌曲");
            music.setArtist("未知艺术家");
        }

        long duration = item.getLongValue("Duration") * 1000;
        music.setDuration(duration);

        String imgUrl = item.getString("Image");
        if (imgUrl != null) {
            music.setPictureUrl(imgUrl.replace("{size}", "240"));
        }

        return music;
    }

    private Music buildFromDetail(JSONObject data, String hash, String quality) {
        Music music = new Music();
        music.setSource("kg");
        music.setId(hash);
        music.setQuality(quality != null ? quality : "320k");
        music.setName(data.getString("song_name"));
        music.setArtist(data.getString("author_name"));
        music.setDuration(data.getLongValue("duration") * 1000);
        music.setPictureUrl(data.getString("img"));
        music.setLyric(data.getString("lyrics"));
        // 暂存 album_id 供后续获取播放链接
        music.setMediaMid(data.getString("album_id"));
        return music;
    }

    private String fetchPlayUrl(String baseUrl, String hash, String albumId, String quality, String userToken) {
        String albumParam = albumId != null ? "&album_id=" + albumId : "";
        String url = baseUrl + "/song/url?hash=" + hash + albumParam + "&quality=" + qualityParam(quality);

        return getWithRetry(url, userToken, json -> {
            if (!Integer.valueOf(1).equals(json.getInteger("status"))) return null;
            JSONObject data = json.getJSONObject("data");
            if (data == null) return null;
            // 尝试多个字段名
            String playUrl = data.getString("play_url");
            if (playUrl == null) playUrl = data.getString("url");
            return playUrl;
        }).orElse(null);
    }

    private String qualityParam(String quality) {
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
