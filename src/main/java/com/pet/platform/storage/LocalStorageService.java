package com.pet.platform.storage;

import com.pet.platform.common.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 本地存储实现，开发环境默认启用；生产可切换为 OSS（app.upload.type=oss）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.upload.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private final UploadProperties properties;

    @Override
    public String upload(MultipartFile file) {
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.')).toLowerCase() : ".jpg";
        String day = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String filename = UUID.randomUUID().toString().replace("-", "") + ext;
        Path dir = Paths.get(properties.getDir(), day.split("/")).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(filename).toFile());
        } catch (IOException e) {
            log.error("保存上传文件失败", e);
            throw new BusinessException("图片上传失败，请稍后重试");
        }
        return properties.getBaseUrl() + "/uploads/" + day + "/" + filename;
    }
}
