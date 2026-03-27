package com.togethermusic.upload.service;

import lombok.extern.slf4j.Slf4j;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Map;

/**
 * 音频文件元数据解析器
 * 使用 JAudioTagger 解析 title、artist、duration
 */
@Slf4j
@Component
public class AudioMetadataParser {

    /** 支持的 MIME 类型及其 magic bytes（文件头魔数） */
    private static final Map<String, byte[]> MAGIC_BYTES = Map.of(
            "audio/mpeg",  new byte[]{(byte) 0xFF, (byte) 0xFB},          // MP3
            "audio/flac",  new byte[]{0x66, 0x4C, 0x61, 0x43},            // fLaC
            "audio/wav",   new byte[]{0x52, 0x49, 0x46, 0x46},            // RIFF
            "audio/ogg",   new byte[]{0x4F, 0x67, 0x67, 0x53},            // OggS
            "audio/mp4",   new byte[]{0x00, 0x00, 0x00}                   // ftyp (宽松匹配)
    );

    /**
     * 通过 magic bytes 验证文件格式，返回实际 MIME 类型
     * 不依赖文件扩展名
     */
    public String detectMimeType(byte[] header) {
        for (Map.Entry<String, byte[]> entry : MAGIC_BYTES.entrySet()) {
            byte[] magic = entry.getValue();
            if (startsWith(header, magic)) {
                return entry.getKey();
            }
        }
        // MP3 ID3 标签头
        if (header.length >= 3 && header[0] == 0x49 && header[1] == 0x44 && header[2] == 0x33) {
            return "audio/mpeg";
        }
        return null;
    }

    /**
     * 解析音频文件元数据
     */
    public AudioMeta parse(File file, String originalFilename) {
        try {
            AudioFile audioFile = AudioFileIO.read(file);
            long durationMs = (long) audioFile.getAudioHeader().getTrackLength() * 1000;

            Tag tag = audioFile.getTag();
            String title = null;
            String artist = null;

            if (tag != null) {
                title = tag.getFirst(FieldKey.TITLE);
                artist = tag.getFirst(FieldKey.ARTIST);
            }

            // 元数据缺失时使用文件名兜底
            if (title == null || title.isBlank()) {
                title = stripExtension(originalFilename);
            }
            if (artist == null || artist.isBlank()) {
                artist = "未知艺术家";
            }

            return new AudioMeta(title, artist, durationMs);
        } catch (Exception e) {
            log.warn("Failed to parse audio metadata for {}: {}", originalFilename, e.getMessage());
            return new AudioMeta(stripExtension(originalFilename), "未知艺术家", null);
        }
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }

    private String stripExtension(String filename) {
        if (filename == null) return "未知歌曲";
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    public record AudioMeta(String title, String artist, Long durationMs) {}
}
