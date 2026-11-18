package com.visualsearch.controller;

import com.visualsearch.dto.BaseResponse;
import com.visualsearch.dto.search.SearchImageData;
import com.visualsearch.dto.search.SearchPageData;
import com.visualsearch.entity.User;
import com.visualsearch.service.search.RecommendationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class RecommendationController {
    private final RecommendationService recommendations;

    @GetMapping
    public ResponseEntity<BaseResponse<SearchPageData<SearchImageData>>> start(
            @RequestParam(defaultValue = "20") int size, @AuthenticationPrincipal User user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(BaseResponse.of(200, "Recommendations retrieved", recommendations.start(user, size)));
    }

    @GetMapping("/{feedId}/results")
    public ResponseEntity<BaseResponse<SearchPageData<SearchImageData>>> next(
            @PathVariable UUID feedId, @RequestParam String cursor, @AuthenticationPrincipal User user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(BaseResponse.of(200, "Recommendations retrieved", recommendations.next(user, feedId, cursor)));
    }

    @PostMapping("/clicks/{imageId}")
    public BaseResponse<Void> click(@PathVariable UUID imageId, @AuthenticationPrincipal User user) {
        recommendations.recordClick(user, imageId);
        return BaseResponse.of(200, "Image click recorded", null);
    }
}
