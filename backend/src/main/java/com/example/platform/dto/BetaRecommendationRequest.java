package com.example.platform.dto;

import java.util.ArrayList;
import java.util.List;

public class BetaRecommendationRequest {
    private List<String> preferenceTags = new ArrayList<>();
    private List<String> objectTags = new ArrayList<>();
    private String keyword;
    private Boolean includeLocal = true;
    private Boolean includeRemote = true;
    private Integer limit = 18;

    public List<String> getPreferenceTags() {
        return preferenceTags;
    }

    public void setPreferenceTags(List<String> preferenceTags) {
        this.preferenceTags = preferenceTags;
    }

    public List<String> getObjectTags() {
        return objectTags;
    }

    public void setObjectTags(List<String> objectTags) {
        this.objectTags = objectTags;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Boolean getIncludeLocal() {
        return includeLocal;
    }

    public void setIncludeLocal(Boolean includeLocal) {
        this.includeLocal = includeLocal;
    }

    public Boolean getIncludeRemote() {
        return includeRemote;
    }

    public void setIncludeRemote(Boolean includeRemote) {
        this.includeRemote = includeRemote;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }
}
