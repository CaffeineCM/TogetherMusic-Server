package com.togethermusic.upload.repository;

import com.togethermusic.upload.entity.AudioFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface AudioFileRepository extends JpaRepository<AudioFile, Long> {

    List<AudioFile> findByUserIdOrderByUploadedAtDesc(Long userId);

    @Transactional
    void deleteByIdAndUserId(Long id, Long userId);
}
