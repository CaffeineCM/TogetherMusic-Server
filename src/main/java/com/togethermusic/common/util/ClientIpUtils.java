package com.togethermusic.common.util;

import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;

/**
 * 客户端 IP 解析工具
 * 兼容常见反向代理/CDN 的转发头，并提供游客显示名生成能力。
 */
public final class ClientIpUtils {

    private static final List<String> HEADER_CANDIDATES = List.of(
            "CF-Connecting-IP",
            "True-Client-IP",
            "X-Forwarded-For",
            "X-Real-IP",
            "X-Original-Forwarded-For",
            "X-Client-IP"
    );

    private ClientIpUtils() {
    }

    public static String extractClientIp(HttpHeaders headers, InetSocketAddress remoteAddress) {
        for (String header : HEADER_CANDIDATES) {
            String value = headers.getFirst(header);
            String ip = extractFirstIpFromHeaderValue(value);
            if (isUsableIp(ip)) {
                return normalizeIp(ip);
            }
        }

        String forwarded = headers.getFirst("Forwarded");
        String forwardedIp = extractFromForwarded(forwarded);
        if (isUsableIp(forwardedIp)) {
            return normalizeIp(forwardedIp);
        }

        if (remoteAddress != null) {
            if (remoteAddress.getAddress() != null) {
                String hostAddress = remoteAddress.getAddress().getHostAddress();
                if (StringUtils.hasText(hostAddress)) {
                    return normalizeIp(hostAddress);
                }
            }
            if (StringUtils.hasText(remoteAddress.getHostString())) {
                return normalizeIp(remoteAddress.getHostString());
            }
        }

        return "unknown";
    }

    public static String buildGuestDisplayName(String ip, String sessionId) {
        if (!isUsableIp(ip) || isLocalOrPrivateIp(ip)) {
            return "游客-" + shortSession(sessionId);
        }
        return desensitizeIp(ip);
    }

    private static String shortSession(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return "匿名";
        }
        String cleaned = sessionId.replace("-", "");
        return cleaned.substring(0, Math.min(cleaned.length(), 6));
    }

    private static String desensitizeIp(String ip) {
        String normalized = normalizeIp(ip);
        String[] parts = normalized.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + ".*.*";
        }
        return normalized.substring(0, Math.min(normalized.length(), 6)) + "***";
    }

    private static boolean isLocalOrPrivateIp(String ip) {
        String normalized = normalizeIp(ip);
        if ("unknown".equalsIgnoreCase(normalized)) {
            return true;
        }
        if (normalized.startsWith("127.") || "0:0:0:0:0:0:0:1".equals(normalized) || "::1".equals(normalized)) {
            return true;
        }
        try {
            InetAddress address = InetAddress.getByName(normalized);
            return address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress();
        } catch (Exception ignored) {
            return true;
        }
    }

    private static boolean isUsableIp(String ip) {
        return StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip.trim());
    }

    private static String extractFirstIpFromHeaderValue(String headerValue) {
        if (!StringUtils.hasText(headerValue)) {
            return null;
        }
        for (String part : headerValue.split(",")) {
            String candidate = sanitizeIpToken(part);
            if (isUsableIp(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static String extractFromForwarded(String forwardedHeader) {
        if (!StringUtils.hasText(forwardedHeader)) {
            return null;
        }
        for (String item : forwardedHeader.split(",")) {
            for (String token : item.split(";")) {
                String trimmed = token.trim();
                if (trimmed.regionMatches(true, 0, "for=", 0, 4)) {
                    String ip = sanitizeIpToken(trimmed.substring(4));
                    if (isUsableIp(ip)) {
                        return ip;
                    }
                }
            }
        }
        return null;
    }

    private static String sanitizeIpToken(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String token = raw.trim();
        if (token.startsWith("\"") && token.endsWith("\"") && token.length() > 1) {
            token = token.substring(1, token.length() - 1);
        }

        // RFC7239: for="[2001:db8:cafe::17]:4711"
        if (token.startsWith("[") && token.contains("]")) {
            int end = token.indexOf(']');
            token = token.substring(1, end);
        } else {
            int firstColon = token.indexOf(':');
            int lastColon = token.lastIndexOf(':');
            // 仅一个冒号时，大概率是 IPv4:port
            if (firstColon > 0 && firstColon == lastColon) {
                String left = token.substring(0, firstColon);
                if (left.chars().allMatch(ch -> Character.isDigit(ch) || ch == '.')) {
                    token = left;
                }
            }
        }

        if (token.startsWith("::ffff:")) {
            token = token.substring("::ffff:".length());
        }

        return token.trim();
    }

    private static String normalizeIp(String ip) {
        if (!StringUtils.hasText(ip)) {
            return "unknown";
        }
        String normalized = ip.trim();
        if (normalized.startsWith("::ffff:")) {
            normalized = normalized.substring("::ffff:".length());
        }
        return normalized;
    }
}
