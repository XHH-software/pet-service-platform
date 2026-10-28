package com.pet.platform.storage;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    /**
     * 上传图片并返回可访问的完整 URL。
     */
    String upload(MultipartFile file);
}
