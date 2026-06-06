package com.example.platform.service;

import com.example.platform.common.BusinessException;
import com.example.platform.common.PageResult;
import com.example.platform.dto.ForumCommentDTO;
import com.example.platform.dto.ForumPostDTO;
import com.example.platform.dto.ForumPostView;
import com.example.platform.entity.ForumComment;
import com.example.platform.entity.ForumPost;
import com.example.platform.entity.ForumPostResource;
import com.example.platform.entity.Resource;
import com.example.platform.entity.User;
import com.example.platform.mapper.ForumCommentMapper;
import com.example.platform.mapper.ForumPostMapper;
import com.example.platform.mapper.ResourceMapper;
import com.example.platform.security.CurrentUser;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ForumService {
    private final ForumPostMapper forumPostMapper;
    private final ForumCommentMapper forumCommentMapper;
    private final ResourceMapper resourceMapper;
    private final FileStorageService fileStorageService;

    public ForumService(ForumPostMapper forumPostMapper,
                        ForumCommentMapper forumCommentMapper,
                        ResourceMapper resourceMapper,
                        FileStorageService fileStorageService) {
        this.forumPostMapper = forumPostMapper;
        this.forumCommentMapper = forumCommentMapper;
        this.resourceMapper = resourceMapper;
        this.fileStorageService = fileStorageService;
    }

    public PageResult<ForumPostView> page(int page, int size, String keyword) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        int offset = (safePage - 1) * safeSize;
        List<ForumPost> posts = forumPostMapper.findPage(trim(keyword), offset, safeSize);
        List<ForumPostView> views = posts.stream().map(this::toViewWithoutComments).toList();
        return new PageResult<>(views, forumPostMapper.countPage(trim(keyword)), safePage, safeSize);
    }

    public ForumPostView get(Long id) {
        ForumPost post = forumPostMapper.findById(id);
        if (post == null) {
            throw new BusinessException(404, "Forum post not found");
        }
        ForumPostView view = toViewWithoutComments(post);
        view.setComments(forumCommentMapper.findByPostId(id));
        return view;
    }

    @Transactional
    public ForumPostView createPost(ForumPostDTO dto, List<MultipartFile> images, Long userId) {
        ForumPost post = new ForumPost();
        post.setUserId(userId);
        post.setTitle(trim(dto.getTitle()));
        post.setContent(trim(dto.getContent()));
        post.setFolderPath(normalizeFolderPath(dto.getFolderPath()));
        post.setImageUrls(joinUrls(storeImages(images)));
        post.setStatus(1);
        forumPostMapper.insert(post);
        attachResources(post.getId(), dto.getResourceIds(), dto.getResourceFolderPaths());
        return get(post.getId());
    }

    @Transactional
    public ForumComment createComment(ForumCommentDTO dto, List<MultipartFile> images, Long userId) {
        if (forumPostMapper.findById(dto.getPostId()) == null) {
            throw new BusinessException(404, "Forum post not found");
        }
        ForumComment comment = new ForumComment();
        comment.setPostId(dto.getPostId());
        comment.setUserId(userId);
        comment.setContent(trim(dto.getContent()));
        comment.setImageUrls(joinUrls(storeImages(images)));
        comment.setParentId(dto.getParentId() == null ? 0L : dto.getParentId());
        comment.setStatus(1);
        forumCommentMapper.insert(comment);
        return forumCommentMapper.findById(comment.getId());
    }

    @Transactional
    public void deletePost(Long id) {
        ForumPost post = forumPostMapper.findById(id);
        if (post == null) {
            throw new BusinessException(404, "Forum post not found");
        }
        User current = requireCurrentUser();
        if (!"ADMIN".equals(current.getRole()) && !post.getUserId().equals(current.getId())) {
            throw new BusinessException(403, "No permission to delete this post");
        }
        forumPostMapper.softDelete(id);
    }

    @Transactional
    public void deleteComment(Long id) {
        ForumComment comment = forumCommentMapper.findById(id);
        if (comment == null) {
            throw new BusinessException(404, "Forum comment not found");
        }
        User current = requireCurrentUser();
        if (!"ADMIN".equals(current.getRole()) && !comment.getUserId().equals(current.getId())) {
            throw new BusinessException(403, "No permission to delete this comment");
        }
        forumCommentMapper.softDelete(id);
    }

    private ForumPostView toViewWithoutComments(ForumPost post) {
        ForumPostView view = new ForumPostView();
        view.setPost(post);
        view.setImageUrls(splitUrls(post.getImageUrls()));
        view.setResources(forumPostMapper.findResources(post.getId()));
        return view;
    }

    private void attachResources(Long postId, List<Long> resourceIds, List<String> folderPaths) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return;
        }
        for (int i = 0; i < resourceIds.size(); i++) {
            Long resourceId = resourceIds.get(i);
            if (resourceId == null) {
                continue;
            }
            Resource resource = resourceMapper.findById(resourceId);
            if (resource == null || Integer.valueOf(0).equals(resource.getStatus())) {
                throw new BusinessException(404, "Linked resource not found: " + resourceId);
            }
            ForumPostResource link = new ForumPostResource();
            link.setPostId(postId);
            link.setResourceId(resourceId);
            link.setFolderPath(normalizeFolderPath(folderAt(folderPaths, i)));
            forumPostMapper.insertResource(link);
        }
    }

    private List<String> storeImages(List<MultipartFile> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        List<String> urls = new ArrayList<>();
        for (MultipartFile image : images) {
            if (image == null || image.isEmpty()) {
                continue;
            }
            String type = image.getContentType() == null ? "" : image.getContentType().toLowerCase();
            if (!type.startsWith("image/")) {
                throw new BusinessException(400, "Only image files can be attached to forum posts");
            }
            FileStorageService.StoredFile stored = fileStorageService.store(image);
            urls.add("/uploads/" + Paths.get(stored.getPath()).getFileName());
        }
        return urls;
    }

    private List<String> splitUrls(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.split("\\n"))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private String joinUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return "";
        }
        return String.join("\n", urls);
    }

    private String folderAt(List<String> paths, int index) {
        if (paths == null || index < 0 || index >= paths.size()) {
            return "";
        }
        return paths.get(index);
    }

    private String normalizeFolderPath(String path) {
        String value = trim(path).replace('\\', '/');
        while (value.contains("//")) {
            value = value.replace("//", "/");
        }
        if (value.startsWith("/")) {
            value = value.substring(1);
        }
        if (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private User requireCurrentUser() {
        User current = CurrentUser.get();
        if (current == null) {
            throw new BusinessException(401, "Please login first");
        }
        return current;
    }
}
