package com.pet.platform.module.upload.controller;

import com.pet.platform.common.BusinessException;
import com.pet.platform.common.Result;
import com.pet.platform.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;

@RestController
@RequestMapping("/admin/common")
@RequiredArgsConstructor
public class UploadController {

    private final StorageService storageService;

    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择图片");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".jpg") && !name.endsWith(".jpeg") && !name.endsWith(".png")) {
            throw new BusinessException("仅支持 jpg / png 格式");
        }
        if (file.getSize() > 2 * 1024 * 1024) {
            throw new BusinessException("图片大小不能超过 2MB");
        }
        return Result.ok(storageService.upload(file));
    }
}
