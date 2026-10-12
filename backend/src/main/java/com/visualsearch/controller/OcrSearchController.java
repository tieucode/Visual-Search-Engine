package com.visualsearch.controller;

import com.visualsearch.dto.search.OcrSearchResult;
import com.visualsearch.service.OcrSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/search/ocr")
@RequiredArgsConstructor
public class OcrSearchController {

    private final OcrSearchService ocrSearchService;

    @GetMapping
    public List<OcrSearchResult> search(
            @RequestParam("q") String keyword
    ) {
        return ocrSearchService.search(keyword);
    }
}