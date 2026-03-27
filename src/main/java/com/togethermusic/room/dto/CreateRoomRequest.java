package com.togethermusic.room.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRoomRequest(
        @NotBlank(message = "房间名不能为空")
        @Size(max = 32, message = "房间名不能超过 32 个字符")
        String name,

        @Size(max = 200, message = "房间描述不能超过 200 个字符")
        String desc,

        String password,

        String adminPwd,

        Boolean keepRoom
) {}
