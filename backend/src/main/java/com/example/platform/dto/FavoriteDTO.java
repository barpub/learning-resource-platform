package com.example.platform.dto;

import javax.validation.constraints.NotNull;

public class FavoriteDTO {
    @NotNull
    private Long resourceId;

    public Long getResourceId() {
        return resourceId;
    }

    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
    }
}
