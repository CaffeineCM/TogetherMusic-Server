package com.togethermusic.music.adapter;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.togethermusic.config.TogetherMusicProperties;
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
                baseUrl + "/search?keyword=" + encode(keyword) + "&page=1&pagesize=30",
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
        String baseUrl = properties.getMusicApi().getKugou();
        List<Music> result = new ArrayList<>();

        getWithRetry(baseUrl + "/playlist/detail?id=" + encode(playlistId), userToken, json -> {
            JSONObject data = firstObject(json, "data", "playlist");
            JSONArray tracks = firstArray(data, "songs", "list", "tracks");
            if (tracks == null) {
                tracks = firstArray(json, "songs", "list", "tracks", "data");
            }
            if (tracks == null) return null;

            for (int i = 0; i < tracks.size(); i++) {
                Music music = buildFromPlaylistItem(tracks.getJSONObject(i));
                if (music != null) {
                    result.add(music);
                }
            }
            return result;
        });

        return result;
    }

    @Override
    public List<MusicPlaylistSummary> getRecommendedPlaylists(String userToken) {
        String baseUrl = properties.getMusicApi().getKugou();
        List<MusicPlaylistSummary> result = new ArrayList<>();

        List<String> candidateUrls = List.of(
                baseUrl + "/playlist/list?page=1&pagesize=24",
                baseUrl + "/playlist/index?page=1&pagesize=24",
                baseUrl + "/top/card"
        );

        for (String url : candidateUrls) {
            if (loadPlaylistSummaries(url, userToken, result)) {
                break;
            }
        }

        return result;
    }

    @Override
    public List<MusicToplistSummary> getToplists(String userToken) {
        String baseUrl = properties.getMusicApi().getKugou();
        List<MusicToplistSummary> result = new ArrayList<>();

        getWithRetry(baseUrl + "/top/card", userToken, json -> {
            JSONArray cards = firstArray(json, "data", "list");
            if (cards == null) {
                cards = firstArray(firstObject(json, "data"), "cards", "list");
            }
            if (cards == null) return null;

            for (int i = 0; i < cards.size(); i++) {
                JSONObject item = cards.getJSONObject(i);
                MusicToplistSummary summary = parseToplistSummary(item);
                if (summary != null) {
                    result.add(summary);
                }
            }
            return result;
        });

        return result;
    }

    @Override
    public boolean validateToken(String userToken) {
        return fetchAccountProfile(userToken).isPresent();
    }

    public Optional<String> getNickname(String userToken) {
        return fetchAccountProfile(userToken).map(profile -> profile.getString("nickname"))
                .filter(name -> name != null && !name.isBlank());
    }

    public Optional<String> getUserId(String userToken) {
        return fetchAccountProfile(userToken)
                .map(profile -> firstNonBlank(profile, "userid", "user_id", "uid"))
                .filter(id -> id != null && !id.isBlank());
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

    private Music buildFromPlaylistItem(JSONObject item) {
        if (item == null) {
            return null;
        }

        String hash = firstNonBlank(item,
                "hash", "audio_id", "audioid", "FileHash", "EMixSongID");
        if (hash == null) {
            return null;
        }

        Music music = new Music();
        music.setSource("kg");
        music.setId(hash);
        music.setQuality("320k");
        music.setName(firstNonBlank(item, "songname", "song_name", "name"));
        music.setArtist(firstNonBlank(item, "singername", "author_name", "artist", "filename"));
        music.setDuration(readDurationMs(item));
        music.setPictureUrl(firstNonBlank(item, "img", "image", "cover"));
        music.setMediaMid(firstNonBlank(item, "album_id", "albumid"));

        if ((music.getArtist() == null || music.getArtist().isBlank())
                && item.getString("filename") != null
                && item.getString("filename").contains(" - ")) {
            String[] parts = item.getString("filename").split(" - ", 2);
            music.setArtist(parts[0].trim());
            if (music.getName() == null || music.getName().isBlank()) {
                music.setName(parts[1].trim());
            }
        }

        if (music.getName() == null || music.getName().isBlank()) {
            music.setName("未知歌曲");
        }
        if (music.getArtist() == null || music.getArtist().isBlank()) {
            music.setArtist("未知艺术家");
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

    private Optional<JSONObject> fetchAccountProfile(String userToken) {
        if (userToken == null || userToken.isBlank()) {
            return Optional.empty();
        }
        String baseUrl = properties.getMusicApi().getKugou();
        return getWithRetry(baseUrl + "/user/detail", userToken, json -> {
            JSONObject data = firstObject(json, "data", "user");
            return data != null ? data : null;
        });
    }

    private boolean loadPlaylistSummaries(String url, String userToken, List<MusicPlaylistSummary> result) {
        return getWithRetry(url, userToken, json -> {
            JSONArray items = firstArray(json, "data", "list");
            if (items == null) {
                JSONObject data = firstObject(json, "data");
                items = firstArray(data, "info", "cards", "list", "playlists");
            }
            if (items == null) return null;

            for (int i = 0; i < items.size(); i++) {
                MusicPlaylistSummary summary = parsePlaylistSummary(items.getJSONObject(i));
                if (summary != null) {
                    result.add(summary);
                }
            }
            return result.isEmpty() ? null : Boolean.TRUE;
        }).orElse(false);
    }

    private MusicPlaylistSummary parsePlaylistSummary(JSONObject item) {
        if (item == null) {
            return null;
        }

        String id = firstNonBlank(item, "id", "specialid", "global_collection_id", "playlist_id");
        String name = firstNonBlank(item, "name", "specialname", "title");
        if (id == null || name == null) {
            return null;
        }

        return MusicPlaylistSummary.builder()
                .id(id)
                .name(name)
                .coverUrl(firstNonBlank(item, "imgurl", "cover", "pic", "image"))
                .creatorName(firstNonBlank(item, "nickname", "username", "author_name"))
                .trackCount(readLong(item, "songcount", "trackCount", "count"))
                .playCount(readLong(item, "play_count", "playCount", "heat"))
                .description(firstNonBlank(item, "intro", "description"))
                .source("kg")
                .build();
    }

    private MusicToplistSummary parseToplistSummary(JSONObject item) {
        if (item == null) {
            return null;
        }

        String id = firstNonBlank(item, "id", "rankid", "specialid", "global_collection_id");
        String name = firstNonBlank(item, "name", "title", "specialname");
        if (id == null || name == null) {
            return null;
        }

        return MusicToplistSummary.builder()
                .id(id)
                .name(name)
                .coverUrl(firstNonBlank(item, "imgurl", "cover", "pic", "image"))
                .description(firstNonBlank(item, "intro", "description"))
                .updateFrequency(firstNonBlank(item, "update_frequency", "updateFrequency"))
                .source("kg")
                .build();
    }

    private JSONObject firstObject(JSONObject source, String... keys) {
        if (source == null) {
            return null;
        }
        JSONObject current = source;
        for (String key : keys) {
            if (current == null) {
                return null;
            }
            Object value = current.get(key);
            if (value instanceof JSONObject next) {
                current = next;
            } else {
                return null;
            }
        }
        return current;
    }

    private JSONArray firstArray(JSONObject source, String... keys) {
        if (source == null) {
            return null;
        }
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof JSONArray array) {
                return array;
            }
            if (value instanceof JSONObject object) {
                JSONArray nested = firstArray(object, "list", "data", "items");
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private String firstNonBlank(JSONObject object, String... keys) {
        if (object == null) {
            return null;
        }
        for (String key : keys) {
            String value = object.getString(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private Long readLong(JSONObject object, String... keys) {
        if (object == null) {
            return null;
        }
        for (String key : keys) {
            Object value = object.get(key);
            if (value instanceof Number number) {
                return number.longValue();
            }
            if (value instanceof String text && !text.isBlank()) {
                try {
                    return Long.parseLong(text);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return null;
    }

    private long readDurationMs(JSONObject object) {
        Long duration = readLong(object, "duration", "Duration", "timelength");
        if (duration == null) {
            return 0L;
        }
        return duration > 1000 ? duration : duration * 1000;
    }
}
