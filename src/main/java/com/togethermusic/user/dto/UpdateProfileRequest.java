package com.togethermusic.user.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 32, message = "昵称不能超过 32 个字符")
        String nickname,

        @Size(max = 512, message = "头像 URL 过长")
        String avatarUrl
) {}
