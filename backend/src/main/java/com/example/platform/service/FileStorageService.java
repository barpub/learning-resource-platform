package com.example.platform.service;

import com.example.platform.common.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            "exe", "bat", "cmd", "sh", "jsp", "php", "jar", "war", "msi", "ps1"
    );

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    public StoredFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "上传文件不能为空");
        }
        String original = file.getOriginalFilename() == null ? "file" : Paths.get(file.getOriginalFilename()).getFileName().toString();
        String extension = extension(original);
        if (BLOCKED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(400, "不支持的文件类型");
        }
        try {
            Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String storedName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
            Path target = dir.resolve(storedName).normalize();
            if (!target.startsWith(dir)) {
                throw new BusinessException(400, "非法文件路径");
            }
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target);
            }
            return new StoredFile(original, target.toString(), file.getSize(), contentType(file));
        } catch (IOException ex) {
            throw new BusinessException(500, "文件保存失败");
        }
    }

    public Path resolve(String filePath) {
        Path path = Paths.get(filePath).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            throw new BusinessException(404, "文件不存在");
        }
        return path;
    }

    private String extension(String name) {
        int idx = name.lastIndexOf('.');
        if (idx < 0 || idx == name.length() - 1) {
            throw new BusinessException(400, "文件缺少扩展名");
        }
        return name.substring(idx + 1).toLowerCase(Locale.ROOT);
    }

    private String contentType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }
        return contentType;
    }

    public static class StoredFile {
        private final String originalName;
        private final String path;
        private final long size;
        private final String type;

        public StoredFile(String originalName, String path, long size, String type) {
            this.originalName = originalName;
            this.path = path;
            this.size = size;
            this.type = type;
        }

        public String getOriginalName() {
            return originalName;
        }

        public String getPath() {
            return path;
        }

        public long getSize() {
            return size;
        }

        public String getType() {
            return type;
        }
    }
}
