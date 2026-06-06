package com.example.platform.entity;

import java.time.LocalDateTime;

public class DownloadRecord {
    private Long id;
    private Long userId;
    private Long resourceId;
    private LocalDateTime downloadTime;
    private String ipAddress;

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

    public LocalDateTime getDownloadTime() {
        return downloadTime;
    }

    public void setDownloadTime(LocalDateTime downloadTime) {
        this.downloadTime = downloadTime;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }
}
