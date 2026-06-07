package com.example.platform.entity;

import java.time.LocalDateTime;

public class ViewHistory {
    private Long id;
    private Long userId;
    private Long resourceId;
    private Integer viewDuration;
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
    }

    public Integer getViewDuration() {
        return viewDuration;
    }

    public void setViewDuration(Integer viewDuration) {
        this.viewDuration = viewDuration;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
