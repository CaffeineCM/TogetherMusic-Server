package com.togethermusic.user.service;

import com.alibaba.fastjson2.JSONObject;
import com.togethermusic.config.TogetherMusicProperties;
import com.togethermusic.music.adapter.KuGouAdapter;
import com.togethermusic.user.dto.KugouAccountStatusResponse;
import com.togethermusic.user.dto.KugouCaptchaSendResponse;
import com.togethermusic.user.dto.KugouQrLoginCheckResponse;
import com.togethermusic.user.dto.KugouQrLoginStartResponse;
import com.togethermusic.user.entity.UserMusicAccount;
import com.togethermusic.user.repository.UserMusicAccountRepository;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class KugouAccountService {

    private static final String SOURCE = "kugou";

    private final UserMusicAccountRepository accountRepository;
    private final KuGouAdapter kuGouAdapter;
    private final TogetherMusicProperties properties;

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

        return persistAuthorizedAccount(userId, userToken, kgUserId, "酷狗授权成功");
    }

    public KugouQrLoginStartResponse startQrLogin() {
        JSONObject keyJson = get(baseUrl() + "/login/qr/key?timestamp=" + System.currentTimeMillis()).orElse(null);
        String key = firstNonBlank(keyJson, "key", "data.key", "codekey", "data.codekey", "qrcode", "data.qrcode");

        JSONObject qrJson = null;
        if (StringUtils.hasText(key)) {
            qrJson = get(baseUrl() + "/login/qr/create?key=" + encode(key) + "&qrimg=true&timestamp="
                    + System.currentTimeMillis()).orElse(null);
        }
        if (qrJson == null) {
            qrJson = get(baseUrl() + "/login/qr/create?qrimg=true&timestamp="
                    + System.currentTimeMillis()).orElse(null);
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
        if (json == null) {
            return KugouQrLoginCheckResponse.builder()
                    .code(-1)
                    .message("二维码状态查询失败")
                    .authorized(false)
                    .build();
        }

        int code = Optional.ofNullable(json.getInteger("code")).orElse(-1);
        String message = extractMessage(json, "等待扫码");
        boolean authorized = false;
        String nickname = null;

        if (code == 4) {
            String token = firstNonBlank(json, "token", "data.token");
            String kgUserId = firstNonBlank(firstObject(json, "data"), "userid", "userId", "uid");
            if (StringUtils.hasText(token)) {
                KugouAccountStatusResponse status = persistAuthorizedAccount(userId, token, kgUserId, "酷狗授权成功");
                authorized = status.valid();
                nickname = status.nickname();
                message = status.message();
            } else {
                message = "二维码已确认，但未返回 token";
            }
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
                .map(account -> buildStatus(account.getAuthToken()))
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

        return KugouAccountStatusResponse.builder()
                .valid(true)
                .nickname(kuGouAdapter.getNickname(token).orElse(null))
                .message(successMessage)
                .build();
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
}
