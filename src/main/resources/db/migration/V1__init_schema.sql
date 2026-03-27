-- Together Music 初始化数据库表结构

CREATE TABLE IF NOT EXISTS tm_user (
    id            BIGSERIAL    PRIMARY KEY,
    username      VARCHAR(32)  NOT NULL UNIQUE,
    email         VARCHAR(128) NOT NULL UNIQUE,
    password_hash VARCHAR(60)  NOT NULL,
    nickname      VARCHAR(32)  DEFAULT NULL,
    avatar_url    VARCHAR(512) DEFAULT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS tm_audio_file (
    id                BIGSERIAL    PRIMARY KEY,
    user_id           BIGINT       NOT NULL REFERENCES tm_user(id) ON DELETE CASCADE,
    original_filename VARCHAR(255) NOT NULL,
    storage_key       VARCHAR(512) NOT NULL,
    access_url        VARCHAR(512) NOT NULL,
    mime_type         VARCHAR(64)  NOT NULL,
    duration          BIGINT       DEFAULT NULL,
    file_size         BIGINT       NOT NULL,
    title             VARCHAR(255) DEFAULT NULL,
    artist            VARCHAR(255) DEFAULT NULL,
    uploaded_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audio_file_user_id ON tm_audio_file(user_id);
