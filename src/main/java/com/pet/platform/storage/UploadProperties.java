package com.pet.platform.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.upload")
public class UploadProperties {

    /** local 或 oss */
    private String type = "local";
    private String dir = "./uploads";
    private String baseUrl = "http://localhost:8080";
    private Oss oss = new Oss();

    @Data
    public static class Oss {
        private String endpoint;
        private String bucket;
        private String accessKeyId;
        private String accessKeySecret;
    }
}
