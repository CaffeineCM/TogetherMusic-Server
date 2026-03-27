package com.togethermusic.upload.service;

import com.togethermusic.config.TogetherMusicProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * 本地文件系统存储实现
 * 激活条件：together-music.storage.type=local（默认）
 * storage key 格式：{userId}/{uuid}.{ext}
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "together-music.storage.type", havingValue = "local", matchIfMissing = true)
@RequiredArgsConstructor
public class LocalFileStoreService implements FileStoreService {

    private final TogetherMusicProperties properties;

    @Override
    public String store(InputStream inputStream, String filename, String mimeType, long size, Long userId) {
        String ext = extractExtension(filename, mimeType);
        String storageKey = userId + "/" + UUID.randomUUID() + "." + ext;
        Path targetPath = Paths.get(properties.getStorage().getLocal().getBaseDir(), storageKey);

        try {
            Files.createDirectories(targetPath.getParent());
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("File stored: {}", storageKey);
            return storageKey;
        } catch (IOException e) {
            throw new RuntimeException("文件存储失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String getAccessUrl(String storageKey) {
        String baseUrl = properties.getStorage().getLocal().getBaseUrl();
        return baseUrl.endsWith("/") ? baseUrl + storageKey : baseUrl + "/" + storageKey;
    }

    @Override
    public void delete(String storageKey) {
        Path path = Paths.get(properties.getStorage().getLocal().getBaseDir(), storageKey);
        try {
            Files.deleteIfExists(path);
            log.info("File deleted: {}", storageKey);
        } catch (IOException e) {
            log.warn("Failed to delete file {}: {}", storageKey, e.getMessage());
        }
    }

    private String extractExtension(String filename, String mimeType) {
        // 优先从文件名取扩展名
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        }
        // 从 MIME 类型推断
        return switch (mimeType) {
            case "audio/mpeg" -> "mp3";
            case "audio/flac" -> "flac";
            case "audio/wav" -> "wav";
            case "audio/ogg" -> "ogg";
            case "audio/mp4" -> "m4a";
            default -> "bin";
        };
    }
}
