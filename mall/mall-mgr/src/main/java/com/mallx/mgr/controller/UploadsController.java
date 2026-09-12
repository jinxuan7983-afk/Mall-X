package com.mallx.mgr.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * 上传文件访问（商品图片等）：/uploads/{type}/{fileName}。
 * 说明：直接用 Path 读取本地文件，避免静态资源处理器在含中文目录下的 URL 编码解析问题；
 * 附带目录穿越防护与浏览器缓存头。
 */
@RestController
public class UploadsController {

    @Value("${mallx.upload.dir:./uploads}")
    private String uploadDir;

    @GetMapping("/uploads/{type}/{fileName}")
    public ResponseEntity<Resource> get(@PathVariable String type, @PathVariable String fileName) {
        Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path target = base.resolve(type).resolve(fileName).normalize();
        // 防目录穿越：目标必须位于上传根目录内
        if (!target.startsWith(base) || !Files.isRegularFile(target)) {
            return ResponseEntity.notFound().build();
        }
        String name = fileName.toLowerCase();
        MediaType mediaType = MediaType.IMAGE_JPEG;
        if (name.endsWith(".png")) {
            mediaType = MediaType.IMAGE_PNG;
        } else if (name.endsWith(".webp")) {
            mediaType = MediaType.parseMediaType("image/webp");
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                .body(new FileSystemResource(target));
    }
}
