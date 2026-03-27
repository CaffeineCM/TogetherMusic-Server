package com.togethermusic.config;

import cn.dev33.satoken.stp.StpUtil;
import com.togethermusic.common.util.ClientIpUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手拦截器
 * 在 HTTP Upgrade 阶段提取 Sa-Token，将 loginId 写入 Session Attributes
 * Guest 用户（无 Token）不阻断连接，registeredUserId 为 null
 */
@Slf4j
@Component
public class TokenHandshakeInterceptor implements HandshakeInterceptor {

    private static final String TOKEN_PARAM = "token";
    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        attributes.put("remoteAddress", extractRemoteAddress(request));

        String token = extractToken(request);
        if (StringUtils.hasText(token)) {
            try {
                Object loginId = StpUtil.getLoginIdByToken(token);
                if (loginId != null) {
                    attributes.put("registeredUserId", Long.parseLong(loginId.toString()));
                    log.debug("WebSocket handshake: authenticated user {}", loginId);
                }
            } catch (Exception e) {
                // Token 无效或已过期，作为 Guest 处理，不阻断连接
                log.debug("WebSocket handshake: invalid token, treating as guest");
            }
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    private String extractToken(ServerHttpRequest request) {
        // 优先从 Query String 取（方便 WebSocket 客户端）
        String query = request.getURI().getQuery();
        if (StringUtils.hasText(query)) {
            for (String param : query.split("&")) {
                if (param.startsWith(TOKEN_PARAM + "=")) {
                    return param.substring(TOKEN_PARAM.length() + 1);
                }
            }
        }
        // 再从 Authorization Header 取
        String authHeader = request.getHeaders().getFirst(AUTH_HEADER);
        if (StringUtils.hasText(authHeader)) {
            if (authHeader.startsWith(BEARER_PREFIX)) {
                return authHeader.substring(BEARER_PREFIX.length());
            }
            return authHeader;
        }
        return null;
    }

    private String extractRemoteAddress(ServerHttpRequest request) {
        return ClientIpUtils.extractClientIp(request.getHeaders(), request.getRemoteAddress());
    }
}
