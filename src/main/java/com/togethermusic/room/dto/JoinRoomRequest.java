package com.togethermusic.room.dto;

import jakarta.validation.constraints.NotBlank;

public record JoinRoomRequest(
        @NotBlank(message = "房间 ID 不能为空")
        String houseId,

        String password
) {}
