-- 用户音乐平台账号绑定表
-- 支持用户绑定网易云、QQ音乐、酷狗等平台账号

CREATE TABLE IF NOT EXISTS tm_user_music_account (
    id              BIGSERIAL    PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES tm_user(id) ON DELETE CASCADE,
    source          VARCHAR(16)  NOT NULL,  -- 'netease', 'qq', 'kugou'
    auth_token      VARCHAR(512) NOT NULL,  -- 平台认证令牌
    refresh_token   VARCHAR(512),           -- 刷新令牌（可选）
    expires_at      TIMESTAMPTZ,            -- 令牌过期时间
    is_active       BOOLEAN      DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, source)
);

CREATE INDEX IF NOT EXISTS idx_user_music_account_user_id ON tm_user_music_account(user_id);
CREATE INDEX IF NOT EXISTS idx_user_music_account_source ON tm_user_music_account(source);
