package com.example.platform.dto;

import com.example.platform.entity.ForumComment;
import com.example.platform.entity.ForumPost;
import com.example.platform.entity.ForumPostResource;
import java.util.ArrayList;
import java.util.List;

public class ForumPostView {
    private ForumPost post;
    private List<String> imageUrls = new ArrayList<>();
    private List<ForumPostResource> resources = new ArrayList<>();
    private List<ForumComment> comments = new ArrayList<>();

    public ForumPost getPost() {
        return post;
    }

    public void setPost(ForumPost post) {
        this.post = post;
    }

    public List<String> getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(List<String> imageUrls) {
        this.imageUrls = imageUrls;
    }

    public List<ForumPostResource> getResources() {
        return resources;
    }

    public void setResources(List<ForumPostResource> resources) {
        this.resources = resources;
    }

    public List<ForumComment> getComments() {
        return comments;
    }

    public void setComments(List<ForumComment> comments) {
        this.comments = comments;
    }
}
