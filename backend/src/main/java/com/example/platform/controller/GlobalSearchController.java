package com.example.platform.controller;

import com.example.platform.common.Result;
import com.example.platform.dto.GlobalSearchResponse;
import com.example.platform.service.GlobalSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
public class GlobalSearchController {
    private final GlobalSearchService globalSearchService;

    public GlobalSearchController(GlobalSearchService globalSearchService) {
        this.globalSearchService = globalSearchService;
    }

    @GetMapping("/global")
    public Result<GlobalSearchResponse> global(@RequestParam(required = false) String keyword,
                                               @RequestParam(defaultValue = "all") String source,
                                               @RequestParam(defaultValue = "30") Integer limit) {
        return Result.success(globalSearchService.search(keyword, source, limit));
    }
}
