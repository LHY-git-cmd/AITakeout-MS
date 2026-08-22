package com.sky.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "sky.web-login")
@Data
public class WebLoginProperties {

    private boolean enabled = false;
    private String verificationCode;
}
