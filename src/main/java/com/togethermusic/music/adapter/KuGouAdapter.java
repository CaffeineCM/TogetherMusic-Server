package com.togethermusic.music.adapter;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.togethermusic.config.TogetherMusicProperties;
import com.togethermusic.music.dto.MusicPlaylistSummary;
import com.togethermusic.music.dto.MusicToplistSummary;
import com.togethermusic.music.model.Music;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
        return null;
    }

    @Override
    public List<Music> searchSongs(String keyword, String quality, String userToken) {
        String baseUrl = properties.getMusicApi().getKugou();
        List<Music> result = new ArrayList<>();

        getWithRetry(
                baseUrl + "/search?keywords=" + encode(keyword) + "&page=1&pagesize=30",
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
                        music.setMediaMid(firstNonBlank(item, "AlbumID", "album_id"));
                        result.add(music);
                    }
                    return result;
                }
        );

        return result;
    }

    @Override
    public Music getById(String id, String quality, String userToken) {
        String baseUrl = properties.getMusicApi().getKugou();
        Optional<JSONObject> audioItemOpt = getWithRetry(
                baseUrl + "/audio?hash=" + id,
                userToken,
                this::extractAudioItem
        );

        if (audioItemOpt.isEmpty()) {
            log.warn("[kg] No detail found for hash={}", id);
            return null;
        }
        JSONObject audioItem = audioItemOpt.get();
        Music music = buildFromAudioItem(audioItem, id, quality);

        String playUrl = fetchPlayUrl(
                baseUrl,
                buildPlayableHashCandidates(audioItem, id, quality),
                music.getMediaMid(),
                quality,
                userToken
        );
        if (playUrl == null || playUrl.isBlank()) {
            log.warn("[kg] No playable URL for hash={}, albumId={}", id, music.getMediaMid());
            return null;
        }
        music.setUrl(playUrl);
        music.setLyric(fetchLyric(baseUrl, audioItem, music.getName(), userToken));
        music.setMediaMid(null);
        return music;
    }

    @Override
    public List<Music> getPlaylist(String playlistId, String userToken) {
        if (playlistId != null && !playlistId.contains("collection_")) {
            return getToplistTracks(playlistId, userToken);
        }

        String baseUrl = properties.getMusicApi().getKugou();
        List<Music> result = new ArrayList<>();

        getWithRetry(baseUrl + "/playlist/track/all?id=" + encode(playlistId) + "&page=1&pagesize=200", userToken, json -> {
            JSONArray tracks = firstArray(json, "data");
            if (tracks == null) {
                JSONObject data = firstObject(json, "data");
                tracks = firstArray(data, "info", "songs", "list", "tracks");
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

    private List<Music> getToplistTracks(String rankId, String userToken) {
        String baseUrl = properties.getMusicApi().getKugou();
        List<Music> result = new ArrayList<>();

        getWithRetry(baseUrl + "/rank/audio?rankid=" + encode(rankId) + "&page=1&pagesize=200", userToken, json -> {
            JSONObject data = firstObject(json, "data");
            JSONArray tracks = firstArray(data, "songlist", "list", "songs");
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

        loadPlaylistSummaries(
                baseUrl + "/top/playlist?category_id=0&withsong=0&withtag=0&page=1&pagesize=24",
                userToken,
                result
        );
        enrichPlaylistTrackCounts(baseUrl, userToken, result);

        return result;
    }

    @Override
    public List<MusicPlaylistSummary> getUserPlaylists(String userToken) {
        if (userToken == null || userToken.isBlank()) {
            return List.of();
        }

        String baseUrl = properties.getMusicApi().getKugou();
        List<MusicPlaylistSummary> result = new ArrayList<>();

        getWithRetry(
                baseUrl + "/user/playlist?page=1&pagesize=50",
                userToken,
                json -> {
                    JSONArray items = extractPlaylistItems(json);
                    if (items == null) {
                        JSONObject data = firstObject(json, "data");
                        items = firstArray(data, "list", "info", "playlists");
                    }
                    if (items == null) return null;

                    for (int i = 0; i < items.size(); i++) {
                        MusicPlaylistSummary summary = toPlaylistSummary(items.getJSONObject(i));
                        if (summary != null) {
                            result.add(summary);
                        }
                    }
                    return result.isEmpty() ? null : result;
                }
        );

        enrichPlaylistTrackCounts(baseUrl, userToken, result);
        return result;
    }

    @Override
    public List<MusicToplistSummary> getToplists(String userToken) {
        String baseUrl = properties.getMusicApi().getKugou();
        List<MusicToplistSummary> result = new ArrayList<>();

        getWithRetry(baseUrl + "/rank/list", userToken, json -> {
            JSONArray items = extractToplistItems(json);
            if (items == null) return null;

            for (int i = 0; i < items.size(); i++) {
                JSONObject item = items.getJSONObject(i);
                MusicToplistSummary summary = toToplistSummary(item);
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

        JSONObject deprecated = firstObject(item, "deprecated");
        JSONObject audioInfo = firstObject(item, "audio_info");
        JSONObject albumInfo = firstObject(item, "album_info");
        JSONObject transParam = firstObject(item, "trans_param");
        JSONObject legacyAlbum = firstObject(item, "albuminfo");
        JSONArray authors = firstArray(item, "authors", "singerinfo");

        String hash = firstNonBlank(
                item,
                "hash",
                "FileHash",
                "EMixSongID");
        if (hash == null) {
            hash = firstNonBlank(deprecated, "hash");
        }
        if (hash == null) {
            hash = firstNonBlank(
                    audioInfo,
                    "hash_128",
                    "hash_320",
                    "hash_flac",
                    "hash_high",
                    "hash_super");
        }
        if (hash == null) {
            hash = firstNonBlank(item, "audio_id", "audioid");
        }
        if (hash == null) {
            return null;
        }

        Music music = new Music();
        music.setSource("kg");
        music.setId(hash);
        music.setQuality("320k");
        music.setName(firstNonBlank(item, "songname", "song_name", "audio_name", "name"));
        music.setArtist(firstNonBlank(item, "singername", "author_name", "artist"));
        if ((music.getArtist() == null || music.getArtist().isBlank()) && authors != null) {
            String artistField = item.containsKey("authors") ? "author_name" : "name";
            music.setArtist(joinArtists(authors, artistField));
        }
        music.setDuration(readDurationMs(item, audioInfo));
        music.setPictureUrl(firstNonBlank(item, "img", "image", "cover", "sizable_cover"));
        if (music.getPictureUrl() == null || music.getPictureUrl().isBlank()) {
            music.setPictureUrl(firstNonBlank(albumInfo, "sizable_cover", "cover"));
        }
        if (music.getPictureUrl() == null || music.getPictureUrl().isBlank()) {
            music.setPictureUrl(firstNonBlank(legacyAlbum, "cover"));
        }
        if (music.getPictureUrl() == null || music.getPictureUrl().isBlank()) {
            music.setPictureUrl(firstNonBlank(transParam, "union_cover"));
        }
        music.setMediaMid(firstNonBlank(item, "album_id", "albumid", "AlbumID"));
        if (music.getMediaMid() == null || music.getMediaMid().isBlank()) {
            music.setMediaMid(firstNonBlank(legacyAlbum, "id"));
        }

        if ((music.getArtist() == null || music.getArtist().isBlank())
                && item.getString("filename") != null
                && item.getString("filename").contains(" - ")) {
            String[] parts = item.getString("filename").split(" - ", 2);
            music.setArtist(parts[0].trim());
            if (music.getName() == null || music.getName().isBlank()) {
                music.setName(parts[1].trim());
            }
        }

        if ((music.getArtist() == null || music.getArtist().isBlank())
                && music.getName() != null
                && music.getName().contains(" - ")) {
            String[] parts = music.getName().split(" - ", 2);
            music.setArtist(parts[0].trim());
            music.setName(parts[1].trim());
        }

        if (music.getName() == null || music.getName().isBlank()) {
            music.setName("未知歌曲");
        }
        if (music.getArtist() == null || music.getArtist().isBlank()) {
            music.setArtist("未知艺术家");
        }
        if (music.getPictureUrl() != null) {
            music.setPictureUrl(normalizeImageUrl(music.getPictureUrl()));
        }

        return music;
    }

    private JSONObject extractAudioItem(JSONObject json) {
        if (!Integer.valueOf(1).equals(json.getInteger("status"))) {
            return null;
        }
        return firstAudioItem(json);
    }

    private Music buildFromAudioItem(JSONObject item, String hash, String quality) {
        if (item == null) {
            return null;
        }

        Music music = new Music();
        music.setSource("kg");
        music.setId(hash);
        music.setQuality(quality != null ? quality : "320k");
        music.setName(firstNonBlank(item, "audio_name", "song_name", "name"));
        music.setArtist(firstNonBlank(item, "author_name", "singername", "author_name_original"));
        music.setDuration(readDurationMs(item));
        music.setPictureUrl(normalizeImageUrl(firstNonBlank(item, "img", "image", "cover", "sizable_cover")));
        music.setLyric(firstNonBlank(item, "lyrics", "lyric"));
        music.setMediaMid(firstNonBlank(item, "album_id", "albumid", "AlbumID"));

        if ((music.getArtist() == null || music.getArtist().isBlank())
                && item.getString("filename") != null
                && item.getString("filename").contains(" - ")) {
            String[] parts = item.getString("filename").split(" - ", 2);
            music.setArtist(parts[0].trim());
            if (music.getName() == null || music.getName().isBlank()) {
                music.setName(parts[1].trim());
            }
        }

        if ((music.getName() == null || music.getName().isBlank())
                && item.getString("audio_name") != null
                && item.getString("audio_name").contains(" - ")) {
            String[] parts = item.getString("audio_name").split(" - ", 2);
            music.setArtist(parts[0].trim());
            music.setName(parts[1].trim());
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

    private String fetchPlayUrl(String baseUrl, List<String> hashCandidates, String albumId, String quality, String userToken) {
        for (String hash : hashCandidates) {
            String playUrl = fetchPlayUrlLegacy(baseUrl, hash, albumId, quality, userToken);
            if (playUrl != null && !playUrl.isBlank()) {
                return playUrl;
            }
        }

        for (String hash : hashCandidates) {
            String playUrl = fetchPlayUrlNew(baseUrl, hash, quality, userToken);
            if (playUrl != null && !playUrl.isBlank()) {
                return playUrl;
            }
        }

        return null;
    }

    private String fetchPlayUrlLegacy(String baseUrl, String hash, String albumId, String quality, String userToken) {
        String albumParam = albumId != null && !albumId.isBlank() ? "&album_id=" + albumId : "";
        String url = baseUrl + "/song/url?hash=" + hash + albumParam + "&quality=" + qualityParam(quality);

        return getWithRetry(url, userToken, json -> {
            if (!Integer.valueOf(1).equals(json.getInteger("status"))) return null;
            JSONObject data = json.getJSONObject("data");
            if (data == null) return null;
            String playUrl = data.getString("play_url");
            if (playUrl == null) playUrl = data.getString("url");
            return playUrl;
        }).orElse(null);
    }

    private String fetchPlayUrlNew(String baseUrl, String hash, String quality, String userToken) {
        return getWithRetry(
                baseUrl + "/song/url/new?hash=" + hash,
                userToken,
                json -> extractPlayUrlFromNewResponse(json, quality, hash)
        ).orElse(null);
    }

    private List<String> buildPlayableHashCandidates(JSONObject item, String requestedHash, String quality) {
        Set<String> candidates = new LinkedHashSet<>();

        addIfPresent(candidates, requestedHash);
        addIfPresent(candidates, firstNonBlank(item, "hash"));

        String normalizedQuality = quality != null ? quality : "320k";
        switch (normalizedQuality) {
            case "flac" -> {
                addIfPresent(candidates, firstNonBlank(item, "hash_flac"));
                addIfPresent(candidates, firstNonBlank(item, "hash_high"));
                addIfPresent(candidates, firstNonBlank(item, "hash_320"));
            }
            case "128k" -> addIfPresent(candidates, firstNonBlank(item, "hash_128"));
            default -> {
                addIfPresent(candidates, firstNonBlank(item, "hash_320"));
                addIfPresent(candidates, firstNonBlank(item, "hash_high"));
            }
        }

        addIfPresent(candidates, firstNonBlank(item, "hash_128"));
        addIfPresent(candidates, firstNonBlank(item, "hash_flac"));
        addIfPresent(candidates, firstNonBlank(item, "hash_high"));

        return new ArrayList<>(candidates);
    }

    private String fetchLyric(String baseUrl, JSONObject audioItem, String keyword, String userToken) {
        JSONObject candidate = searchLyricCandidate(baseUrl, audioItem, keyword, userToken);
        if (candidate == null) {
            return "";
        }

        String id = firstNonBlank(candidate, "id");
        String accessKey = firstNonBlank(candidate, "accesskey", "accessKey");
        if (id == null || accessKey == null) {
            return "";
        }

        return getWithRetry(
                baseUrl + "/lyric?id=" + encode(id) + "&accesskey=" + encode(accessKey) + "&fmt=lrc&decode=true",
                userToken,
                KuGouAdapter::extractLyricContent
        ).orElse("");
    }

    private JSONObject searchLyricCandidate(String baseUrl, JSONObject audioItem, String keyword, String userToken) {
        List<String> lyricSearchUrls = new ArrayList<>();
        String hash = firstNonBlank(audioItem, "hash", "hash_128", "hash_320", "hash_flac", "hash_high");
        if (hash != null && !hash.isBlank()) {
            lyricSearchUrls.add(baseUrl + "/search/lyric?hash=" + encode(hash));
        }
        if (keyword != null && !keyword.isBlank()) {
            lyricSearchUrls.add(baseUrl + "/search/lyric?keywords=" + encode(keyword));
        }

        for (String url : lyricSearchUrls) {
            Optional<JSONObject> candidate = getWithRetry(url, userToken, KuGouAdapter::extractLyricCandidate);
            if (candidate.isPresent()) {
                return candidate.get();
            }
        }

        return null;
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
            JSONArray items = extractPlaylistItems(json);
            if (items == null) return null;

            for (int i = 0; i < items.size(); i++) {
                MusicPlaylistSummary summary = toPlaylistSummary(items.getJSONObject(i));
                if (summary != null) {
                    result.add(summary);
                }
            }
            return result.isEmpty() ? null : Boolean.TRUE;
        }).orElse(false);
    }

    private void enrichPlaylistTrackCounts(String baseUrl, String userToken, List<MusicPlaylistSummary> result) {
        List<String> ids = result.stream()
                .filter(summary -> summary != null && summary.id() != null && !summary.id().isBlank())
                .map(MusicPlaylistSummary::id)
                .toList();
        if (ids.isEmpty()) {
            return;
        }

        Map<String, Long> trackCounts = getWithRetry(
                baseUrl + "/playlist/detail?ids=" + encode(String.join(",", ids)),
                userToken,
                KuGouAdapter::extractPlaylistTrackCounts
        ).orElse(Map.of());

        if (trackCounts.isEmpty()) {
            return;
        }

        for (int i = 0; i < result.size(); i++) {
            MusicPlaylistSummary summary = result.get(i);
            Long trackCount = trackCounts.get(summary.id());
            if (trackCount == null || trackCount <= 0) {
                continue;
            }

            result.set(i, MusicPlaylistSummary.builder()
                    .id(summary.id())
                    .name(summary.name())
                    .coverUrl(summary.coverUrl())
                    .creatorName(summary.creatorName())
                    .trackCount(trackCount)
                    .playCount(summary.playCount())
                    .description(summary.description())
                    .source(summary.source())
                    .build());
        }
    }

    static JSONArray extractPlaylistItems(JSONObject json) {
        JSONArray items = firstArray(json, "data");
        if (items != null) {
            return items;
        }

        JSONObject data = firstObject(json, "data");
        if (data == null) {
            return null;
        }

        return firstArray(data,
                "special_list",
                "specialList",
                "info",
                "cards",
                "list",
                "playlists");
    }

    static JSONArray extractToplistItems(JSONObject json) {
        JSONArray items = firstArray(json, "data");
        if (items != null) {
            return items;
        }

        JSONObject data = firstObject(json, "data");
        if (data == null) {
            return null;
        }

        return firstArray(data, "info", "list");
    }

    static MusicPlaylistSummary toPlaylistSummary(JSONObject item) {
        if (item == null) {
            return null;
        }

        String id = firstNonBlank(item, "global_collection_id", "playlist_id", "id", "specialid");
        String name = firstNonBlank(item, "name", "specialname", "title");
        if (id == null || name == null) {
            return null;
        }

        return MusicPlaylistSummary.builder()
                .id(id)
                .name(name)
                .coverUrl(normalizeImageUrl(firstNonBlank(
                        item,
                        "imgurl",
                        "flexible_cover",
                        "cover",
                        "pic",
                        "image",
                        "banner_imgurl")))
                .creatorName(firstNonBlank(item, "nickname", "username", "author_name"))
                .trackCount(readLong(item, "songcount", "song_count", "trackCount", "count"))
                .playCount(readLong(item, "play_count", "playCount", "heat", "collectcount"))
                .description(firstNonBlank(item, "intro", "description"))
                .source("kg")
                .build();
    }

    static MusicToplistSummary toToplistSummary(JSONObject item) {
        if (item == null) {
            return null;
        }

        String id = firstNonBlank(item, "rankid", "id", "specialid", "global_collection_id");
        String name = firstNonBlank(item, "name", "title", "specialname", "rankname");
        if (id == null || name == null) {
            return null;
        }

        return MusicToplistSummary.builder()
                .id(id)
                .name(name)
                .coverUrl(normalizeImageUrl(firstNonBlank(
                        item,
                        "imgurl",
                        "img_cover",
                        "album_img_9",
                        "cover",
                        "pic",
                        "image")))
                .description(firstNonBlank(item, "intro", "description", "rank_features"))
                .updateFrequency(firstNonBlank(item, "update_frequency", "updateFrequency", "publish"))
                .source("kg")
                .build();
    }

    static Map<String, Long> extractPlaylistTrackCounts(JSONObject json) {
        JSONArray data = json.getJSONArray("data");
        if (data == null || data.isEmpty()) {
            return Map.of();
        }

        Map<String, Long> result = new java.util.HashMap<>();
        for (int i = 0; i < data.size(); i++) {
            JSONObject item = data.getJSONObject(i);
            if (item == null) {
                continue;
            }
            String id = firstNonBlank(item, "global_collection_id", "parent_global_collection_id", "list_create_gid");
            Long count = readLong(item, "count", "songcount", "trackCount");
            if (id != null && count != null) {
                result.put(id, count);
            }
        }
        return result;
    }

    static String extractPlayUrlFromNewResponse(JSONObject json, String quality, String requestedHash) {
        JSONArray data = json.getJSONArray("data");
        if (data == null || data.isEmpty()) {
            return null;
        }

        List<JSONObject> candidates = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            collectPlayableCandidates(data.getJSONObject(i), candidates);
        }

        for (String preferredQuality : qualityPreferences(quality)) {
            String url = firstTrackerUrl(candidates, preferredQuality, requestedHash);
            if (url != null) {
                return url;
            }
        }

        return firstTrackerUrl(candidates, null, requestedHash);
    }

    static JSONObject extractLyricCandidate(JSONObject json) {
        if (json == null) {
            return null;
        }

        JSONArray candidates = firstArray(json, "candidates", "data");
        if (candidates != null && !candidates.isEmpty()) {
            return candidates.getJSONObject(0);
        }

        JSONObject data = firstObject(json, "data");
        if (data != null) {
            JSONArray nestedCandidates = firstArray(data, "candidates", "candidate", "info");
            if (nestedCandidates != null && !nestedCandidates.isEmpty()) {
                return nestedCandidates.getJSONObject(0);
            }
        }

        return null;
    }

    static String extractLyricContent(JSONObject json) {
        if (json == null) {
            return "";
        }

        String decoded = firstNonBlank(json, "decodeContent");
        if (decoded != null) {
            return decoded;
        }

        JSONObject data = firstObject(json, "data");
        decoded = firstNonBlank(data, "decodeContent", "lyric", "content");
        return decoded != null ? decoded : "";
    }

    private JSONObject firstAudioItem(JSONObject json) {
        if (json == null) {
            return null;
        }
        Object data = json.get("data");
        if (data instanceof JSONObject object) {
            JSONArray info = firstArray(object, "info", "data", "list");
            if (info != null && !info.isEmpty()) {
                return info.getJSONObject(0);
            }
            return object;
        }
        if (data instanceof JSONArray array && !array.isEmpty()) {
            return array.getJSONObject(0);
        }
        return null;
    }

    private static JSONObject firstObject(JSONObject source, String... keys) {
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

    private static JSONArray firstArray(JSONObject source, String... keys) {
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

    private static String firstNonBlank(JSONObject object, String... keys) {
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

    private static String normalizeImageUrl(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value.replace("{size}", "240");
    }

    private static Long readLong(JSONObject object, String... keys) {
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

    private static void collectPlayableCandidates(JSONObject item, List<JSONObject> result) {
        if (item == null) {
            return;
        }
        result.add(item);

        JSONArray related = item.getJSONArray("relate_goods");
        if (related == null) {
            return;
        }
        for (int i = 0; i < related.size(); i++) {
            collectPlayableCandidates(related.getJSONObject(i), result);
        }
    }

    private static String firstTrackerUrl(List<JSONObject> candidates, String preferredQuality, String requestedHash) {
        for (JSONObject candidate : candidates) {
            if (preferredQuality != null && !preferredQuality.equalsIgnoreCase(candidate.getString("quality"))) {
                continue;
            }
            JSONArray urls = trackerUrls(candidate);
            if (urls == null || urls.isEmpty()) {
                continue;
            }
            if (requestedHash != null && requestedHash.equalsIgnoreCase(candidate.getString("hash"))) {
                return urls.getString(0);
            }
        }

        for (JSONObject candidate : candidates) {
            if (preferredQuality != null && !preferredQuality.equalsIgnoreCase(candidate.getString("quality"))) {
                continue;
            }
            JSONArray urls = trackerUrls(candidate);
            if (urls != null && !urls.isEmpty()) {
                return urls.getString(0);
            }
        }

        return null;
    }

    private static JSONArray trackerUrls(JSONObject candidate) {
        JSONObject info = firstObject(candidate, "info");
        if (info == null) {
            return null;
        }
        return info.getJSONArray("tracker_url");
    }

    private static List<String> qualityPreferences(String quality) {
        return switch (quality != null ? quality : "320k") {
            case "flac" -> List.of("flac", "high", "320", "128");
            case "128k" -> List.of("128");
            default -> List.of("320", "128");
        };
    }

    private static void addIfPresent(Set<String> target, String value) {
        if (value != null && !value.isBlank()) {
            target.add(value);
        }
    }

    private long readDurationMs(JSONObject... objects) {
        Long duration = null;
        for (JSONObject object : objects) {
            duration = readLong(object,
                    "duration",
                    "Duration",
                    "timelength",
                    "timelen",
                    "duration_128",
                    "duration_320",
                    "duration_flac",
                    "duration_high");
            if (duration != null) {
                break;
            }
        }
        if (duration == null) {
            return 0L;
        }
        return duration > 1000 ? duration : duration * 1000;
    }
}
