package com.togethermusic.music.adapter.netease;

public enum AesEncryptMode {
    CBC("CBC"),
    ECB("ECB");

    private final String type;

    AesEncryptMode(String type) {
        this.type = type;
    }

    public String type() {
        return type;
    }
}
