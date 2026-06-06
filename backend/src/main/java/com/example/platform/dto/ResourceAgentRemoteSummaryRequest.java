package com.example.platform.dto;

public class ResourceAgentRemoteSummaryRequest {
    private String title;
    private String description;
    private String fileName;
    private String fileType;
    private String resourceType;
    private Long fileSize;
    private Long remoteConnectionId;
    private String remoteConnectionName;
    private String remotePath;

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
}
