package com.example.platform.service;

import com.example.platform.common.BusinessException;
import com.example.platform.dto.CommentDTO;
import com.example.platform.entity.Comment;
import com.example.platform.entity.User;
import com.example.platform.mapper.CommentMapper;
import com.example.platform.mapper.ResourceMapper;
import com.example.platform.security.CurrentUser;
import com.example.platform.utils.RedisUtil;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentService {
    private final CommentMapper commentMapper;
    private final ResourceMapper resourceMapper;
    private final RedisUtil redisUtil;

    public CommentService(CommentMapper commentMapper, ResourceMapper resourceMapper, RedisUtil redisUtil) {
        this.commentMapper = commentMapper;
        this.resourceMapper = resourceMapper;
        this.redisUtil = redisUtil;
    }

    public List<Comment> listByResource(Long resourceId) {
        return commentMapper.findByResourceId(resourceId);
    }

    public List<Comment> listAll() {
        return commentMapper.findAll();
    }

    @Transactional
    public Comment create(CommentDTO dto, Long userId) {
        if (resourceMapper.findById(dto.getResourceId()) == null) {
            throw new BusinessException(404, "资源不存在");
        }
        Comment comment = new Comment();
        comment.setResourceId(dto.getResourceId());
        comment.setUserId(userId);
        comment.setContent(dto.getContent());
        comment.setRating(dto.getRating());
        comment.setParentId(dto.getParentId() == null ? 0L : dto.getParentId());
        comment.setStatus(1);
        commentMapper.insert(comment);
        resourceMapper.refreshRating(dto.getResourceId());
        redisUtil.delete("resource:" + dto.getResourceId());
        return commentMapper.findById(comment.getId());
    }

    @Transactional
    public void delete(Long id) {
        Comment comment = commentMapper.findById(id);
        if (comment == null) {
            throw new BusinessException(404, "评论不存在");
        }
        User current = CurrentUser.get();
        if (current == null) {
            throw new BusinessException(401, "请先登录");
        }
        if (!"ADMIN".equals(current.getRole()) && !comment.getUserId().equals(current.getId())) {
            throw new BusinessException(403, "无权限删除该评论");
        }
        commentMapper.softDelete(id);
        resourceMapper.refreshRating(comment.getResourceId());
        redisUtil.delete("resource:" + comment.getResourceId());
    }
}
