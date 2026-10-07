package com.sky.controller;

import com.sky.service.storage.LocalProductImageStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/** 管理端与用户端共用的公开商品图片读取接口。 */
@RestController
@RequestMapping({"/uploads/products", "/admin/uploads/products"})
@RequiredArgsConstructor
public class ProductImageController {
    private final LocalProductImageStorage storage;

    @GetMapping("/{id}")
    public ResponseEntity<byte[]> image(@PathVariable String id,
                                        @RequestParam("ext") String extension) {
        LocalProductImageStorage.StoredImage image = storage.load(id, extension);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .contentType(image.mediaType())
                .body(image.content());
    }
}
