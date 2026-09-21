package com.sky.service.profile;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 开发环境头像存储：将对象写入项目运行目录下的相对目录，避免本地开发依赖云 OSS。
 */
@Component
@Profile("dev")
public class LocalAvatarStorage implements AvatarStorage {
    private final Path root;

    public LocalAvatarStorage(@Value("${sky.avatar.local-root:data/uploads}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    @Override
    public String upload(byte[] content, String objectName) {
        Path target = root.resolve(objectName).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("头像存储路径无效");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException ex) {
            throw new IllegalStateException("头像保存失败", ex);
        }
        // 返回经过 API 代理的相对地址，浏览器和生产网关都能访问。
        return "/api/uploads/" + objectName.replace('\\', '/');
    }
}
