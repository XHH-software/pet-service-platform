package com.pet.platform.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.pet.platform.common.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 阿里云 OSS 存储实现，配置 app.upload.type=oss 且填写密钥后启用。
 */
@Service
@ConditionalOnProperty(name = "app.upload.type", havingValue = "oss")
public class OssStorageService implements StorageService {

    private final UploadProperties properties;

    public OssStorageService(UploadProperties properties) {
        this.properties = properties;
    }

    @Override
    public String upload(MultipartFile file) {
        UploadProperties.Oss oss = properties.getOss();
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.')) : ".jpg";
        String day = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String key = "uploads/" + day + "/" + UUID.randomUUID().toString().replace("-", "") + ext;
        OSS client = new OSSClientBuilder().build(oss.getEndpoint(), oss.getAccessKeyId(), oss.getAccessKeySecret());
        try (InputStream in = file.getInputStream()) {
            client.putObject(oss.getBucket(), key, in);
            return "https://" + oss.getBucket() + "." + oss.getEndpoint() + "/" + key;
        } catch (Exception e) {
            throw new BusinessException("OSS 上传失败：" + e.getMessage());
        } finally {
            client.shutdown();
        }
    }
}
