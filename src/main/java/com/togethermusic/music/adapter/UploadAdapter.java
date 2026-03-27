package com.togethermusic.music.adapter;

import com.togethermusic.common.code.ErrorCode;
import com.togethermusic.common.exception.BusinessException;
import com.togethermusic.music.model.Music;
import com.togethermusic.upload.entity.AudioFile;
import com.togethermusic.upload.repository.AudioFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 用户上传音频适配器
 * source="upload"，id 格式为 "upload_{fileId}"
 */
@Component
@RequiredArgsConstructor
public class UploadAdapter implements MusicSourceAdapter {

    private final AudioFileRepository audioFileRepository;

    @Override
    public String sourceCode() {
        return "upload";
    }

    @Override
    public Music search(String keyword, String quality, String userToken) {
        // 上传音频不支持关键词搜索，通过 getById 直接获取
        throw new BusinessException(ErrorCode.BAD_REQUEST, "上传音频请通过文件 ID 点歌");
    }

    @Override
    public Music getById(String id, String quality, String userToken) {
        // id 格式：upload_{fileId}
        Long fileId = parseFileId(id);
        AudioFile audioFile = audioFileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "上传文件不存在"));

        return Music.builder()
                .id(id)
                .name(audioFile.getTitle())
                .artist(audioFile.getArtist())
                .duration(audioFile.getDuration())
                .url(audioFile.getAccessUrl())
                .source("upload")
                .quality("original")
                .build();
    }

    @Override
    public List<Music> getPlaylist(String playlistId, String userToken) {
        return List.of();
    }

    private Long parseFileId(String id) {
        try {
            return Long.parseLong(id.replace("upload_", ""));
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的文件 ID: " + id);
        }
    }
}
