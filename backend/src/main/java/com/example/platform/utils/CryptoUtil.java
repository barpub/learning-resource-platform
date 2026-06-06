package com.example.platform.utils;

import com.example.platform.common.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Simple AES/ECB utility used to protect sensitive settings such as FTP passwords.
 * The secret is derived from the JWT secret so the platform does not need an extra config entry.
 */
@Component
public class CryptoUtil {
    private static final String PREFIX = "enc:";
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";

    private final SecretKeySpec keySpec;

    public CryptoUtil(@Value("${jwt.secret:learning-resource-platform-default-secret}") String secret) {
        this.keySpec = buildKey(secret);
    }

    public String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] bytes = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(bytes);
        } catch (Exception ex) {
            throw new BusinessException(500, "加密失败：" + ex.getMessage());
        }
    }

    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) {
            return null;
        }
        if (!cipherText.startsWith(PREFIX)) {
            return cipherText;
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decoded = Base64.getDecoder().decode(cipherText.substring(PREFIX.length()));
            return new String(cipher.doFinal(decoded), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new BusinessException(500, "解密失败：" + ex.getMessage());
        }
    }

    private SecretKeySpec buildKey(String secret) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha.digest(secret.getBytes(StandardCharsets.UTF_8));
            byte[] key = new byte[16];
            System.arraycopy(hash, 0, key, 0, 16);
            return new SecretKeySpec(key, ALGORITHM);
        } catch (Exception ex) {
            throw new BusinessException(500, "初始化加密密钥失败");
        }
    }
}
