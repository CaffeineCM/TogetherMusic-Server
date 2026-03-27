package com.togethermusic.music.dto;

import lombok.Builder;

@Builder
public record MusicPlaylistSummary(
        String id,
        String name,
        String coverUrl,
        String creatorName,
        Long trackCount,
        Long playCount,
        String description,
        String source
) {
}
