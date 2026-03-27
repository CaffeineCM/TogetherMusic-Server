package com.togethermusic.upload.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "tm_audio_file")
@Getter
@Setter
@NoArgsConstructor
public class AudioFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    @Column(name = "access_url", nullable = false, length = 512)
    private String accessUrl;

    @Column(name = "mime_type", nullable = false, length = 64)
    private String mimeType;

    @Column
    private Long duration;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(length = 255)
    private String title;

    @Column(length = 255)
    private String artist;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private OffsetDateTime uploadedAt;

    @PrePersist
    void prePersist() {
        this.uploadedAt = OffsetDateTime.now();
    }
}
