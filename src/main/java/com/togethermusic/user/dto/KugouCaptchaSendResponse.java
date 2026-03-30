package com.togethermusic.user.dto;

import lombok.Builder;

@Builder
public record KugouCaptchaSendResponse(
        boolean success,
        String message
) {
}
