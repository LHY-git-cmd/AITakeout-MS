package com.sky.service.profile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 开发环境头像相对目录存储契约。 */
class LocalAvatarStorageTest {
    @TempDir
    Path tempDir;

    @Test
    void storesAvatarUnderConfiguredRootAndReturnsProxyUrl() throws Exception {
        LocalAvatarStorage storage = new LocalAvatarStorage(tempDir.toString());

        String url = storage.upload(new byte[]{1, 2, 3}, "user-avatar/7/avatar.png");

        assertThat(url).isEqualTo("/api/uploads/user-avatar/7/avatar.png");
        assertThat(Files.readAllBytes(tempDir.resolve("user-avatar/7/avatar.png")))
                .containsExactly(1, 2, 3);
    }

    @Test
    void rejectsObjectNameOutsideConfiguredRoot() {
        LocalAvatarStorage storage = new LocalAvatarStorage(tempDir.toString());

        assertThatThrownBy(() -> storage.upload(new byte[]{1}, "../outside.png"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
