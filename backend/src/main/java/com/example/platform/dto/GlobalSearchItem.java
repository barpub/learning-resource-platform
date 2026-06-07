package com.example.platform.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class GlobalSearchItem {
    private String id;
    private String source;
    private String title;
    private String description;
    private String fileName;
    private String fileType;
    private String resourceType;
    private Long fileSize;
    private Long localResourceId;
    private Long remoteConnectionId;
    private String remoteConnectionName;
    private String remotePath;
    private String openUrl;
    private String previewUrl;
    private String downloadUrl;
    private String categoryName;
    private String tags;
    private String ownerName;
    private Integer viewCount;
    private Integer downloadCount;
    private BigDecimal rating;
    private Double score;
    private List<String> highlights = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public Long getLocalResourceId() {
        return localResourceId;
    }

    public void setLocalResourceId(Long localResourceId) {
        this.localResourceId = localResourceId;
    }

    public Long getRemoteConnectionId() {
        return remoteConnectionId;
    }

    public void setRemoteConnectionId(Long remoteConnectionId) {
        this.remoteConnectionId = remoteConnectionId;
    }

    public String getRemoteConnectionName() {
        return remoteConnectionName;
    }

    public void setRemoteConnectionName(String remoteConnectionName) {
        this.remoteConnectionName = remoteConnectionName;
    }

    public String getRemotePath() {
        return remotePath;
    }

    public void setRemotePath(String remotePath) {
        this.remotePath = remotePath;
    }

    public String getOpenUrl() {
        return openUrl;
    }

    public void setOpenUrl(String openUrl) {
        this.openUrl = openUrl;
    }

    public String getPreviewUrl() {
        return previewUrl;
    }

    public void setPreviewUrl(String previewUrl) {
        this.previewUrl = previewUrl;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public Integer getViewCount() {
        return viewCount;
    }

    public void setViewCount(Integer viewCount) {
        this.viewCount = viewCount;
    }

    public Integer getDownloadCount() {
        return downloadCount;
    }

    public void setDownloadCount(Integer downloadCount) {
        this.downloadCount = downloadCount;
    }

    public BigDecimal getRating() {
        return rating;
    }

    public void setRating(BigDecimal rating) {
        this.rating = rating;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public List<String> getHighlights() {
        return highlights;
    }

    public void setHighlights(List<String> highlights) {
        this.highlights = highlights;
    }
}
