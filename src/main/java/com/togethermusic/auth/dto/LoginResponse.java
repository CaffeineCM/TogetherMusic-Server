package com.togethermusic.auth.dto;

public record LoginResponse(
        String token,
        Long userId,
        String username,
        String nickname
) {}
