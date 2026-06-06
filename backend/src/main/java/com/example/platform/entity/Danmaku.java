package com.example.platform.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Danmaku {
    private Long id;
    private Long resourceId;
    private Long userId;
    private String content;
    private BigDecimal timeSeconds;
    private String type;
    private String color;
    private Integer status;
    private LocalDateTime createTime;
    // Joined display fields
    private String username;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getResourceId() { return resourceId; }
    public void setResourceId(Long resourceId) { this.resourceId = resourceId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public BigDecimal getTimeSeconds() { return timeSeconds; }
    public void setTimeSeconds(BigDecimal timeSeconds) { this.timeSeconds = timeSeconds; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
}
