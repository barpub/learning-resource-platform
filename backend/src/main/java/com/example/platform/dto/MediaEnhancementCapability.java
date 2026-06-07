package com.example.platform.dto;

import java.util.List;

public class MediaEnhancementCapability {
    private boolean ffmpegAvailable;
    private boolean ffprobeAvailable;
    private boolean aiUpscaleAvailable;
    private boolean frameInterpolationAvailable;
    private String ffmpegPath;
    private String message;
    private List<String> availableFeatures;
    private List<String> unavailableFeatures;

    public boolean isFfmpegAvailable() {
        return ffmpegAvailable;
    }

    public void setFfmpegAvailable(boolean ffmpegAvailable) {
        this.ffmpegAvailable = ffmpegAvailable;
    }

    public boolean isFfprobeAvailable() {
        return ffprobeAvailable;
    }

    public void setFfprobeAvailable(boolean ffprobeAvailable) {
        this.ffprobeAvailable = ffprobeAvailable;
    }

    public boolean isAiUpscaleAvailable() {
        return aiUpscaleAvailable;
    }

    public void setAiUpscaleAvailable(boolean aiUpscaleAvailable) {
        this.aiUpscaleAvailable = aiUpscaleAvailable;
    }

    public boolean isFrameInterpolationAvailable() {
        return frameInterpolationAvailable;
    }

    public void setFrameInterpolationAvailable(boolean frameInterpolationAvailable) {
        this.frameInterpolationAvailable = frameInterpolationAvailable;
    }

    public String getFfmpegPath() {
        return ffmpegPath;
    }

    public void setFfmpegPath(String ffmpegPath) {
        this.ffmpegPath = ffmpegPath;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getAvailableFeatures() {
        return availableFeatures;
    }

    public void setAvailableFeatures(List<String> availableFeatures) {
        this.availableFeatures = availableFeatures;
    }

    public List<String> getUnavailableFeatures() {
        return unavailableFeatures;
    }

    public void setUnavailableFeatures(List<String> unavailableFeatures) {
        this.unavailableFeatures = unavailableFeatures;
    }
}
