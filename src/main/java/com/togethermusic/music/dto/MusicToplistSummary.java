package com.togethermusic.music.dto;

import lombok.Builder;

@Builder
public record MusicToplistSummary(
        String id,
        String name,
        String coverUrl,
        String description,
        String updateFrequency,
        String source
) {
}
