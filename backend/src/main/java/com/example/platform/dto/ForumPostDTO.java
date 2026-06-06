package com.example.platform.dto;

import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class ForumPostDTO {
    @NotBlank
    private String title;
    @NotBlank
    private String content;
    private String folderPath;
    private List<Long> resourceIds = new ArrayList<>();
    private List<String> resourceFolderPaths = new ArrayList<>();

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

    public String getFolderPath() {
        return folderPath;
    }

    public void setFolderPath(String folderPath) {
        this.folderPath = folderPath;
    }

    public List<Long> getResourceIds() {
        return resourceIds;
    }

    public void setResourceIds(List<Long> resourceIds) {
        this.resourceIds = resourceIds;
    }

    public List<String> getResourceFolderPaths() {
        return resourceFolderPaths;
    }

    public void setResourceFolderPaths(List<String> resourceFolderPaths) {
        this.resourceFolderPaths = resourceFolderPaths;
    }
}
