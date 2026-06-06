package com.example.platform.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class ResourceDTO {
    @NotBlank
    private String title;
    private String description;
    @NotNull
    private Long categoryId;

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

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }
}
