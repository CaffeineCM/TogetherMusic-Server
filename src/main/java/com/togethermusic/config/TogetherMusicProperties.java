package com.togethermusic.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * together-music 自定义配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "together-music")
public class TogetherMusicProperties {

    private Room room = new Room();
    private Music music = new Music();
    private Storage storage = new Storage();
    private Upload upload = new Upload();
    private MusicApi musicApi = new MusicApi();

    @Data
    public static class Room {
        private int ipHouseLimit = 3;
        private int maxHouseSize = 32;
    }

    @Data
    public static class Music {
        private float voteRate = 0.3f;
        private long expireTime = 1200000L;
        private int playlistSize = 100;
    }

    @Data
    public static class Storage {
        private String type = "local";
        private Local local = new Local();
        private Minio minio = new Minio();

        @Data
        public static class Local {
            private String baseDir = "/data/tm-uploads";
            private String baseUrl = "http://localhost:8080/uploads";
        }

        @Data
        public static class Minio {
            private String endpoint;
            private String accessKey;
            private String secretKey;
            private String bucket = "together-music";
        }
    }

    @Data
    public static class Upload {
        private long maxFileSize = 104857600L;
        private List<String> allowedMimeTypes = List.of(
                "audio/mpeg", "audio/flac", "audio/wav", "audio/ogg", "audio/mp4"
        );
    }

    @Data
    public static class MusicApi {
        private String netease = "http://localhost:3000";
        private String qq = "http://localhost:3300";
        private String kugou = "http://localhost:3400";
    }
}
