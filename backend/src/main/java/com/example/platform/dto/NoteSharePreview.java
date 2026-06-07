package com.example.platform.dto;

import java.time.LocalDateTime;
import java.util.List;

public class NoteSharePreview {
    private String token;
    private String title;
    private String content;
    private String category;
    private Long resourceId;
    private String resourceTitle;
    private String anchorType;
    private String anchorText;
    private String anchorImage;
    private Double anchorSeconds;
    private List<String> tagNames;
    private LocalDateTime createTime;
    private LocalDateTime shareTime;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
    }

    public String getResourceTitle() {
        return resourceTitle;
    }

    public void setResourceTitle(String resourceTitle) {
        this.resourceTitle = resourceTitle;
    }

    public String getAnchorType() {
        return anchorType;
    }

    public void setAnchorType(String anchorType) {
        this.anchorType = anchorType;
    }

    public String getAnchorText() {
        return anchorText;
    }

    public void setAnchorText(String anchorText) {
        this.anchorText = anchorText;
    }

    public String getAnchorImage() {
        return anchorImage;
    }

    public void setAnchorImage(String anchorImage) {
        this.anchorImage = anchorImage;
    }

    public Double getAnchorSeconds() {
        return anchorSeconds;
    }

    public void setAnchorSeconds(Double anchorSeconds) {
        this.anchorSeconds = anchorSeconds;
    }

    public List<String> getTagNames() {
        return tagNames;
    }

    public void setTagNames(List<String> tagNames) {
        this.tagNames = tagNames;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getShareTime() {
        return shareTime;
    }

    public void setShareTime(LocalDateTime shareTime) {
        this.shareTime = shareTime;
    }
}
