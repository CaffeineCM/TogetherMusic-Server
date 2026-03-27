package com.togethermusic.music.dto;

import com.togethermusic.music.model.Music;
import lombok.Builder;

@Builder
public record PlaybackSnapshot(
        Music music,
        String status,
        long positionMs,
        long updatedAt,
        long serverTime
) {}
