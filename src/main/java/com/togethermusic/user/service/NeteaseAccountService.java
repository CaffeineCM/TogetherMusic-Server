package com.togethermusic.user.service;

import com.alibaba.fastjson2.JSONObject;
import com.togethermusic.common.code.ErrorCode;
import com.togethermusic.common.exception.BusinessException;
import com.togethermusic.config.TogetherMusicProperties;
import com.togethermusic.repository.RoomRedisRepository;
import com.togethermusic.room.model.House;
import com.togethermusic.user.dto.NeteaseAccountStatusResponse;
import com.togethermusic.user.dto.NeteaseQrLoginCheckResponse;
import com.togethermusic.user.dto.NeteaseQrLoginStartResponse;
import com.togethermusic.user.entity.UserMusicAccount;
import com.togethermusic.user.repository.UserMusicAccountRepository;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 网易云账号授权与保活服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NeteaseAccountService {

    private static final String SOURCE = "netease";
    private static final Set<String> IMPORTANT_COOKIE_KEYS = Set.of(
            "MUSIC_U",
            "MUSIC_A",
            "__csrf",
            "uid",
            "NMTID",
            "__remember_me",
            "MUSICIAN_COMPANY_LAST_ENTRY",
            "NTES_YD_SESS",
            "S_INFO",
            "P_INFO",
            "JSESSIONID-WYYY"
    );

    private final TogetherMusicProperties properties;
    private final UserMusicAccountRepository accountRepository;
    private final RoomRedisRepository roomRepository;

    public NeteaseQrLoginStartResponse startQrLogin() {
        String key = execute(post(baseUrl() + "/login/qr/key"))
                .map(json -> json.getJSONObject("data"))
                .map(data -> data.getString("unikey"))
                .orElseThrow(() -> new IllegalStateException("获取网易云二维码 key 失败"));

        JSONObject qrData = execute(post(baseUrl() + "/login/qr/create")
                .queryString("key", key)
                .queryString("qrimg", true))
                .map(json -> json.getJSONObject("data"))
                .orElseThrow(() -> new IllegalStateException("生成网易云二维码失败"));

        return NeteaseQrLoginStartResponse.builder()
                .key(key)
                .qrUrl(qrData.getString("qrurl"))
                .qrImage(qrData.getString("qrimg"))
                .build();
    }

    public boolean sendCaptcha(String phone, String ctcode) {
        JSONObject response = execute(post(baseUrl() + "/captcha/sent")
                .queryString("phone", phone)
                .queryString("ctcode", ctcode))
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "验证码发送失败，请稍后重试"));
        if (response.getIntValue("code") != 200) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, extractMessage(response, "验证码发送失败"));
        }
        return true;
    }

    @Transactional
    public NeteaseAccountStatusResponse importCookie(Long userId, String rawCookie, String uid) {
        String normalizedCookie = normalizeCookie(rawCookie, uid);
        if (normalizedCookie.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "导入的 Cookie 为空");
        }

        UserMusicAccount account = accountRepository
                .findByUserIdAndSource(userId, SOURCE)
                .orElseGet(UserMusicAccount::new);
        account.setUserId(userId);
        account.setSource(SOURCE);
        account.setAuthToken(normalizedCookie);
        account.setIsActive(true);
        account.setExpiresAt(null);
        account.setRefreshToken(null);
        accountRepository.save(account);

        NeteaseAccountStatusResponse status = validateByProvider(account);
        if (!status.isValid()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    status.getMessage() != null ? status.getMessage() : "导入的 Cookie 无效");
        }
        syncOwnedRoomsTokenHolder(userId);
        return status;
    }

    @Transactional
    public NeteaseAccountStatusResponse loginByCaptcha(Long userId, String phone, String captcha, String ctcode) {
        JSONObject response = execute(post(baseUrl() + "/login/cellphone")
                .queryString("phone", phone)
                .queryString("captcha", captcha)
                .queryString("ctcode", ctcode))
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "网易云手机验证码登录失败"));

        if (response.getIntValue("code") != 200) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    extractMessage(response, "网易云手机验证码登录失败")
            );
        }

        String cookie = response.getString("cookie");
        if (cookie == null || cookie.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "网易云登录成功，但未返回登录 cookie");
        }

        UserMusicAccount account = accountRepository
                .findByUserIdAndSource(userId, SOURCE)
                .orElseGet(UserMusicAccount::new);
        account.setUserId(userId);
        account.setSource(SOURCE);
        account.setAuthToken(cookie);
        account.setIsActive(true);
        account.setExpiresAt(null);
        account.setRefreshToken(null);
        accountRepository.save(account);

        NeteaseAccountStatusResponse status = validateAccount(account, false);
        if (status.isValid()) {
            syncOwnedRoomsTokenHolder(userId);
        }
        return status;
    }

    @Transactional
    public NeteaseQrLoginCheckResponse checkQrLogin(Long userId, String key) {
        JSONObject response = execute(get(baseUrl() + "/login/qr/check")
                .queryString("key", key))
                .orElseThrow(() -> new IllegalStateException("轮询网易云二维码状态失败"));

        int code = response.getIntValue("code");
        String message = response.getString("message");

        if (code != 803) {
            return NeteaseQrLoginCheckResponse.builder()
                    .code(code)
                    .message(message)
                    .authorized(false)
                    .build();
        }

        String cookie = response.getString("cookie");
        if (cookie == null || cookie.isBlank()) {
            throw new IllegalStateException("网易云授权成功，但未返回登录 cookie");
        }

        UserMusicAccount account = accountRepository
                .findByUserIdAndSource(userId, SOURCE)
                .orElseGet(UserMusicAccount::new);
        account.setUserId(userId);
        account.setSource(SOURCE);
        account.setAuthToken(cookie);
        account.setIsActive(true);
        account.setExpiresAt(null);
        account.setRefreshToken(null);
        accountRepository.save(account);

        NeteaseAccountStatusResponse status = validateAccount(account, false);
        if (status.isValid()) {
            syncOwnedRoomsTokenHolder(userId);
        }
        log.info("[NeteaseAuth] User {} QR login success, nickname={}", userId, status.getNickname());

        return NeteaseQrLoginCheckResponse.builder()
                .code(code)
                .message(message)
                .authorized(true)
                .nickname(status.getNickname())
                .build();
    }

    @Transactional
    public NeteaseAccountStatusResponse validateCurrentUser(Long userId) {
        UserMusicAccount account = accountRepository.findByUserIdAndSource(userId, SOURCE)
                .orElse(null);
        if (account == null) {
            return NeteaseAccountStatusResponse.builder()
                    .valid(false)
                    .refreshed(false)
                    .message("未绑定网易云账号")
                    .build();
        }
        return validateAccount(account, true);
    }

    @Transactional
    public NeteaseAccountStatusResponse refreshCurrentUser(Long userId) {
        UserMusicAccount account = accountRepository.findByUserIdAndSource(userId, SOURCE)
                .orElse(null);
        if (account == null) {
            return NeteaseAccountStatusResponse.builder()
                    .valid(false)
                    .refreshed(false)
                    .message("未绑定网易云账号")
                    .build();
        }
        return refreshAccount(account);
    }

    @Transactional
    @Scheduled(fixedDelay = 21600000)
    public void keepAliveAccounts() {
        List<UserMusicAccount> accounts = accountRepository.findBySource(SOURCE);
        for (UserMusicAccount account : accounts) {
            if (!Boolean.TRUE.equals(account.getIsActive())) {
                continue;
            }
            try {
                validateAccount(account, true);
            } catch (Exception e) {
                log.warn("[NeteaseAuth] Keepalive failed for user {}: {}",
                        account.getUserId(), e.getMessage());
            }
        }
    }

    private NeteaseAccountStatusResponse validateAccount(UserMusicAccount account, boolean autoRefresh) {
        NeteaseAccountStatusResponse providerStatus = validateByProvider(account);
        if (providerStatus.isValid()) {
            account.setIsActive(true);
            account.setUpdatedAt(OffsetDateTime.now());
            accountRepository.save(account);
            syncOwnedRoomsTokenHolder(account.getUserId());
            return providerStatus;
        }

        if (!autoRefresh) {
            account.setIsActive(false);
            accountRepository.save(account);
            return providerStatus;
        }

        return refreshAccount(account);
    }

    private NeteaseAccountStatusResponse validateByProvider(UserMusicAccount account) {
        JSONObject response = execute(post(baseUrl() + "/login/status")
                .queryString("cookie", account.getAuthToken()))
                .orElse(null);

        String nickname = extractNickname(response);
        boolean valid = isLoginStatusValid(response);
        if (valid) {
            return NeteaseAccountStatusResponse.builder()
                    .valid(true)
                    .refreshed(false)
                    .nickname(nickname)
                    .message("网易云授权有效")
                    .build();
        }

        return NeteaseAccountStatusResponse.builder()
                .valid(false)
                .refreshed(false)
                .nickname(nickname)
                .message(extractMessage(response, "网易云授权已失效"))
                .build();
    }

    private NeteaseAccountStatusResponse refreshAccount(UserMusicAccount account) {
        JSONObject response = execute(post(baseUrl() + "/login/refresh")
                .queryString("cookie", account.getAuthToken()))
                .orElse(null);

        if (response == null || response.getIntValue("code") != 200) {
            account.setIsActive(false);
            accountRepository.save(account);
            return NeteaseAccountStatusResponse.builder()
                    .valid(false)
                    .refreshed(false)
                    .message("网易云授权刷新失败，请重新授权")
                    .build();
        }

        String refreshedCookie = response.getString("cookie");
        if (refreshedCookie != null && !refreshedCookie.isBlank()) {
            account.setAuthToken(refreshedCookie);
        }
        account.setIsActive(true);
        account.setUpdatedAt(OffsetDateTime.now());
        accountRepository.save(account);

        JSONObject status = execute(post(baseUrl() + "/login/status")
                .queryString("cookie", account.getAuthToken()))
                .orElse(null);

        boolean valid = isLoginStatusValid(status);
        String nickname = extractNickname(status);
        account.setIsActive(valid);
        accountRepository.save(account);
        if (valid) {
            syncOwnedRoomsTokenHolder(account.getUserId());
        }

        return NeteaseAccountStatusResponse.builder()
                .valid(valid)
                .refreshed(true)
                .nickname(nickname)
                .message(valid ? "网易云授权已刷新" : "网易云授权刷新后仍无效，请重新授权")
                .build();
    }

    private String baseUrl() {
        return properties.getMusicApi().getNetease();
    }

    private boolean isLoginStatusValid(JSONObject response) {
        if (response == null) {
            return false;
        }
        JSONObject data = response.getJSONObject("data");
        Integer code = response.getInteger("code");
        if (code != null && code != 200) {
            return false;
        }
        if (data == null) {
            return hasUserIdentity(response);
        }

        JSONObject account = data.getJSONObject("account");
        if (account != null) {
            return true;
        }

        return hasUserIdentity(data);
    }

    private String extractNickname(JSONObject response) {
        if (response == null) {
            return null;
        }
        JSONObject data = response.getJSONObject("data");
        if (data == null) {
            return extractName(response);
        }
        JSONObject profile = data.getJSONObject("profile");
        if (profile != null) {
            return profile.getString("nickname");
        }
        return extractName(data);
    }

    private static kong.unirest.GetRequest get(String url) {
        return Unirest.get(url);
    }

    private static kong.unirest.HttpRequestWithBody post(String url) {
        return Unirest.post(url);
    }

    private static Optional<JSONObject> asJson(HttpResponse<String> response) {
        String body = response.getBody();
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(JSONObject.parseObject(body));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static Optional<JSONObject> execute(kong.unirest.HttpRequest<?> request) {
        try {
            return asJson(request.asString());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String extractMessage(JSONObject response, String fallback) {
        String message = response.getString("message");
        if (message == null || message.isBlank()) {
            message = response.getString("msg");
        }
        return message == null || message.isBlank() ? fallback : message;
    }

    private boolean hasUserIdentity(JSONObject json) {
        return json.getLong("id") != null
                || json.getLong("userId") != null
                || json.getString("userName") != null
                || json.getString("nickname") != null;
    }

    private String extractName(JSONObject json) {
        String nickname = json.getString("nickname");
        if (nickname != null && !nickname.isBlank()) {
            return nickname;
        }
        String userName = json.getString("userName");
        if (userName != null && !userName.isBlank()) {
            return userName;
        }
        return null;
    }

    private String normalizeCookie(String rawCookie, String uid) {
        if (rawCookie == null || rawCookie.isBlank()) {
            return "";
        }

        Map<String, String> cookies = new LinkedHashMap<>();
        String[] entries = rawCookie.split(";");
        for (String entry : entries) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int separatorIndex = trimmed.indexOf('=');
            if (separatorIndex <= 0) {
                continue;
            }
            String key = trimmed.substring(0, separatorIndex).trim();
            String value = trimmed.substring(separatorIndex + 1).trim();
            if (key.isEmpty() || value.isEmpty()) {
                continue;
            }
            cookies.put(key, value);
        }

        if (uid != null && !uid.isBlank()) {
            cookies.put("uid", uid.trim());
        }

        StringBuilder builder = new StringBuilder();
        for (String key : IMPORTANT_COOKIE_KEYS) {
            String value = cookies.get(key);
            if (value == null || value.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append("; ");
            }
            builder.append(key).append("=").append(value);
        }

        if (builder.isEmpty()) {
            cookies.forEach((key, value) -> {
                if (!builder.isEmpty()) {
                    builder.append("; ");
                }
                builder.append(key).append("=").append(value);
            });
        }

        return builder.toString();
    }

    private void syncOwnedRoomsTokenHolder(Long userId) {
        if (userId == null) {
            return;
        }

        for (House house : roomRepository.findAll()) {
            if (!userId.equals(house.getCreatorUserId())) {
                continue;
            }

            String source = house.getDefaultMusicSource();
            if (source != null
                    && !"wy".equalsIgnoreCase(source)
                    && !"netease".equalsIgnoreCase(source)) {
                continue;
            }

            if (userId.equals(house.getTokenHolderUserId())) {
                continue;
            }

            house.setTokenHolderUserId(userId);
            roomRepository.save(house);
            log.info("[NeteaseAuth] Synced room {} token holder to user {}", house.getId(), userId);
        }
    }
}
