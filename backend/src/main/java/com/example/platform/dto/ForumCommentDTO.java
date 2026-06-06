package com.example.platform.dto;

import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotBlank;

public class ForumCommentDTO {
    private Long postId;
    @NotBlank
    private String content;
    private List<String> imageUrls = new ArrayList<>();
    private Long parentId;

    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<String> getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(List<String> imageUrls) {
        this.imageUrls = imageUrls;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }
}
