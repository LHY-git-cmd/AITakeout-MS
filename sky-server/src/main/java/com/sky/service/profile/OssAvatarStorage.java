package com.sky.service.profile;

import com.sky.utils.AliOssUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 将已校验头像写入阿里云 OSS。 */
@Component
@Profile("!dev")
@RequiredArgsConstructor
public class OssAvatarStorage implements AvatarStorage {
    private final AliOssUtil aliOssUtil;

    @Override
    public String upload(byte[] content, String objectName) {
        return aliOssUtil.upload(content, objectName);
    }
}
