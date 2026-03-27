-- 网易云 cookie 往往长于 512 字符，这里扩容到 TEXT，避免授权信息被截断

ALTER TABLE tm_user_music_account
    ALTER COLUMN auth_token TYPE TEXT,
    ALTER COLUMN refresh_token TYPE TEXT;
