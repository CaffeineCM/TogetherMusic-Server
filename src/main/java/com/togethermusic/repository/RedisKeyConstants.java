package com.togethermusic.repository;

/**
 * Redis Key 命名规范
 * 全局前缀 tm:，房间相关以 {houseId} 作为命名空间
 */
public final class RedisKeyConstants {

    private RedisKeyConstants() {}

    // 所有房间的 Map（houseId -> House JSON）
    public static final String HOUSES = "tm:houses";

    // 单个房间基本信息
    public static String house(String houseId) {
        return "tm:house:" + houseId;
    }

    // 点歌队列（List，index 0 为队首待播）
    public static String pickList(String houseId) {
        return "tm:pick:" + houseId;
    }

    // 默认播放列表（兜底歌单）
    public static String defaultList(String houseId) {
        return "tm:default:" + houseId;
    }

    // 当前正在播放的歌曲
    public static String playing(String houseId) {
        return "tm:playing:" + houseId;
    }

    // 房间运行时配置（Hash）
    public static String config(String houseId) {
        return "tm:config:" + houseId;
    }

    // 房间在线会话（Hash，sessionId -> SessionUser JSON）
    public static String session(String houseId) {
        return "tm:session:" + houseId;
    }

    // 被拉黑的用户（Set，存 sessionId 或 IP）
    public static String blackUser(String houseId) {
        return "tm:black:user:" + houseId;
    }

    // 被拉黑的音乐（Set，存 musicId）
    public static String blackMusic(String houseId) {
        return "tm:black:music:" + houseId;
    }

    // 本轮投票切歌的 sessionId 集合
    public static String vote(String houseId) {
        return "tm:vote:" + houseId;
    }

    // 某 IP 创建的房间 ID 集合
    public static String ipHouses(String ip) {
        return "tm:ip:house:" + ip;
    }
}
