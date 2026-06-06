package com.example.platform.controller;

import com.example.platform.common.BusinessException;
import com.example.platform.common.Result;
import com.example.platform.dto.CommentDTO;
import com.example.platform.entity.Comment;
import com.example.platform.security.CurrentUser;
import com.example.platform.service.CommentService;
import java.util.List;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class CommentController {
    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping("/api/comments")
    public Result<List<Comment>> list(@RequestParam(required = false) Long resourceId) {
        if (resourceId != null) {
            return Result.success(commentService.listByResource(resourceId));
        }
        return Result.success(commentService.listAll());
    }

    @GetMapping("/api/resources/{id}/comments")
    public Result<List<Comment>> resourceComments(@PathVariable Long id) {
        return Result.success(commentService.listByResource(id));
    }

    @PostMapping("/api/comments")
    public Result<Comment> create(@Valid @RequestBody CommentDTO dto) {
        Long userId = CurrentUser.id();
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        return Result.success(commentService.create(dto, userId));
    }

    @DeleteMapping("/api/comments/{id}")
    public Result<String> delete(@PathVariable Long id) {
        commentService.delete(id);
        return Result.success("删除成功");
    }
}
