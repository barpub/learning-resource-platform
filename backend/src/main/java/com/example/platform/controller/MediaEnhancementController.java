package com.example.platform.controller;

import com.example.platform.common.Result;
import com.example.platform.dto.MediaEnhancementCapability;
import com.example.platform.dto.MediaEnhancementRequest;
import com.example.platform.entity.MediaEnhancementJob;
import com.example.platform.service.MediaEnhancementService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MediaEnhancementController {
    private final MediaEnhancementService mediaEnhancementService;

    public MediaEnhancementController(MediaEnhancementService mediaEnhancementService) {
        this.mediaEnhancementService = mediaEnhancementService;
    }

    @GetMapping("/media-enhancements/capabilities")
    public Result<MediaEnhancementCapability> capabilities() {
        return Result.success(mediaEnhancementService.capability());
    }

    @PostMapping("/resources/{resourceId}/enhancements")
    public Result<MediaEnhancementJob> submit(@PathVariable Long resourceId,
                                              @RequestBody(required = false) MediaEnhancementRequest request) {
        return Result.success(mediaEnhancementService.submit(resourceId, request));
    }

    @GetMapping("/resources/{resourceId}/enhancements")
    public Result<List<MediaEnhancementJob>> listByResource(@PathVariable Long resourceId) {
        return Result.success(mediaEnhancementService.listByResource(resourceId));
    }

    @GetMapping("/media-enhancements/{id}")
    public Result<MediaEnhancementJob> get(@PathVariable Long id) {
        return Result.success(mediaEnhancementService.get(id));
    }

    @PostMapping("/media-enhancements/{id}/cancel")
    public Result<MediaEnhancementJob> cancel(@PathVariable Long id) {
        return Result.success(mediaEnhancementService.cancel(id));
    }
}
