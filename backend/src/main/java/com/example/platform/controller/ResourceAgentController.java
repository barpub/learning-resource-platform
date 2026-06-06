package com.example.platform.controller;

import com.example.platform.common.Result;
import com.example.platform.dto.ResourceAgentRemoteSummaryRequest;
import com.example.platform.dto.ResourceAgentSearchResponse;
import com.example.platform.dto.ResourceContentSummary;
import com.example.platform.service.ResourceAgentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
public class ResourceAgentController {
    private final ResourceAgentService resourceAgentService;

    public ResourceAgentController(ResourceAgentService resourceAgentService) {
        this.resourceAgentService = resourceAgentService;
    }

    @GetMapping("/search")
    public Result<ResourceAgentSearchResponse> search(@RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) String source,
                                                      @RequestParam(required = false) Integer limit) {
        return Result.success(resourceAgentService.search(keyword, source, limit));
    }

    @GetMapping("/resources/{id}/summary")
    public Result<ResourceContentSummary> summarizeLocal(@PathVariable Long id) {
        return Result.success(resourceAgentService.summarizeLocal(id));
    }

    @GetMapping("/folders/{id}/summary")
    public Result<ResourceContentSummary> summarizeFolder(@PathVariable Long id,
                                                          @RequestParam(required = false) String path) {
        return Result.success(resourceAgentService.summarizeVirtualFolder(id, path));
    }

    @PostMapping("/remote/summary")
    public Result<ResourceContentSummary> summarizeRemote(@RequestBody ResourceAgentRemoteSummaryRequest request) {
        return Result.success(resourceAgentService.summarizeRemote(request));
    }
}
