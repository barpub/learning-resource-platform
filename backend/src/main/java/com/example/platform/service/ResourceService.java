package com.example.platform.service;

import com.example.platform.common.BusinessException;
import com.example.platform.common.PageResult;
import com.example.platform.dto.ResourceDTO;
import com.example.platform.entity.DownloadRecord;
import com.example.platform.entity.Resource;
import com.example.platform.entity.User;
import com.example.platform.entity.ViewHistory;
import com.example.platform.mapper.DownloadRecordMapper;
import com.example.platform.mapper.FavoriteMapper;
import com.example.platform.mapper.ResourceMapper;
import com.example.platform.mapper.ViewHistoryMapper;
import com.example.platform.security.CurrentUser;
import com.example.platform.utils.RedisUtil;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ResourceService {
    private final ResourceMapper resourceMapper;
    private final FavoriteMapper favoriteMapper;
    private final DownloadRecordMapper downloadRecordMapper;
    private final FileStorageService fileStorageService;
    private final RedisUtil redisUtil;
    private final ViewHistoryMapper viewHistoryMapper;
    private static final int MAX_TAG_COUNT = 6;
    private static final int MAX_TAG_LENGTH = 16;
    private static final int MAX_TAGS_TEXT_LENGTH = 128;

    public ResourceService(ResourceMapper resourceMapper, FavoriteMapper favoriteMapper,
                           DownloadRecordMapper downloadRecordMapper, FileStorageService fileStorageService,
                           RedisUtil redisUtil, ViewHistoryMapper viewHistoryMapper) {
        this.resourceMapper = resourceMapper;
        this.favoriteMapper = favoriteMapper;
        this.downloadRecordMapper = downloadRecordMapper;
        this.fileStorageService = fileStorageService;
        this.redisUtil = redisUtil;
        this.viewHistoryMapper = viewHistoryMapper;
    }

    public PageResult<Resource> page(int page, int size, String keyword, Long categoryId, String sort, String order) {
        int current = Math.max(page, 1);
        int pageSize = Math.min(Math.max(size, 1), 50);
        int offset = (current - 1) * pageSize;
        List<Resource> records = resourceMapper.findPage(keyword, categoryId, sort, order, offset, pageSize);
        long total = resourceMapper.countPage(keyword, categoryId);
        return new PageResult<>(records, total, current, pageSize);
    }

    public Resource get(Long id) {
        Resource resource = resourceMapper.findById(id);
        if (resource == null || Integer.valueOf(0).equals(resource.getStatus())) {
            throw new BusinessException(404, "资源不存在");
        }
        resourceMapper.incrementViewCount(id);
        Long userId = CurrentUser.id();
        if (userId != null) {
            ViewHistory history = new ViewHistory();
            history.setUserId(userId);
            history.setResourceId(id);
            history.setViewDuration(0);
            viewHistoryMapper.insert(history);
        }
        resource.setFavorite(userId != null && favoriteMapper.findByUserAndResource(userId, id) != null);
        redisUtil.set("resource:" + id, resource, 3600);
        return resource;
    }

    @Transactional
    public Resource upload(ResourceDTO dto, MultipartFile file, Long userId) {
        FileStorageService.StoredFile storedFile = fileStorageService.store(file);
        Resource resource = new Resource();
        resource.setTitle(dto.getTitle());
        resource.setDescription(dto.getDescription());
        resource.setCategoryId(dto.getCategoryId());
        resource.setTags(normalizeTags(dto.getTags()));
        resource.setUserId(userId);
        resource.setFileName(storedFile.getOriginalName());
        resource.setFilePath(storedFile.getPath());
        resource.setFileSize(storedFile.getSize());
        resource.setFileType(storedFile.getType());
        resource.setResourceType("FILE");
        resource.setStatus(1);
        resourceMapper.insert(resource);
        return resourceMapper.findById(resource.getId());
    }

    @Transactional
    public Resource uploadBatch(ResourceDTO dto, List<MultipartFile> files, List<String> relativePaths, Long userId) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException(400, "请选择要上传的文件");
        }

        List<FileStorageService.StoredFile> storedFiles = new ArrayList<>();
        long totalSize = 0;
        for (MultipartFile file : files) {
            FileStorageService.StoredFile storedFile = fileStorageService.store(file);
            storedFiles.add(storedFile);
            totalSize += storedFile.getSize();
        }

        Resource folder = new Resource();
        folder.setTitle(dto.getTitle());
        folder.setDescription(dto.getDescription());
        folder.setCategoryId(dto.getCategoryId());
        folder.setTags(normalizeTags(dto.getTags()));
        folder.setUserId(userId);
        folder.setFileName(dto.getTitle());
        folder.setFilePath("");
        folder.setFileSize(totalSize);
        folder.setFileType("inode/directory");
        folder.setResourceType("FOLDER");
        folder.setFileCount(storedFiles.size());
        folder.setStatus(1);
        resourceMapper.insert(folder);

        for (int i = 0; i < storedFiles.size(); i++) {
            FileStorageService.StoredFile storedFile = storedFiles.get(i);
            Resource child = new Resource();
            child.setTitle(storedFile.getOriginalName());
            child.setDescription(dto.getDescription());
            child.setCategoryId(dto.getCategoryId());
            child.setTags(folder.getTags());
            child.setUserId(userId);
            child.setFileName(storedFile.getOriginalName());
            child.setFilePath(storedFile.getPath());
            child.setFileSize(storedFile.getSize());
            child.setFileType(storedFile.getType());
            child.setResourceType("FILE");
            child.setParentId(folder.getId());
            child.setSortOrder(i);
            child.setRelativePath(relativePathAt(relativePaths, i, storedFile.getOriginalName()));
            child.setStatus(1);
            resourceMapper.insert(child);
        }

        return resourceMapper.findById(folder.getId());
    }

    @Transactional
    public List<Resource> appendChildren(Long folderId, List<MultipartFile> files, List<String> relativePaths) {
        Resource folder = assertOwnerOrAdmin(folderId);
        if (!"FOLDER".equals(folder.getResourceType())) {
            throw new BusinessException(400, "当前资源不是文件夹");
        }
        if (files == null || files.isEmpty()) {
            throw new BusinessException(400, "请选择要上传的文件");
        }

        List<Resource> existingChildren = resourceMapper.findChildren(folderId);
        int sortOrder = existingChildren.size();
        List<Resource> created = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            FileStorageService.StoredFile storedFile = fileStorageService.store(files.get(i));
            Resource child = new Resource();
            child.setTitle(storedFile.getOriginalName());
        child.setDescription(folder.getDescription());
        child.setCategoryId(folder.getCategoryId());
        child.setTags(folder.getTags());
        child.setUserId(folder.getUserId());
            child.setFileName(storedFile.getOriginalName());
            child.setFilePath(storedFile.getPath());
            child.setFileSize(storedFile.getSize());
            child.setFileType(storedFile.getType());
            child.setResourceType("FILE");
            child.setParentId(folder.getId());
            child.setSortOrder(sortOrder + i);
            child.setRelativePath(uniqueRelativePath(existingChildren, relativePathAt(relativePaths, i, storedFile.getOriginalName()), null));
            child.setStatus(1);
            resourceMapper.insert(child);
            existingChildren.add(child);
            created.add(resourceMapper.findById(child.getId()));
        }
        resourceMapper.refreshFolderStats(folderId);
        redisUtil.delete("resource:" + folderId);
        return created;
    }

    @Transactional
    public Resource updateFilePath(Long id, String fileName, String relativePath) {
        Resource resource = assertOwnerOrAdmin(id);
        if (!"FILE".equals(resource.getResourceType()) || resource.getParentId() == null) {
            throw new BusinessException(400, "当前资源不是文件夹内文件");
        }
        String normalizedName = sanitizeName(fileName);
        String normalizedPath = normalizePath(relativePath);
        if (normalizedName.isBlank() || normalizedPath.isBlank()) {
            throw new BusinessException(400, "文件名和路径不能为空");
        }
        List<Resource> siblings = resourceMapper.findChildren(resource.getParentId());
        String availablePath = uniqueRelativePath(siblings, normalizedPath, resource.getId());
        String availableName = baseName(availablePath);
        resourceMapper.updateFileLocation(resource.getId(), availableName, availablePath);
        redisUtil.delete("resource:" + resource.getId());
        redisUtil.delete("resource:" + resource.getParentId());
        return resourceMapper.findById(resource.getId());
    }

    @Transactional
    public List<Resource> updateFolderPath(Long folderId, String sourcePath, String targetPath) {
        Resource folder = assertOwnerOrAdmin(folderId);
        if (!"FOLDER".equals(folder.getResourceType())) {
            throw new BusinessException(400, "当前资源不是文件夹");
        }
        String source = normalizePath(sourcePath);
        String target = normalizePath(targetPath);
        if (source.isBlank()) {
            throw new BusinessException(400, "源目录不能为空");
        }
        if (target.equals(source) || target.startsWith(source + "/")) {
            throw new BusinessException(400, "不能移动到自身或子目录");
        }

        List<Resource> children = resourceMapper.findChildren(folderId);
        boolean changed = false;
        for (Resource child : children) {
            String path = normalizePath(child.getRelativePath());
            if (path.equals(source) || path.startsWith(source + "/")) {
                String suffix = path.equals(source) ? "" : path.substring(source.length() + 1);
                String nextPath = target.isBlank() ? suffix : (suffix.isBlank() ? target : target + "/" + suffix);
                String availablePath = uniqueRelativePath(children, nextPath, child.getId());
                resourceMapper.updateFileLocation(child.getId(), baseName(availablePath), availablePath);
                changed = true;
            }
        }
        if (!changed) {
            throw new BusinessException(404, "目录不存在");
        }
        redisUtil.delete("resource:" + folderId);
        return resourceMapper.findChildren(folderId);
    }

    public Resource update(Long id, ResourceDTO dto) {
        Resource resource = assertOwnerOrAdmin(id);
        resource.setTitle(dto.getTitle());
        resource.setDescription(dto.getDescription());
        resource.setCategoryId(dto.getCategoryId());
        resource.setTags(normalizeTags(dto.getTags()));
        resourceMapper.update(resource);
        redisUtil.delete("resource:" + id);
        return resourceMapper.findById(id);
    }

    public void delete(Long id) {
        Resource resource = assertOwnerOrAdmin(id);
        resourceMapper.softDelete(id);
        if ("FOLDER".equals(resource.getResourceType())) {
            resourceMapper.softDeleteChildren(id);
        }
        if (resource.getParentId() != null) {
            resourceMapper.refreshFolderStats(resource.getParentId());
            redisUtil.delete("resource:" + resource.getParentId());
        }
        redisUtil.delete("resource:" + id);
    }

    public void download(Long id, Long userId, String ip, HttpServletResponse response) throws IOException {
        Resource resource = resourceMapper.findById(id);
        if (resource == null || Integer.valueOf(0).equals(resource.getStatus())) {
            throw new BusinessException(404, "资源不存在");
        }
        if ("FOLDER".equals(resource.getResourceType())) {
            downloadFolder(resource, userId, ip, response);
            return;
        }
        Path path = fileStorageService.resolve(resource.getFilePath());
        resourceMapper.incrementDownloadCount(id);
        DownloadRecord record = new DownloadRecord();
        record.setUserId(userId);
        record.setResourceId(id);
        record.setIpAddress(ip);
        downloadRecordMapper.insert(record);

        String encodedName = URLEncoder.encode(resource.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedName);
        Files.copy(path, response.getOutputStream());
        response.flushBuffer();
    }

    private void downloadFolder(Resource folder, Long userId, String ip, HttpServletResponse response) throws IOException {
        List<Resource> children = resourceMapper.findChildren(folder.getId()).stream()
                .filter(item -> "FILE".equals(item.getResourceType()))
                .sorted(Comparator.comparing(item -> normalizePath(firstNonBlank(item.getRelativePath(), item.getFileName(), item.getTitle()))))
                .toList();
        if (children.isEmpty()) {
            throw new BusinessException(404, "文件夹中没有可下载文件");
        }

        resourceMapper.incrementDownloadCount(folder.getId());
        DownloadRecord record = new DownloadRecord();
        record.setUserId(userId);
        record.setResourceId(folder.getId());
        record.setIpAddress(ip);
        downloadRecordMapper.insert(record);

        String zipName = safeArchiveName(firstNonBlank(folder.getFileName(), folder.getTitle(), "folder")) + ".zip";
        String encodedName = URLEncoder.encode(zipName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedName);

        Set<String> usedNames = new LinkedHashSet<>();
        try (OutputStream output = response.getOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (Resource child : children) {
                Path filePath = fileStorageService.resolve(child.getFilePath());
                if (!Files.isRegularFile(filePath)) {
                    continue;
                }
                String entryName = uniqueZipEntryName(
                        normalizePath(firstNonBlank(child.getRelativePath(), child.getFileName(), child.getTitle())),
                        usedNames);
                zip.putNextEntry(new ZipEntry(entryName));
                Files.copy(filePath, zip);
                zip.closeEntry();
            }
            zip.finish();
        }
        response.flushBuffer();
    }

    public List<Resource> favorites(Long userId) {
        return resourceMapper.findFavorites(userId);
    }

    public List<Resource> children(Long id) {
        Resource resource = resourceMapper.findById(id);
        if (resource == null || Integer.valueOf(0).equals(resource.getStatus())) {
            throw new BusinessException(404, "资源不存在");
        }
        if (!"FOLDER".equals(resource.getResourceType())) {
            throw new BusinessException(400, "当前资源不是文件夹");
        }
        return resourceMapper.findChildren(id);
    }

    public void preview(Long id, HttpServletResponse response) throws IOException {
        Resource resource = resourceMapper.findById(id);
        if (resource == null || Integer.valueOf(0).equals(resource.getStatus())) {
            throw new BusinessException(404, "resource not found");
        }
        if ("FOLDER".equals(resource.getResourceType())) {
            throw new BusinessException(415, "folder cannot be previewed directly");
        }
        String contentType = previewContentType(resource);
        Path path = fileStorageService.resolve(resource.getFilePath());
        if (contentType == null) {
            throw new BusinessException(415, "unsupported preview type");
        }
        String encodedName = URLEncoder.encode(resource.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType(contentType);
        response.setHeader("Content-Disposition", "inline; filename*=UTF-8''" + encodedName);
        response.setHeader("Accept-Ranges", "bytes");
        Files.copy(path, response.getOutputStream());
        response.flushBuffer();
    }

    private String previewContentType(Resource resource) {
        String fileType = resource.getFileType() == null ? "" : resource.getFileType().toLowerCase();
        String name = resource.getFileName() == null ? "" : resource.getFileName().toLowerCase();
        if ("application/pdf".equals(fileType) || name.endsWith(".pdf")) {
            return "application/pdf";
        }
        if ("application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(fileType) || name.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if ("application/vnd.openxmlformats-officedocument.presentationml.presentation".equals(fileType) || name.endsWith(".pptx")) {
            return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        }
        if (fileType.startsWith("image/")) {
            return fileType;
        }
        if (fileType.startsWith("video/")) {
            return fileType;
        }
        if (fileType.startsWith("audio/")) {
            return fileType;
        }
        if (fileType.startsWith("text/")) {
            return fileType;
        }
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (name.endsWith(".mp4")) {
            return "video/mp4";
        }
        if (name.endsWith(".webm")) {
            return "video/webm";
        }
        if (name.endsWith(".mov")) {
            return "video/quicktime";
        }
        if (name.endsWith(".mp3")) {
            return "audio/mpeg";
        }
        if (name.endsWith(".wav")) {
            return "audio/wav";
        }
        if (name.endsWith(".ogg")) {
            return "audio/ogg";
        }
        if (name.endsWith(".flac")) {
            return "audio/flac";
        }
        if (name.endsWith(".m4a")) {
            return "audio/mp4";
        }
        if (name.endsWith(".aac")) {
            return "audio/aac";
        }
        if (name.endsWith(".txt")) {
            return "text/plain;charset=UTF-8";
        }
        return null;
    }

    private String relativePathAt(List<String> relativePaths, int index, String fallback) {
        if (relativePaths == null || index >= relativePaths.size()) {
            return fallback;
        }
        String path = relativePaths.get(index);
        if (path == null || path.isBlank()) {
            return fallback;
        }
        return path.replace("\\", "/");
    }

    private String uniqueRelativePath(List<Resource> resources, String candidatePath, Long ignoreId) {
        String normalized = normalizePath(candidatePath);
        if (!hasRelativePath(resources, normalized, ignoreId)) {
            return normalized;
        }
        String folder = parentPath(normalized);
        String originalName = baseName(normalized);
        int dotIndex = originalName.lastIndexOf('.');
        boolean hasExtension = dotIndex > 0;
        String stem = hasExtension ? originalName.substring(0, dotIndex) : originalName;
        String extension = hasExtension ? originalName.substring(dotIndex) : "";
        int index = 1;
        String nextPath;
        do {
            String nextName = stem + " (" + index + ")" + extension;
            nextPath = folder.isBlank() ? nextName : folder + "/" + nextName;
            index++;
        } while (hasRelativePath(resources, nextPath, ignoreId));
        return nextPath;
    }

    private boolean hasRelativePath(List<Resource> resources, String path, Long ignoreId) {
        String normalized = normalizePath(path);
        return resources.stream().anyMatch(resource -> {
            if (ignoreId != null && ignoreId.equals(resource.getId())) {
                return false;
            }
            return normalized.equalsIgnoreCase(normalizePath(resource.getRelativePath()));
        });
    }

    private String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        String[] parts = path.replace("\\", "/").split("/");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            String cleaned = sanitizeName(part);
            if (!cleaned.isBlank() && !".".equals(cleaned) && !"..".equals(cleaned)) {
                result.add(cleaned);
            }
        }
        return String.join("/", result);
    }

    private String sanitizeName(String name) {
        if (name == null) {
            return "";
        }
        return name.replaceAll("[\\\\/<>:\"|?*]", "").trim();
    }

    private String parentPath(String path) {
        String normalized = normalizePath(path);
        int index = normalized.lastIndexOf('/');
        return index < 0 ? "" : normalized.substring(0, index);
    }

    private String baseName(String path) {
        String normalized = normalizePath(path);
        int index = normalized.lastIndexOf('/');
        return index < 0 ? normalized : normalized.substring(index + 1);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private String safeArchiveName(String name) {
        String cleaned = sanitizeName(name);
        return cleaned.isBlank() ? "folder" : cleaned;
    }

    private String uniqueZipEntryName(String candidate, Set<String> usedNames) {
        String normalized = normalizePath(candidate);
        if (normalized.isBlank()) {
            normalized = "file";
        }
        String next = normalized;
        int index = 1;
        while (!usedNames.add(next.toLowerCase())) {
            String parent = parentPath(normalized);
            String base = baseName(normalized);
            int dot = base.lastIndexOf('.');
            String renamed = dot > 0
                    ? base.substring(0, dot) + "-" + index + base.substring(dot)
                    : base + "-" + index;
            next = parent.isBlank() ? renamed : parent + "/" + renamed;
            index++;
        }
        return next;
    }

    private Resource assertOwnerOrAdmin(Long id) {
        Resource resource = resourceMapper.findById(id);
        if (resource == null) {
            throw new BusinessException(404, "资源不存在");
        }
        User current = CurrentUser.get();
        if (current == null) {
            throw new BusinessException(401, "请先登录");
        }
        if (!"ADMIN".equals(current.getRole()) && !resource.getUserId().equals(current.getId())) {
            throw new BusinessException(403, "无权限操作该资源");
        }
        return resource;
    }

    private String normalizeTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return null;
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String raw : tags.split("[,，;；\\s]+")) {
            String tag = raw == null ? "" : raw.trim();
            if (tag.isBlank()) {
                continue;
            }
            tag = tag.replaceAll("[#<>\"'`]", "");
            if (tag.isBlank()) {
                continue;
            }
            if (tag.length() > MAX_TAG_LENGTH) {
                tag = tag.substring(0, MAX_TAG_LENGTH);
            }
            normalized.add(tag);
            if (normalized.size() >= MAX_TAG_COUNT) {
                break;
            }
        }
        if (normalized.isEmpty()) {
            return null;
        }
        String result = String.join(",", normalized);
        return result.length() <= MAX_TAGS_TEXT_LENGTH ? result : result.substring(0, MAX_TAGS_TEXT_LENGTH);
    }
}
