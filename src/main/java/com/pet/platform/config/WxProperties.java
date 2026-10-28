package com.pet.platform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信小程序登录配置。开发环境 mock=true 时不请求微信服务器。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.wx")
public class WxProperties {

    private String appId;

    private String appSecret;

    private boolean mock = true;
}
