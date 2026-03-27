package com.togethermusic.music.model;

/**
 * 房间运行时配置字段名常量
 * 对应 Redis Hash tm:config:{houseId} 的 field 名
 */
public final class RoomConfig {

    private RoomConfig() {}

    public static final String SEARCH_ENABLED = "searchEnabled";
    public static final String SWITCH_ENABLED = "switchEnabled";
    public static final String GOOD_MODEL = "goodModel";
    public static final String RANDOM_MODEL = "randomModel";
    public static final String MUSIC_CIRCLE = "musicCircle";
    public static final String LIST_CIRCLE = "listCircle";
    public static final String VOTE_RATE = "voteRate";
    public static final String VOLUME = "volume";
    public static final String LAST_PUSH_TIME = "lastPushTime";
    public static final String LAST_DURATION = "lastDuration";
    public static final String PUSH_SWITCH = "pushSwitch";
    public static final String PLAYBACK_STATUS = "playbackStatus";
    public static final String PLAYBACK_POSITION = "playbackPosition";
    public static final String PLAYBACK_UPDATED_AT = "playbackUpdatedAt";
}
