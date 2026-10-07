package com.sky.service.storage;

import com.sky.exception.BaseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 本地商品图片存储的路径、格式和安全边界测试。 */
class LocalProductImageStorageTest {
    @TempDir Path tempDir;

    @Test
    void storesAndLoadsValidPng() throws Exception {
        LocalProductImageStorage storage = new LocalProductImageStorage(tempDir.toString());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(16, 12, BufferedImage.TYPE_INT_RGB), "png", output);

        String url = storage.store(new MockMultipartFile(
                "file", "dish.png", "image/png", output.toByteArray()));
        String id = url.substring("/api/uploads/products/".length(), url.indexOf('?'));
        LocalProductImageStorage.StoredImage loaded = storage.load(id, "png");

        assertThat(url).matches("/api/uploads/products/[a-f0-9]{32}\\?ext=png");
        assertThat(loaded.content()).isEqualTo(output.toByteArray());
        assertThat(loaded.mediaType().toString()).isEqualTo("image/png");
    }

    @Test
    void rejectsFakeImage() {
        LocalProductImageStorage storage = new LocalProductImageStorage(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file", "fake.png", "image/png", "not-an-image".getBytes());
        assertThatThrownBy(() -> storage.store(file))
                .isInstanceOf(BaseException.class).hasMessage("文件不是有效图片");
    }

    @Test
    void rejectsPathTraversal() {
        LocalProductImageStorage storage = new LocalProductImageStorage(tempDir.toString());
        assertThatThrownBy(() -> storage.load("../secret", "png"))
                .isInstanceOf(BaseException.class).hasMessage("图片地址无效");
    }
}
