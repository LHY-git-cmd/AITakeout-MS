package com.sky.service.storage;

import com.sky.exception.BaseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 将菜品和套餐图片保存到运行目录下的相对目录。 */
@Service
public class LocalProductImageStorage {
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final int MAX_DIMENSION = 4096;
    private static final Set<String> EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Map<String, MediaType> MEDIA_TYPES = Map.of(
            "jpg", MediaType.IMAGE_JPEG, "jpeg", MediaType.IMAGE_JPEG,
            "png", MediaType.IMAGE_PNG, "webp", MediaType.parseMediaType("image/webp"));

    private final Path root;

    public LocalProductImageStorage(
            @Value("${sky.product-image.local-root:data/uploads/products}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    /** 校验并保存图片，返回可写入数据库的相对访问地址。 */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BaseException("图片不能为空");
        if (file.getSize() > MAX_BYTES) throw new BaseException("图片大小不能超过5MB");
        String extension = extension(file.getOriginalFilename());
        try {
            byte[] content = file.getBytes();
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
            if (image == null) throw new BaseException("文件不是有效图片");
            if (image.getWidth() <= 0 || image.getHeight() <= 0
                    || image.getWidth() > MAX_DIMENSION || image.getHeight() > MAX_DIMENSION) {
                throw new BaseException("图片尺寸必须在1到4096像素之间");
            }
            Files.createDirectories(root);
            String id = UUID.randomUUID().toString().replace("-", "");
            Path target = root.resolve(id + "." + extension).normalize();
            if (!target.startsWith(root)) throw new BaseException("图片存储路径无效");
            Files.write(target, content, StandardOpenOption.CREATE_NEW);
            // 路径不带扩展名，避免被现有Nginx图片正则优先当作管理端静态文件。
            return "/api/uploads/products/" + id + "?ext=" + extension;
        } catch (BaseException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BaseException("图片保存失败");
        }
    }

    /** 按受控ID和扩展名读取图片，避免目录穿越。 */
    public StoredImage load(String id, String extension) {
        if (id == null || !id.matches("[a-f0-9]{32}")) throw new BaseException("图片地址无效");
        String normalizedExtension = normalizeExtension(extension);
        Path target = root.resolve(id + "." + normalizedExtension).normalize();
        if (!target.startsWith(root) || !Files.isRegularFile(target)) throw new BaseException("图片不存在");
        try {
            return new StoredImage(Files.readAllBytes(target), MEDIA_TYPES.get(normalizedExtension));
        } catch (IOException exception) {
            throw new BaseException("图片读取失败");
        }
    }

    private String extension(String originalName) {
        if (originalName == null || !originalName.contains(".")) throw new BaseException("图片扩展名无效");
        return normalizeExtension(originalName.substring(originalName.lastIndexOf('.') + 1));
    }

    private String normalizeExtension(String value) {
        String extension = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!EXTENSIONS.contains(extension)) throw new BaseException("仅支持JPG、PNG和WEBP图片");
        return extension;
    }

    public record StoredImage(byte[] content, MediaType mediaType) { }
}
