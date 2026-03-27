package com.togethermusic.music.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * 统一音乐数据模型，所有音乐源适配器均返回此对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Music implements Serializable {

    /** 音乐源内部 ID */
    private String id;

    /** 歌曲名 */
    private String name;

    /** 艺术家 */
    private String artist;

    /** 时长（毫秒） */
    private Long duration;

    /** 播放链接 */
    private String url;

    /** 歌词 */
    private String lyric;

    /** 封面图 URL */
    private String pictureUrl;

    /**
     * 来源标识：wy / qq / kg / upload
     */
    @Builder.Default
    private String source = "wy";

    /** 音质：128k / 320k / flac */
    @Builder.Default
    private String quality = "320k";

    /** 点歌时间戳（毫秒），用于排序 */
    private long pickTime;

    /** 推送时间戳（毫秒），用于客户端计算播放进度 */
    private Long pushTime;

    /** 播放链接过期时间戳（毫秒），到期前需刷新 */
    private Long urlExpireTime;

    /** 点赞用户 ID 集合，用于点赞模式排序 */
    @Builder.Default
    private Set<String> likedUserIds = new HashSet<>();

    /** 最后点赞时间（毫秒），点赞数相同时的次级排序依据 */
    private Long likeTime;

    /** 置顶时间（毫秒），不为 null 时优先排在队首 */
    private Long topTime;

    /** 点歌者 sessionId */
    private String pickedBy;

    /** 额外字段：QQ 音乐 mediaMid，用于获取播放链接 */
    private String mediaMid;
}
