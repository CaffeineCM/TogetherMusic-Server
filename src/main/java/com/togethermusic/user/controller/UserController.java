package com.togethermusic.user.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.togethermusic.common.response.Response;
import com.togethermusic.user.dto.UpdateProfileRequest;
import com.togethermusic.user.dto.UserProfileResponse;
import com.togethermusic.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user")
@SaCheckLogin
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/profile")
    public Response<UserProfileResponse> getProfile() {
        return Response.success(userService.getProfile());
    }

    @PutMapping("/profile")
    public Response<UserProfileResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return Response.success(userService.updateProfile(request), "资料更新成功");
    }
}
