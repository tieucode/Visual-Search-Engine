package com.visualsearch.dto.admin;

public record OcrSearchIndexStatusData(long totalImagesWithOcr, long indexed, long pending, long retrying) {}
