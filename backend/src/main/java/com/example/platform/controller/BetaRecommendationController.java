package com.example.platform.controller;

import com.example.platform.common.Result;
import com.example.platform.dto.BetaRecommendationRequest;
import com.example.platform.dto.BetaRecommendationResponse;
import com.example.platform.service.BetaRecommendationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/beta/recommendations")
public class BetaRecommendationController {
    private final BetaRecommendationService betaRecommendationService;

    public BetaRecommendationController(BetaRecommendationService betaRecommendationService) {
        this.betaRecommendationService = betaRecommendationService;
    }

    @PostMapping
    public Result<BetaRecommendationResponse> recommend(@RequestBody(required = false) BetaRecommendationRequest request) {
        return Result.success(betaRecommendationService.recommend(request));
    }
}
