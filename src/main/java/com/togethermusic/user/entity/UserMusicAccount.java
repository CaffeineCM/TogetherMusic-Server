package com.togethermusic.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 用户音乐平台账号绑定
 * 支持用户绑定网易云、QQ音乐、酷狗等平台账号
 */
@Entity
@Table(name = "tm_user_music_account")
@Getter
@Setter
@NoArgsConstructor
public class UserMusicAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 16)
    private String source;

    @Column(name = "auth_token", nullable = false, columnDefinition = "TEXT")
    private String authToken;

    @Column(name = "refresh_token", columnDefinition = "TEXT")
    private String refreshToken;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.isActive == null) {
            this.isActive = true;
        }
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 检查 Token 是否过期
     */
    public boolean isTokenExpired() {
        if (expiresAt == null) {
            return false;
        }
        return OffsetDateTime.now().isAfter(expiresAt);
    }

    /**
     * 检查账号是否有效（激活且未过期）
     */
    public boolean isValid() {
        return Boolean.TRUE.equals(isActive) && !isTokenExpired();
    }
}
