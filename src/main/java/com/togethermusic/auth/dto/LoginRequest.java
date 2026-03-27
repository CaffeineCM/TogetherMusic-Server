package com.togethermusic.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "账号不能为空")
        String account,   // 支持用户名或邮箱

        @NotBlank(message = "密码不能为空")
        String password
) {}
