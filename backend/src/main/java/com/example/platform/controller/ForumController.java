package com.example.platform.controller;

import com.example.platform.common.BusinessException;
import com.example.platform.common.PageResult;
import com.example.platform.common.Result;
import com.example.platform.dto.ForumCommentDTO;
import com.example.platform.dto.ForumPostDTO;
import com.example.platform.dto.ForumPostView;
import com.example.platform.entity.ForumComment;
import com.example.platform.security.CurrentUser;
import com.example.platform.service.ForumService;
import java.util.List;
import javax.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/forum")
public class ForumController {
    private final ForumService forumService;

    public ForumController(ForumService forumService) {
        this.forumService = forumService;
    }

    @GetMapping("/posts")
    public Result<PageResult<ForumPostView>> posts(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "10") int size,
                                                   @RequestParam(required = false) String keyword) {
        return Result.success(forumService.page(page, size, keyword));
    }

    @GetMapping("/posts/{id}")
    public Result<ForumPostView> get(@PathVariable Long id) {
        return Result.success(forumService.get(id));
    }

    @PostMapping(value = "/posts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ForumPostView> createPost(@Valid @ModelAttribute ForumPostDTO dto,
                                            @RequestPart(required = false) List<MultipartFile> images) {
        Long userId = requireUser();
        return Result.success(forumService.createPost(dto, images, userId));
    }

    @PostMapping(value = "/posts/{id}/comments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ForumComment> createComment(@PathVariable Long id,
                                              @Valid @ModelAttribute ForumCommentDTO dto,
                                              @RequestPart(required = false) List<MultipartFile> images) {
        Long userId = requireUser();
        dto.setPostId(id);
        return Result.success(forumService.createComment(dto, images, userId));
    }

    @DeleteMapping("/posts/{id}")
    public Result<String> deletePost(@PathVariable Long id) {
        forumService.deletePost(id);
        return Result.success("deleted");
    }

    @DeleteMapping("/comments/{id}")
    public Result<String> deleteComment(@PathVariable Long id) {
        forumService.deleteComment(id);
        return Result.success("deleted");
    }

    private Long requireUser() {
        Long userId = CurrentUser.id();
        if (userId == null) {
            throw new BusinessException(401, "Please login first");
        }
        return userId;
    }
}
