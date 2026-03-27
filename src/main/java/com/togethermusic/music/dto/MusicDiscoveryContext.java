package com.togethermusic.music.dto;

import lombok.Builder;

@Builder
public record MusicDiscoveryContext(
        boolean canViewHostPlaylists,
        String playlistSource
) {
}
