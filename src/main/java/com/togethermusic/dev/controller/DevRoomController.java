package com.togethermusic.dev.controller;

import com.togethermusic.common.response.Response;
import com.togethermusic.dev.service.DevRoomCleanupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dev/rooms")
@RequiredArgsConstructor
public class DevRoomController {

    private final DevRoomCleanupService cleanupService;

    @PostMapping("/clear")
    public Response<DevRoomCleanupService.CleanupResult> clearAllRoomData() {
        return Response.success(cleanupService.clearAllRoomData(), "开发环境房间数据已清理");
    }
}
