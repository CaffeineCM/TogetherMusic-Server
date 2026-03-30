package com.togethermusic.user.service;

import com.alibaba.fastjson2.JSONObject;
import com.togethermusic.config.TogetherMusicProperties;
import com.togethermusic.music.adapter.KuGouAdapter;
import com.togethermusic.repository.RoomRedisRepository;
import com.togethermusic.room.model.House;
import com.togethermusic.user.dto.KugouAccountStatusResponse;
import com.togethermusic.user.dto.KugouCaptchaSendResponse;
import com.togethermusic.user.dto.KugouQrLoginCheckResponse;
import com.togethermusic.user.dto.KugouQrLoginStartResponse;
import com.togethermusic.user.entity.UserMusicAccount;
import com.togethermusic.user.repository.UserMusicAccountRepository;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class KugouAccountService {

    private static final String SOURCE = "kugou";

    private final UserMusicAccountRepository accountRepository;
    private final KuGouAdapter kuGouAdapter;
    private final TogetherMusicProperties properties;
    private final RoomRedisRepository roomRepository;

    public KugouAccountStatusResponse importToken(Long userId, String token) {
        String normalized = token == null ? "" : token.trim();
        if (normalized.isEmpty()) {
            return invalid("酷狗凭证不能为空");
        }
        return persistAuthorizedAccount(userId, normalized, null, "酷狗授权成功");
    }

    public KugouCaptchaSendResponse sendCaptcha(String mobile) {
        if (!StringUtils.hasText(mobile)) {
            return KugouCaptchaSendResponse.builder()
                    .success(false)
                    .message("手机号不能为空")
                    .build();
        }

        JSONObject json = get(baseUrl() + "/captcha/sent?mobile=" + encode(mobile)).orElse(null);
        log.info("[KugouAuth] captcha/send mobile={} response={}", maskPhone(mobile), toLogString(json));
        if (json == null) {
            return KugouCaptchaSendResponse.builder()
                    .success(false)
                    .message("酷狗验证码接口无响应")
                    .build();
        }
        Integer status = json.getInteger("status");
        Integer code = json.getInteger("code");
        boolean success = Integer.valueOf(1).equals(status) || Integer.valueOf(200).equals(code);
        return KugouCaptchaSendResponse.builder()
                .success(success)
                .message(extractMessage(json, success ? "验证码已发送" : "验证码发送失败"))
                .build();
    }

    public KugouAccountStatusResponse loginByCaptcha(Long userId, String mobile, String captcha) {
        if (!StringUtils.hasText(mobile) || !StringUtils.hasText(captcha)) {
            return invalid("手机号和验证码不能为空");
        }

        JSONObject json = get(baseUrl() + "/login/cellphone?mobile=" + encode(mobile) + "&code=" + encode(captcha))
                .orElse(null);
        log.info("[KugouAuth] captcha/login userId={} mobile={} response={}",
                userId, maskPhone(mobile), toLogString(json));
        if (json == null) {
            return invalid("酷狗登录失败");
        }

        Integer status = json.getInteger("status");
        Integer code = json.getInteger("code");
        if (!Integer.valueOf(1).equals(status) && !Integer.valueOf(200).equals(code)) {
            return invalid(extractMessage(json, "酷狗登录失败"));
        }

        String token = firstNonBlank(json, "token", "data.token");
        String userToken = token != null ? token : firstNonBlank(firstObject(json, "data"), "token");
        String kgUserId = firstNonBlank(firstObject(json, "data"), "userid", "userId", "uid");
        if (!StringUtils.hasText(userToken)) {
            return invalid("酷狗登录成功但未返回 token");
        }

        saveAccount(userId, userToken, kgUserId);
        return KugouAccountStatusResponse.builder()
                .valid(true)
                .nickname(firstNonBlank(firstObject(json, "data"), "nickname", "uname", "username"))
                .message("酷狗授权成功")
                .build();
    }

    public KugouQrLoginStartResponse startQrLogin() {
        JSONObject keyJson = get(baseUrl() + "/login/qr/key?timestamp=" + System.currentTimeMillis()).orElse(null);
        log.info("[KugouAuth] qr/start key response={}", toLogString(keyJson));
        String key = firstNonBlank(keyJson, "key", "data.key", "codekey", "data.codekey", "qrcode", "data.qrcode");

        JSONObject qrJson = null;
        if (StringUtils.hasText(key)) {
            qrJson = get(baseUrl() + "/login/qr/create?key=" + encode(key) + "&qrimg=true&timestamp="
                    + System.currentTimeMillis()).orElse(null);
            log.info("[KugouAuth] qr/start create-with-key key={} response={}", key, toLogString(qrJson));
        }
        if (qrJson == null) {
            qrJson = get(baseUrl() + "/login/qr/create?qrimg=true&timestamp="
                    + System.currentTimeMillis()).orElse(null);
            log.info("[KugouAuth] qr/start create-without-key response={}", toLogString(qrJson));
        }

        String effectiveKey = StringUtils.hasText(key)
                ? key
                : firstNonBlank(qrJson, "key", "data.key", "codekey", "data.codekey", "qrcode", "data.qrcode");
        return KugouQrLoginStartResponse.builder()
                .key(effectiveKey)
                .qrUrl(firstNonBlank(
                        qrJson,
                        "url",
                        "data.url",
                        "qrurl",
                        "data.qrurl",
                        "qrUrl",
                        "data.qrUrl"
                ))
                .qrImage(firstNonBlank(
                        qrJson,
                        "qrimg",
                        "data.qrimg",
                        "base64",
                        "data.base64",
                        "qrImage",
                        "data.qrImage"
                ))
                .build();
    }

    public KugouQrLoginCheckResponse checkQrLogin(Long userId, String key) {
        JSONObject json = get(baseUrl() + "/login/qr/check?key=" + encode(key) + "&timestamp="
                + System.currentTimeMillis()).orElse(null);
        log.info("[KugouAuth] qr/check userId={} key={} response={}", userId, key, toLogString(json));
        if (json == null) {
            return KugouQrLoginCheckResponse.builder()
                    .code(-1)
                    .message("二维码状态查询失败")
                    .authorized(false)
                    .build();
        }

        int code = firstInt(json, "code", "status", "data.code", "data.status");
        String message = extractMessage(json, "等待扫码");
        boolean authorized = false;
        String nickname = null;
        String token = firstNonBlank(json, "token", "data.token", "data.info.token");
        String kgUserId = firstNonBlank(
                json,
                "userid",
                "userId",
                "uid",
                "data.userid",
                "data.userId",
                "data.uid",
                "data.info.userid"
        );

        if (StringUtils.hasText(token)) {
            saveAccount(userId, token, kgUserId);
            authorized = true;
            nickname = firstNonBlank(
                    json,
                    "nickname",
                    "data.nickname",
                    "data.info.nickname",
                    "data.uname",
                    "data.username"
            );
            message = "酷狗授权成功";
        } else if (code == 4) {
            message = "二维码已确认，但未返回 token";
        }

        return KugouQrLoginCheckResponse.builder()
                .code(code)
                .message(message)
                .authorized(authorized)
                .nickname(nickname)
                .build();
    }

    public KugouAccountStatusResponse validateCurrentUser(Long userId) {
        return accountRepository.findByUserIdAndSource(userId, SOURCE)
                .map(this::validateStoredAccount)
                .orElseGet(() -> invalid("未授权"));
    }

    public KugouAccountStatusResponse refreshCurrentUser(Long userId) {
        return accountRepository.findByUserIdAndSource(userId, SOURCE)
                .map(this::refreshStatus)
                .orElseGet(() -> invalid("未授权"));
    }

    private KugouAccountStatusResponse refreshStatus(UserMusicAccount account) {
        String token = account.getAuthToken();
        String kgUserId = account.getRefreshToken();
        if (!StringUtils.hasText(token)) {
            return invalid("未授权");
        }

        StringBuilder url = new StringBuilder(baseUrl()).append("/login/token?token=").append(encode(token));
        if (StringUtils.hasText(kgUserId)) {
            url.append("&userid=").append(encode(kgUserId));
        }
        JSONObject json = get(url.toString()).orElse(null);
        log.info("[KugouAuth] refresh userId={} storedUserId={} response={}",
                account.getUserId(), kgUserId, toLogString(json));
        if (json == null) {
            return buildStatus(token);
        }

        String refreshedToken = firstNonBlank(json, "token", "data.token");
        String effectiveToken = StringUtils.hasText(refreshedToken) ? refreshedToken : token;
        String refreshedUserId = firstNonBlank(firstObject(json, "data"), "userid", "userId", "uid");
        if (StringUtils.hasText(refreshedToken)) {
            account.setAuthToken(effectiveToken);
        }
        if (StringUtils.hasText(refreshedUserId)) {
            account.setRefreshToken(refreshedUserId);
        }
        accountRepository.save(account);

        KugouAccountStatusResponse status = buildStatus(effectiveToken);
        return KugouAccountStatusResponse.builder()
                .valid(status.valid())
                .nickname(status.nickname())
                .message(status.valid() ? "授权已刷新" : status.message())
                .build();
    }

    private KugouAccountStatusResponse validateStoredAccount(UserMusicAccount account) {
        if (!Boolean.TRUE.equals(account.getIsActive()) || !StringUtils.hasText(account.getAuthToken())) {
            return invalid("未授权");
        }

        String token = account.getAuthToken();
        String kgUserId = account.getRefreshToken();
        String nickname = null;

        if (StringUtils.hasText(kgUserId)) {
            JSONObject json = get(baseUrl() + "/login/token?token=" + encode(token) + "&userid=" + encode(kgUserId))
                    .orElse(null);
            log.info("[KugouAuth] status userId={} storedUserId={} response={}",
                    account.getUserId(), kgUserId, toLogString(json));
            if (json != null) {
                String refreshedToken = firstNonBlank(json, "token", "data.token");
                String refreshedUserId = firstNonBlank(json, "userid", "userId", "uid", "data.userid", "data.userId");
                nickname = firstNonBlank(json, "nickname", "data.nickname", "data.uname", "data.username");
                if (StringUtils.hasText(refreshedToken)) {
                    account.setAuthToken(refreshedToken);
                }
                if (StringUtils.hasText(refreshedUserId)) {
                    account.setRefreshToken(refreshedUserId);
                }
                accountRepository.save(account);
            }
        }

        return KugouAccountStatusResponse.builder()
                .valid(true)
                .nickname(nickname)
                .message("已授权")
                .build();
    }

    private KugouAccountStatusResponse persistAuthorizedAccount(
            Long userId,
            String token,
            String kgUserId,
            String successMessage
    ) {
        boolean valid = kuGouAdapter.validateToken(token);
        if (!valid) {
            return invalid("酷狗凭证校验失败");
        }

        saveAccount(userId, token, kgUserId);

        return KugouAccountStatusResponse.builder()
                .valid(true)
                .nickname(kuGouAdapter.getNickname(token).orElse(null))
                .message(successMessage)
                .build();
    }

    private void saveAccount(Long userId, String token, String kgUserId) {
        UserMusicAccount account = accountRepository.findByUserIdAndSource(userId, SOURCE)
                .orElseGet(UserMusicAccount::new);
        account.setUserId(userId);
        account.setSource(SOURCE);
        account.setAuthToken(token);
        account.setRefreshToken(StringUtils.hasText(kgUserId)
                ? kgUserId
                : kuGouAdapter.getUserId(token).orElse(null));
        account.setExpiresAt(null);
        account.setIsActive(true);
        accountRepository.save(account);
        syncOwnedRoomsTokenHolder(userId);
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
                    && !"kg".equalsIgnoreCase(source)
                    && !"kugou".equalsIgnoreCase(source)) {
                continue;
            }

            if (userId.equals(house.getTokenHolderUserId("kg"))) {
                continue;
            }

            house.setTokenHolderUserId("kg", userId);
            roomRepository.save(house);
            log.info("[KugouAuth] Synced room {} token holder to user {}", house.getId(), userId);
        }
    }

    private KugouAccountStatusResponse buildStatus(String token) {
        boolean valid = kuGouAdapter.validateToken(token);
        return KugouAccountStatusResponse.builder()
                .valid(valid)
                .nickname(valid ? kuGouAdapter.getNickname(token).orElse(null) : null)
                .message(valid ? "授权有效" : "凭证已失效")
                .build();
    }

    private KugouAccountStatusResponse invalid(String message) {
        return KugouAccountStatusResponse.builder()
                .valid(false)
                .message(message)
                .build();
    }

    private Optional<JSONObject> get(String url) {
        try {
            HttpResponse<String> response = Unirest.get(url).asString();
            if (response.getStatus() == 200) {
                return Optional.ofNullable(JSONObject.parseObject(response.getBody()));
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }

    private String toLogString(JSONObject json) {
        if (json == null) {
            return "null";
        }
        String text = json.toJSONString();
        return text.length() > 1200 ? text.substring(0, 1200) + "...<truncated>" : text;
    }

    private String maskPhone(String mobile) {
        if (!StringUtils.hasText(mobile) || mobile.length() < 7) {
            return mobile;
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }

    private String extractMessage(JSONObject json, String fallback) {
        String message = firstNonBlank(json, "message", "msg");
        if (StringUtils.hasText(message)) {
            return message;
        }
        JSONObject data = firstObject(json, "data");
        String nested = firstNonBlank(data, "message", "msg", "statusText");
        return StringUtils.hasText(nested) ? nested : fallback;
    }

    private String baseUrl() {
        return properties.getMusicApi().getKugou();
    }

    private String encode(String raw) {
        try {
            return java.net.URLEncoder.encode(raw, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return raw;
        }
    }

    private JSONObject firstObject(JSONObject source, String key) {
        if (source == null) {
            return null;
        }
        Object value = source.get(key);
        return value instanceof JSONObject object ? object : null;
    }

    private String firstNonBlank(JSONObject source, String... paths) {
        if (source == null) {
            return null;
        }
        for (String path : paths) {
            Object current = source;
            for (String key : path.split("\\.")) {
                if (!(current instanceof JSONObject object)) {
                    current = null;
                    break;
                }
                current = object.get(key);
            }
            if (current instanceof String text && StringUtils.hasText(text)) {
                return text;
            }
            if (current instanceof Number number) {
                return String.valueOf(number);
            }
        }
        return null;
    }

    private int firstInt(JSONObject source, String... paths) {
        if (source == null) {
            return -1;
        }
        for (String path : paths) {
            Object current = source;
            for (String key : path.split("\\.")) {
                if (!(current instanceof JSONObject object)) {
                    current = null;
                    break;
                }
                current = object.get(key);
            }
            if (current instanceof Number number) {
                return number.intValue();
            }
            if (current instanceof String text && StringUtils.hasText(text)) {
                try {
                    return Integer.parseInt(text);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return -1;
    }
}
