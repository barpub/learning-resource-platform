package com.example.platform.security;

import com.example.platform.entity.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtUtil {
    @Value("${jwt.secret}")
    private String secret;
    @Value("${jwt.expiration}")
    private long expirationMs;

    public String generateToken(User user) {
        long exp = Instant.now().toEpochMilli() + expirationMs;
        String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = base64Url(("{\"sub\":\"" + escape(user.getUsername()) + "\",\"uid\":" + user.getId()
                + ",\"role\":\"" + escape(user.getRole()) + "\",\"exp\":" + exp + "}").getBytes(StandardCharsets.UTF_8));
        String content = header + "." + payload;
        return content + "." + sign(content);
    }

    public String getUsername(String token) {
        String payload = payload(token);
        return extractString(payload, "sub");
    }

    public boolean validate(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return false;
            }
            String content = parts[0] + "." + parts[1];
            if (!MessageDigest.isEqual(sign(content).getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8))) {
                return false;
            }
            long exp = extractLong(payload(token), "exp");
            return exp > Instant.now().toEpochMilli();
        } catch (Exception ex) {
            return false;
        }
    }

    private String payload(String token) {
        String[] parts = token.split("\\.");
        return new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
    }

    private String sign(String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return base64Url(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("JWT 签名失败", ex);
        }
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String extractString(String json, String key) {
        String marker = "\"" + key + "\":\"";
        int start = json.indexOf(marker);
        if (start < 0) {
            return null;
        }
        start += marker.length();
        int end = json.indexOf('"', start);
        return end < 0 ? null : json.substring(start, end);
    }

    private long extractLong(String json, String key) {
        String marker = "\"" + key + "\":";
        int start = json.indexOf(marker);
        if (start < 0) {
            return 0;
        }
        start += marker.length();
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        return Long.parseLong(json.substring(start, end));
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
