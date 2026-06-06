package com.example.platform.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class BetaRecommendationItem {
    private String betaId;
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
    private String username;
    private Integer viewCount;
    private Integer downloadCount;
    private BigDecimal rating;
    private Double score;
    private Double vectorDistance;
    private List<String> objectTags = new ArrayList<>();
    private List<String> matchReasons = new ArrayList<>();

    public String getBetaId() {
        return betaId;
    }

    public void setBetaId(String betaId) {
        this.betaId = betaId;
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

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
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

    public Double getVectorDistance() {
        return vectorDistance;
    }

    public void setVectorDistance(Double vectorDistance) {
        this.vectorDistance = vectorDistance;
    }

    public List<String> getObjectTags() {
        return objectTags;
    }

    public void setObjectTags(List<String> objectTags) {
        this.objectTags = objectTags;
    }

    public List<String> getMatchReasons() {
        return matchReasons;
    }

    public void setMatchReasons(List<String> matchReasons) {
        this.matchReasons = matchReasons;
    }
}
