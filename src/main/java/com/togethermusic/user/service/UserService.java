package com.togethermusic.user.service;

import cn.dev33.satoken.stp.StpUtil;
import com.togethermusic.common.code.ErrorCode;
import com.togethermusic.common.exception.BusinessException;
import com.togethermusic.user.dto.UpdateProfileRequest;
import com.togethermusic.user.dto.UserProfileResponse;
import com.togethermusic.user.entity.RegisteredUser;
import com.togethermusic.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public UserProfileResponse getProfile() {
        RegisteredUser user = currentUser();
        return toResponse(user);
    }

    @Transactional
    public UserProfileResponse updateProfile(UpdateProfileRequest request) {
        RegisteredUser user = currentUser();

        if (StringUtils.hasText(request.nickname())) {
            user.setNickname(request.nickname());
        }
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(request.avatarUrl());
        }

        return toResponse(userRepository.save(user));
    }

    public RegisteredUser currentUser() {
        long userId = StpUtil.getLoginIdAsLong();
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private UserProfileResponse toResponse(RegisteredUser user) {
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getCreatedAt()
        );
    }
}
