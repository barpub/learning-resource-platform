package com.example.platform.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BetaRecommendationResponse {
    private String mode = "BETA_OBJECT_TAG_VECTOR";
    private List<String> userVectorTags = new ArrayList<>();
    private List<BetaRecommendationItem> records = new ArrayList<>();
    private Map<String, Object> summary;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public List<String> getUserVectorTags() {
        return userVectorTags;
    }

    public void setUserVectorTags(List<String> userVectorTags) {
        this.userVectorTags = userVectorTags;
    }

    public List<BetaRecommendationItem> getRecords() {
        return records;
    }

    public void setRecords(List<BetaRecommendationItem> records) {
        this.records = records;
    }

    public Map<String, Object> getSummary() {
        return summary;
    }

    public void setSummary(Map<String, Object> summary) {
        this.summary = summary;
    }
}
