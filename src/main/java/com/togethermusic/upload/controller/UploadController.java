package com.togethermusic.upload.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.togethermusic.common.response.Response;
import com.togethermusic.upload.entity.AudioFile;
import com.togethermusic.upload.service.UploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/upload")
@SaCheckLogin
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;

    /**
     * 上传音频文件
     * POST /api/v1/upload/audio
     * Content-Type: multipart/form-data
     */
    @PostMapping("/audio")
    public Response<AudioFile> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Response.failure(400, "文件不能为空");
        }
        AudioFile result = uploadService.upload(file);
        return Response.success(result, "上传成功");
    }

    /**
     * 查询我的上传列表
     * GET /api/v1/upload/audio/list
     */
    @GetMapping("/audio/list")
    public Response<List<AudioFile>> list() {
        return Response.success(uploadService.listMyUploads());
    }

    /**
     * 删除上传的音频
     * DELETE /api/v1/upload/audio/{fileId}
     */
    @DeleteMapping("/audio/{fileId}")
    public Response<Void> delete(@PathVariable Long fileId) {
        uploadService.delete(fileId);
        return Response.success(null, "删除成功");
    }
}
