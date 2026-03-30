package com.togethermusic.user.dto;

import lombok.Builder;

@Builder
public record KugouAccountStatusResponse(
        boolean valid,
        String nickname,
        String message
) {
}
