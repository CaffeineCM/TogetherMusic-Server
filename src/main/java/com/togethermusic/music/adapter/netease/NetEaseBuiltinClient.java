package com.togethermusic.music.adapter.netease;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.togethermusic.music.model.Music;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NetEaseBuiltinClient {

    private static final String MUSIC_HOST = "https://music.163.com";
    private static final String INTERFACE_HOST = "https://interface.music.163.com";

    private final NetEaseCrypto crypto;

    public String searchFirstSongId(String keyword) {
        HttpResponse<String> response = Unirest.post(MUSIC_HOST + "/api/search/get/web")
                .header("Referer", MUSIC_HOST)
                .header("Origin", MUSIC_HOST)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .field("s", keyword)
                .field("type", "1")
                .field("offset", "0")
                .field("limit", "1")
                .field("total", "true")
                .asString();
        if (response.getStatus() != 200) {
            return null;
        }

        JSONObject json = JSONObject.parseObject(response.getBody());
        if (!Integer.valueOf(200).equals(json.getInteger("code"))) {
            return null;
        }
        JSONObject result = json.getJSONObject("result");
        if (result == null || result.getIntValue("songCount") == 0) {
            return null;
        }
        JSONArray songs = result.getJSONArray("songs");
        return songs == null || songs.isEmpty() ? null : songs.getJSONObject(0).getString("id");
    }

    public Music getSongDetail(String id) {
        HttpResponse<String> response = Unirest.get(MUSIC_HOST + "/api/song/detail")
                .header("Referer", MUSIC_HOST)
                .queryString("ids", "[" + id + "]")
                .queryString("id", id)
                .asString();
        if (response.getStatus() != 200) {
            return null;
        }

        JSONObject json = JSONObject.parseObject(response.getBody());
        if (!Integer.valueOf(200).equals(json.getInteger("code"))) {
            return null;
        }
        JSONArray songs = json.getJSONArray("songs");
        if (songs == null || songs.isEmpty()) {
            return null;
        }
        JSONObject song = songs.getJSONObject(0);

        Music music = new Music();
        music.setSource("wy");
        music.setId(id);
        music.setName(song.getString("name"));
        music.setArtist(joinArtists(song.getJSONArray("artists"), "name"));
        music.setDuration(song.getLong("duration"));

        JSONObject album = song.getJSONObject("album");
        if (album != null) {
            music.setPictureUrl(album.getString("picUrl"));
        }
        return music;
    }

    public String getLyric(String id) {
        HttpResponse<String> response = Unirest.get(MUSIC_HOST + "/api/song/lyric")
                .header("Referer", MUSIC_HOST)
                .queryString("id", id)
                .queryString("lv", 1)
                .queryString("kv", 1)
                .queryString("tv", -1)
                .asString();
        if (response.getStatus() != 200) {
            return "";
        }

        JSONObject json = JSONObject.parseObject(response.getBody());
        if (!Integer.valueOf(200).equals(json.getInteger("code"))) {
            return "";
        }
        JSONObject lrc = json.getJSONObject("lrc");
        return lrc != null ? lrc.getString("lyric") : "";
    }

    public String getTrackUrl(String id, String quality, String cookie) {
        String level = switch (quality != null ? quality : "320k") {
            case "flac" -> "lossless";
            case "128k" -> "standard";
            default -> "exhigh";
        };

        String path = "/api/song/enhance/player/url/v1";
        String body = "{'ids':['" + id + "'],'level':'" + level + "','encodeType':'flac'}";
        String params = crypto.eapiEncrypt(path, body);

        var request = Unirest.post(INTERFACE_HOST + "/eapi/song/enhance/player/url/v1")
                .header("Referer", MUSIC_HOST)
                .header("Origin", MUSIC_HOST)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .field("params", params);
        if (cookie != null && !cookie.isBlank()) {
            request.header("Cookie", cookie);
        }

        HttpResponse<String> response = request.asString();
        if (response.getStatus() != 200) {
            return null;
        }

        JSONObject json = JSONObject.parseObject(response.getBody());
        JSONArray data = json.getJSONArray("data");
        if (data == null || data.isEmpty()) {
            return null;
        }
        return data.getJSONObject(0).getString("url");
    }

    public List<Music> getPlaylist(String playlistId) {
        List<Music> result = new ArrayList<>();
        HttpResponse<String> response = Unirest.get(MUSIC_HOST + "/api/v6/playlist/detail")
                .header("Referer", MUSIC_HOST)
                .queryString("id", playlistId)
                .queryString("n", 1000)
                .asString();
        if (response.getStatus() != 200) {
            return result;
        }

        JSONObject json = JSONObject.parseObject(response.getBody());
        JSONObject playlist = json.getJSONObject("playlist");
        if (playlist == null) {
            return result;
        }
        JSONArray tracks = playlist.getJSONArray("tracks");
        if (tracks == null) {
            return result;
        }

        for (int i = 0; i < tracks.size(); i++) {
            JSONObject song = tracks.getJSONObject(i);
            String id = song.getString("id");
            if (id == null) {
                continue;
            }
            Music music = new Music();
            music.setSource("wy");
            music.setId(id);
            music.setName(song.getString("name"));
            music.setArtist(joinArtists(song.getJSONArray("ar"), "name"));
            music.setDuration(song.getLong("dt"));
            JSONObject album = song.getJSONObject("al");
            if (album != null) {
                music.setPictureUrl(album.getString("picUrl"));
            }
            result.add(music);
        }
        return result;
    }

    private String joinArtists(JSONArray artists, String nameField) {
        if (artists == null || artists.isEmpty()) {
            return "未知艺术家";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < artists.size(); i++) {
            if (i > 0) {
                sb.append("&");
            }
            sb.append(artists.getJSONObject(i).getString(nameField));
        }
        return sb.toString();
    }
}
