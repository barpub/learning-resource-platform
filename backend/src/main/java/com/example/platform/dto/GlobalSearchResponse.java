package com.example.platform.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GlobalSearchResponse {
    private String keyword;
    private List<GlobalSearchItem> records = new ArrayList<>();
    private Map<String, Object> summary;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public List<GlobalSearchItem> getRecords() {
        return records;
    }

    public void setRecords(List<GlobalSearchItem> records) {
        this.records = records;
    }

    public Map<String, Object> getSummary() {
        return summary;
    }

    public void setSummary(Map<String, Object> summary) {
        this.summary = summary;
    }
}
