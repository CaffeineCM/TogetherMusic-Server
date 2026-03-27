package com.togethermusic.auth.service;

import cn.dev33.satoken.stp.StpUtil;
import com.togethermusic.auth.dto.LoginRequest;
import com.togethermusic.auth.dto.LoginResponse;
import com.togethermusic.auth.dto.RegisterRequest;
import com.togethermusic.common.code.ErrorCode;
import com.togethermusic.common.exception.BusinessException;
import com.togethermusic.user.entity.RegisteredUser;
import com.togethermusic.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, "用户名已存在");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, "邮箱已被注册");
        }

        RegisteredUser user = new RegisteredUser();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.username()); // 默认昵称与用户名相同
        userRepository.save(user);
    }

    public LoginResponse login(LoginRequest request) {
        // 支持用户名或邮箱登录
        RegisteredUser user = userRepository.findByUsername(request.account())
                .or(() -> userRepository.findByEmail(request.account()))
                .orElseThrow(() -> new BusinessException(ErrorCode.WRONG_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.WRONG_CREDENTIALS);
        }

        StpUtil.login(user.getId());
        String token = StpUtil.getTokenValue();

        return new LoginResponse(token, user.getId(), user.getUsername(), user.getNickname());
    }

    public void logout() {
        StpUtil.logout();
    }
}
