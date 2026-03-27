package com.togethermusic.user.dto;

import java.time.OffsetDateTime;

public record UserProfileResponse(
        Long id,
        String username,
        String email,
        String nickname,
        String avatarUrl,
        OffsetDateTime createdAt
) {}
