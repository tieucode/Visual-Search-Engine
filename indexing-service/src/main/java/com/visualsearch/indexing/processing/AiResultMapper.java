package com.visualsearch.indexing.processing;

import com.visualsearch.indexing.client.ai.AiBatchResponse;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Slf4j
public class AiResultMapper {
    public Map<UUID, List<AiBatchResponse.Result>> group(
            AiBatchResponse response, Set<UUID> requested) {
        var grouped =
                response.results().stream()
                        .filter(result -> result.imageId() != null)
                        .collect(Collectors.groupingBy(AiBatchResponse.Result::imageId));
        var unknown = new HashSet<>(grouped.keySet());
        unknown.removeAll(requested);
        if (!unknown.isEmpty()) log.warn("AI response contained unknown imageIds={}", unknown);
        return grouped;
    }

    public OcrData toOcrData(AiBatchResponse.OcrItem item) {
        AiBatchResponse.BoundingBox box = item.boundingBox();
        OcrData.BoundingBox boundingBox =
                box == null
                        ? null
                        : new OcrData.BoundingBox(
                                safeFloat(box.x()),
                                safeFloat(box.y()),
                                safeFloat(box.width()),
                                safeFloat(box.height()));
        return new OcrData(
                item.text(), item.normalizedText(), safeFloat(item.confidence()), boundingBox);
    }

    private float safeFloat(Float value) {
        return value == null || !Float.isFinite(value) ? 0.0f : value;
    }
}
