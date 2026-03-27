package com.togethermusic.music.adapter;

import com.togethermusic.music.model.Music;
import com.togethermusic.music.dto.MusicPlaylistSummary;
import com.togethermusic.music.dto.MusicToplistSummary;

import java.util.List;

/**
 * 音乐源适配器接口
 * 所有音乐平台实现此接口，统一返回 Music 对象
 */
public interface MusicSourceAdapter {

    /**
     * 按关键词搜索，返回第一条有效结果
     * @param keyword 搜索关键词
     * @param quality 音质：128k/320k/flac
     * @param userToken 用户绑定的平台 Token，为 null 时使用系统默认
     */
    Music search(String keyword, String quality, String userToken);

    /**
     * 按关键词搜索候选列表
     * @param keyword 搜索关键词
     * @param quality 音质：128k/320k/flac
     * @param userToken 用户绑定的平台 Token，为 null 时使用系统默认
     */
    default List<Music> searchSongs(String keyword, String quality, String userToken) {
        Music result = search(keyword, quality, userToken);
        return result != null ? List.of(result) : List.of();
    }

    /**
     * 按平台内部 ID 获取歌曲详情（含播放链接）
     * @param id 歌曲ID
     * @param quality 音质：128k/320k/flac
     * @param userToken 用户绑定的平台 Token，为 null 时使用系统默认
     */
    Music getById(String id, String quality, String userToken);

    /**
     * 获取歌单内容
     * @param playlistId 歌单ID
     * @param userToken 用户绑定的平台 Token，为 null 时使用系统默认
     */
    List<Music> getPlaylist(String playlistId, String userToken);

    /**
     * 获取公开歌单列表
     */
    default List<MusicPlaylistSummary> getRecommendedPlaylists(String userToken) {
        return List.of();
    }

    /**
     * 获取当前账号收藏/订阅歌单，仅对持有对应 token 的用户开放
     */
    default List<MusicPlaylistSummary> getUserPlaylists(String userToken) {
        return List.of();
    }

    /**
     * 获取排行榜列表
     */
    default List<MusicToplistSummary> getToplists(String userToken) {
        return List.of();
    }

    /**
     * 适配器标识，对应 Music.source 字段
     */
    String sourceCode();

    /**
     * 验证 Token 是否有效
     * @param userToken 用户 Token
     * @return true 表示有效
     */
    default boolean validateToken(String userToken) {
        // 默认实现：非空即有效，子类可覆盖
        return userToken != null && !userToken.isBlank();
    }

    // ========== 向后兼容的默认方法 ==========

    /**
     * 按关键词搜索（使用系统默认 Token）
     */
    default Music search(String keyword, String quality) {
        return search(keyword, quality, null);
    }

    /**
     * 按 ID 获取歌曲（使用系统默认 Token）
     */
    default Music getById(String id, String quality) {
        return getById(id, quality, null);
    }

    default List<Music> searchSongs(String keyword, String quality) {
        return searchSongs(keyword, quality, null);
    }

    /**
     * 获取歌单（使用系统默认 Token）
     */
    default List<Music> getPlaylist(String playlistId) {
        return getPlaylist(playlistId, null);
    }

    default List<MusicPlaylistSummary> getRecommendedPlaylists() {
        return getRecommendedPlaylists(null);
    }

    default List<MusicPlaylistSummary> getUserPlaylists() {
        return getUserPlaylists(null);
    }

    default List<MusicToplistSummary> getToplists() {
        return getToplists(null);
    }
}
