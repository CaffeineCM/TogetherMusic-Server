package com.togethermusic.auth.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.togethermusic.auth.dto.LoginRequest;
import com.togethermusic.auth.dto.LoginResponse;
import com.togethermusic.auth.dto.RegisterRequest;
import com.togethermusic.auth.service.AuthService;
import com.togethermusic.common.response.Response;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public Response<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return Response.success(null, "注册成功");
    }

    @PostMapping("/login")
    public Response<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return Response.success(response, "登录成功");
    }

    @PostMapping("/logout")
    @SaCheckLogin
    public Response<Void> logout() {
        authService.logout();
        return Response.success(null, "已退出登录");
    }
}
