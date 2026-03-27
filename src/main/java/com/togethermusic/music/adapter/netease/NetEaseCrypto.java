package com.togethermusic.music.adapter.netease;

import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class NetEaseCrypto {

    private static final String PRESET_KEY = "0CoJUm6Qyw8W8jud";
    private static final String PUB_KEY = "010001";
    private static final String MODULUS = "00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e0312ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932575cce10b424d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b3ece0462db0a22b8e7";
    private static final String IV = "0102030405060708";
    private static final String EAPI_KEY = "e82ckenh8dichen8";

    public String eapiEncrypt(String url, String content) {
        String message = "nobody" + url + "use" + content + "md5forencrypt";
        String digest = DigestUtils.md5DigestAsHex(message.getBytes(StandardCharsets.UTF_8));
        String data = url + "-36cd479b6b5-" + content + "-36cd479b6b5-" + digest;
        return aesEncrypt(data, EAPI_KEY, AesEncryptMode.ECB, "").toUpperCase();
    }

    public String[] weapiEncrypt(String content) {
        String key = createSecretKey();
        String encText = aesEncrypt(
                aesEncrypt(content, PRESET_KEY, AesEncryptMode.CBC, IV),
                key,
                AesEncryptMode.CBC,
                IV
        );
        String encSecKey = rsaEncrypt(key);
        return new String[]{encText, encSecKey};
    }

    private String createSecretKey() {
        String keys = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder key = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            int index = (int) Math.floor(Math.random() * keys.length());
            key.append(keys.charAt(index));
        }
        return key.toString();
    }

    private String aesEncrypt(String content, String key, AesEncryptMode mode, String iv) {
        try {
            Cipher cipher = Cipher.getInstance("AES/" + mode.type() + "/PKCS5Padding");
            byte[] bytes;
            if (mode == AesEncryptMode.CBC) {
                cipher.init(
                        Cipher.ENCRYPT_MODE,
                        new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES"),
                        new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8))
                );
                bytes = cipher.doFinal(content.getBytes(StandardCharsets.UTF_8));
                return Base64.getEncoder().encodeToString(bytes);
            }

            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES")
            );
            bytes = cipher.doFinal(content.getBytes(StandardCharsets.UTF_8));
            return toHex(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt NetEase payload", e);
        }
    }

    private String rsaEncrypt(String text) {
        String reversed = new StringBuilder(text).reverse().toString();
        BigInteger biText = new BigInteger(strToHex(reversed), 16);
        BigInteger biEx = new BigInteger(PUB_KEY, 16);
        BigInteger biMod = new BigInteger(MODULUS, 16);
        BigInteger biRet = biText.modPow(biEx, biMod);
        return zFill(biRet.toString(16));
    }

    private String zFill(String str) {
        StringBuilder builder = new StringBuilder(str);
        while (builder.length() < 256) {
            builder.insert(0, "0");
        }
        return builder.toString();
    }

    private String strToHex(String s) {
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            str.append(Integer.toHexString(s.charAt(i)));
        }
        return str.toString();
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
