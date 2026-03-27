package com.togethermusic.upload.service;

import cn.dev33.satoken.stp.StpUtil;
import com.togethermusic.common.code.ErrorCode;
import com.togethermusic.common.exception.BusinessException;
import com.togethermusic.config.TogetherMusicProperties;
import com.togethermusic.music.model.Music;
import com.togethermusic.upload.entity.AudioFile;
import com.togethermusic.upload.repository.AudioFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UploadService {

    private static final int MAGIC_BYTES_LENGTH = 12;

    private final FileStoreService fileStoreService;
    private final AudioFileRepository audioFileRepository;
    private final AudioMetadataParser metadataParser;
    private final TogetherMusicProperties properties;

    /**
     * 上传音频文件
     */
    @Transactional
    public AudioFile upload(MultipartFile file) {
        long userId = StpUtil.getLoginIdAsLong();

        // 文件大小校验
        long maxSize = properties.getUpload().getMaxFileSize();
        if (file.getSize() > maxSize) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE,
                    "文件大小不能超过 " + (maxSize / 1024 / 1024) + "MB");
        }

        // 读取文件头，验证 MIME 类型（不依赖扩展名）
        byte[] header = readHeader(file);
        String detectedMime = metadataParser.detectMimeType(header);
        if (detectedMime == null || !properties.getUpload().getAllowedMimeTypes().contains(detectedMime)) {
            throw new BusinessException(ErrorCode.FILE_FORMAT_NOT_SUPPORTED,
                    "不支持的文件格式，仅支持 mp3/flac/wav/ogg/m4a");
        }

        // 解析元数据（需要临时文件，JAudioTagger 要求 File 对象）
        AudioMetadataParser.AudioMeta meta = parseMetadata(file, detectedMime);

        // 存储文件
        String storageKey;
        try {
            storageKey = fileStoreService.store(
                    file.getInputStream(),
                    file.getOriginalFilename(),
                    detectedMime,
                    file.getSize(),
                    userId
            );
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件存储失败");
        }

        String accessUrl = fileStoreService.getAccessUrl(storageKey);

        // 持久化元数据
        AudioFile audioFile = new AudioFile();
        audioFile.setUserId(userId);
        audioFile.setOriginalFilename(file.getOriginalFilename());
        audioFile.setStorageKey(storageKey);
        audioFile.setAccessUrl(accessUrl);
        audioFile.setMimeType(detectedMime);
        audioFile.setDuration(meta.durationMs());
        audioFile.setFileSize(file.getSize());
        audioFile.setTitle(meta.title());
        audioFile.setArtist(meta.artist());

        AudioFile saved = audioFileRepository.save(audioFile);
        log.info("Audio uploaded: id={}, title={}, user={}", saved.getId(), meta.title(), userId);
        return saved;
    }

    /**
     * 查询当前用户的上传列表
     */
    public List<AudioFile> listMyUploads() {
        long userId = StpUtil.getLoginIdAsLong();
        return audioFileRepository.findByUserIdOrderByUploadedAtDesc(userId);
    }

    /**
     * 删除上传的音频文件
     */
    @Transactional
    public void delete(Long fileId) {
        long userId = StpUtil.getLoginIdAsLong();
        AudioFile audioFile = audioFileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));

        if (!audioFile.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权删除他人的文件");
        }

        fileStoreService.delete(audioFile.getStorageKey());
        audioFileRepository.delete(audioFile);
        log.info("Audio deleted: id={}, user={}", fileId, userId);
    }

    /**
     * 将上传的音频文件转换为 Music 对象（用于点歌）
     */
    public Music toMusic(AudioFile audioFile) {
        return Music.builder()
                .id("upload_" + audioFile.getId())
                .name(audioFile.getTitle())
                .artist(audioFile.getArtist())
                .duration(audioFile.getDuration())
                .url(audioFile.getAccessUrl())
                .source("upload")
                .quality("original")
                .build();
    }

    private byte[] readHeader(MultipartFile file) {
        try {
            byte[] header = new byte[MAGIC_BYTES_LENGTH];
            try (var is = file.getInputStream()) {
                int read = is.read(header);
                if (read < MAGIC_BYTES_LENGTH) {
                    byte[] trimmed = new byte[read];
                    System.arraycopy(header, 0, trimmed, 0, read);
                    return trimmed;
                }
            }
            return header;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "读取文件失败");
        }
    }

    private AudioMetadataParser.AudioMeta parseMetadata(MultipartFile file, String mimeType) {
        File tempFile = null;
        try {
            String suffix = "." + mimeType.split("/")[1];
            tempFile = Files.createTempFile("tm_upload_", suffix).toFile();
            file.transferTo(tempFile);
            return metadataParser.parse(tempFile, file.getOriginalFilename());
        } catch (IOException e) {
            log.warn("Failed to create temp file for metadata parsing: {}", e.getMessage());
            return new AudioMetadataParser.AudioMeta(
                    file.getOriginalFilename(), "未知艺术家", null);
        } finally {
            if (tempFile != null) {
                tempFile.delete();
            }
        }
    }
}
