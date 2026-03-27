package com.togethermusic.music.adapter;

import com.alibaba.fastjson2.JSONObject;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.function.Function;

/**
 * 音乐适配器基类
 * 封装 HTTP 调用重试逻辑，子类只需关注业务解析
 */
@Slf4j
public abstract class AbstractMusicAdapter implements MusicSourceAdapter {

    protected static final int MAX_RETRY = 2;

    /**
     * 带重试的 GET 请求，返回解析后的结果
     */
    protected <T> Optional<T> getWithRetry(String url, Function<JSONObject, T> parser) {
        return getWithRetry(url, null, parser);
    }

    /**
     * 带重试的 GET 请求，支持用户 Token
     * @param url 请求 URL
     * @param userToken 用户 Token，为 null 时不添加认证头
     * @param parser 响应解析器
     */
    protected <T> Optional<T> getWithRetry(String url, String userToken, Function<JSONObject, T> parser) {
        for (int i = 0; i < MAX_RETRY; i++) {
            try {
                var request = Unirest.get(url);
                if (userToken != null && !userToken.isBlank()) {
                    request.header("Authorization", "Bearer " + userToken);
                }
                HttpResponse<String> response = request.asString();
                if (response.getStatus() == 200) {
                    JSONObject json = JSONObject.parseObject(response.getBody());
                    T result = parser.apply(json);
                    if (result != null) return Optional.of(result);
                }
            } catch (Exception e) {
                log.warn("[{}] GET {} failed (attempt {}): {}", sourceCode(), url, i + 1, e.getMessage());
            }
        }
        return Optional.empty();
    }

    /**
     * 带重试的 POST 请求（query string 参数）
     */
    protected <T> Optional<T> postWithRetry(String url, java.util.Map<String, Object> params,
                                             Function<JSONObject, T> parser) {
        return postWithRetry(url, params, null, parser);
    }

    /**
     * 带重试的 POST 请求，支持用户 Token
     * @param url 请求 URL
     * @param params 请求参数
     * @param userToken 用户 Token，为 null 时不添加认证头
     * @param parser 响应解析器
     */
    protected <T> Optional<T> postWithRetry(String url, java.util.Map<String, Object> params,
                                             String userToken, Function<JSONObject, T> parser) {
        for (int i = 0; i < MAX_RETRY; i++) {
            try {
                var request = Unirest.post(url);
                params.forEach(request::queryString);
                if (userToken != null && !userToken.isBlank()) {
                    request.header("Authorization", "Bearer " + userToken);
                }
                HttpResponse<String> response = request.asString();
                if (response.getStatus() == 200) {
                    JSONObject json = JSONObject.parseObject(response.getBody());
                    T result = parser.apply(json);
                    if (result != null) return Optional.of(result);
                }
            } catch (Exception e) {
                log.warn("[{}] POST {} failed (attempt {}): {}", sourceCode(), url, i + 1, e.getMessage());
            }
        }
        return Optional.empty();
    }

    /**
     * 拼接艺术家名称（数组 -> "A&B&C"）
     */
    protected String joinArtists(com.alibaba.fastjson2.JSONArray artists, String nameField) {
        if (artists == null || artists.isEmpty()) return "未知艺术家";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < artists.size(); i++) {
            if (i > 0) sb.append("&");
            sb.append(artists.getJSONObject(i).getString(nameField));
        }
        return sb.toString();
    }
}
