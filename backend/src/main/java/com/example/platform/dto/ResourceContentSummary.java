package com.example.platform.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ResourceContentSummary {
    private Long resourceId;
    private String source;
    private String title;
    private String fileName;
    private String mediaKind;
    private String contentSource;
    private String summary;
    private Double confidence;
    private List<String> knowledgePoints = new ArrayList<>();
    private List<String> outline = new ArrayList<>();
    private List<String> evidenceSnippets = new ArrayList<>();
    private List<String> limitations = new ArrayList<>();
    private List<String> nextActions = new ArrayList<>();
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public Long getResourceId() {
        return resourceId;
    }

    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
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

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getMediaKind() {
        return mediaKind;
    }

    public void setMediaKind(String mediaKind) {
        this.mediaKind = mediaKind;
    }

    public String getContentSource() {
        return contentSource;
    }

    public void setContentSource(String contentSource) {
        this.contentSource = contentSource;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public List<String> getKnowledgePoints() {
        return knowledgePoints;
    }

    public void setKnowledgePoints(List<String> knowledgePoints) {
        this.knowledgePoints = knowledgePoints;
    }

    public List<String> getOutline() {
        return outline;
    }

    public void setOutline(List<String> outline) {
        this.outline = outline;
    }

    public List<String> getEvidenceSnippets() {
        return evidenceSnippets;
    }

    public void setEvidenceSnippets(List<String> evidenceSnippets) {
        this.evidenceSnippets = evidenceSnippets;
    }

    public List<String> getLimitations() {
        return limitations;
    }

    public void setLimitations(List<String> limitations) {
        this.limitations = limitations;
    }

    public List<String> getNextActions() {
        return nextActions;
    }

    public void setNextActions(List<String> nextActions) {
        this.nextActions = nextActions;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
