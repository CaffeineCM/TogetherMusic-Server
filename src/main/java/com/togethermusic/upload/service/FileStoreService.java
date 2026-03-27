package com.togethermusic.upload.service;

import java.io.InputStream;

/**
 * 文件存储服务接口
 * 当前实现：本地文件系统（LocalFileStoreService）
 * 后续可替换为 MinIO（MinioFileStoreService），业务层零改动
 */
public interface FileStoreService {

    /**
     * 存储文件
     *
     * @param inputStream 文件输入流
     * @param filename    原始文件名（用于推断扩展名）
     * @param mimeType    实际 MIME 类型
     * @param size        文件大小（字节）
     * @param userId      所属用户 ID（用于路径隔离）
     * @return storage key，唯一标识存储位置
     */
    String store(InputStream inputStream, String filename, String mimeType, long size, Long userId);

    /**
     * 根据 storage key 生成可访问的 URL
     */
    String getAccessUrl(String storageKey);

    /**
     * 删除文件
     */
    void delete(String storageKey);
}
