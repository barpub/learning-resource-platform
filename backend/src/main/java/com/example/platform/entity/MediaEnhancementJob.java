package com.example.platform.entity;

import java.time.LocalDateTime;

public class MediaEnhancementJob {
    private Long id;
    private Long sourceResourceId;
    private Long outputResourceId;
    private Long userId;
    private String mediaType;
    private String targetResolution;
    private Integer targetFps;
    private String videoPreset;
    private String audioPreset;
    private Integer aiUpscale;
    private Integer frameInterpolation;
    private String status;
    private Integer progress;
    private String message;
    private String outputFilePath;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSourceResourceId() {
        return sourceResourceId;
    }

    public void setSourceResourceId(Long sourceResourceId) {
        this.sourceResourceId = sourceResourceId;
    }

    public Long getOutputResourceId() {
        return outputResourceId;
    }

    public void setOutputResourceId(Long outputResourceId) {
        this.outputResourceId = outputResourceId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public String getTargetResolution() {
        return targetResolution;
    }

    public void setTargetResolution(String targetResolution) {
        this.targetResolution = targetResolution;
    }

    public Integer getTargetFps() {
        return targetFps;
    }

    public void setTargetFps(Integer targetFps) {
        this.targetFps = targetFps;
    }

    public String getVideoPreset() {
        return videoPreset;
    }

    public void setVideoPreset(String videoPreset) {
        this.videoPreset = videoPreset;
    }

    public String getAudioPreset() {
        return audioPreset;
    }

    public void setAudioPreset(String audioPreset) {
        this.audioPreset = audioPreset;
    }

    public Integer getAiUpscale() {
        return aiUpscale;
    }

    public void setAiUpscale(Integer aiUpscale) {
        this.aiUpscale = aiUpscale;
    }

    public Integer getFrameInterpolation() {
        return frameInterpolation;
    }

    public void setFrameInterpolation(Integer frameInterpolation) {
        this.frameInterpolation = frameInterpolation;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getOutputFilePath() {
        return outputFilePath;
    }

    public void setOutputFilePath(String outputFilePath) {
        this.outputFilePath = outputFilePath;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
