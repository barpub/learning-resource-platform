package com.example.platform.entity;

import java.time.LocalDateTime;
import java.util.List;

public class Note {
    private Long id;
    private String title;
    private String content;
    private String category;
    private Long resourceId;
    private String anchorType;
    private String anchorText;
    private String anchorImage;
    private Double anchorSeconds;
    private Long userId;
    private Integer isFavorite;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<NoteTag> tags;
    private String resourceTitle;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getIsFavorite() {
        return isFavorite;
    }

    public void setIsFavorite(Integer isFavorite) {
        this.isFavorite = isFavorite;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public List<NoteTag> getTags() {
        return tags;
    }

    public void setTags(List<NoteTag> tags) {
        this.tags = tags;
    }

    public String getResourceTitle() {
        return resourceTitle;
    }

    public void setResourceTitle(String resourceTitle) {
        this.resourceTitle = resourceTitle;
    }
}
