package com.example.platform.dto;

public class MediaEnhancementRequest {
    private String targetResolution;
    private Integer targetFps;
    private String videoPreset;
    private String audioPreset;
    private Boolean aiUpscale;
    private Boolean frameInterpolation;

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

    public Boolean getAiUpscale() {
        return aiUpscale;
    }

    public void setAiUpscale(Boolean aiUpscale) {
        this.aiUpscale = aiUpscale;
    }

    public Boolean getFrameInterpolation() {
        return frameInterpolation;
    }

    public void setFrameInterpolation(Boolean frameInterpolation) {
        this.frameInterpolation = frameInterpolation;
    }
}
