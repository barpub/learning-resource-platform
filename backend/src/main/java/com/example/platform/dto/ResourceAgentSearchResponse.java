package com.example.platform.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ResourceAgentSearchResponse {
    private String keyword;
    private String answer;
    private List<GlobalSearchItem> records = new ArrayList<>();
    private Map<String, Object> summary;
    private List<String> agentSteps = new ArrayList<>();
    private List<String> suggestedQueries = new ArrayList<>();

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
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

    public List<String> getAgentSteps() {
        return agentSteps;
    }

    public void setAgentSteps(List<String> agentSteps) {
        this.agentSteps = agentSteps;
    }

    public List<String> getSuggestedQueries() {
        return suggestedQueries;
    }

    public void setSuggestedQueries(List<String> suggestedQueries) {
        this.suggestedQueries = suggestedQueries;
    }
}
