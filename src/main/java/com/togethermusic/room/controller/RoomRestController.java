package com.togethermusic.room.controller;

import com.togethermusic.common.response.Response;
import com.togethermusic.music.dto.PlaybackSnapshot;
import com.togethermusic.music.model.Music;
import com.togethermusic.music.service.MusicService;
import com.togethermusic.repository.RoomRedisRepository;
import com.togethermusic.room.dto.RoomSummary;
import com.togethermusic.room.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
public class RoomRestController {

    private final RoomService roomService;
    private final RoomRedisRepository roomRepository;
    private final MusicService musicService;

    @GetMapping
    public Response<List<RoomSummary>> listRooms() {
        return Response.success(roomService.listRooms());
    }

    @GetMapping("/{houseId}/playing")
    public Response<Music> getCurrentPlaying(@PathVariable String houseId) {
        return Response.success(roomRepository.getPlaying(houseId).orElse(null));
    }

    @GetMapping("/{houseId}/playback")
    public Response<PlaybackSnapshot> getPlaybackSnapshot(@PathVariable String houseId) {
        return Response.success(musicService.getPlaybackSnapshot(houseId));
    }
}
