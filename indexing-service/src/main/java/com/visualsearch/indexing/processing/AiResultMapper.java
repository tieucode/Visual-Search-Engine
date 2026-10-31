package com.visualsearch.indexing.processing;
import com.visualsearch.indexing.client.ai.AiBatchResponse;
import org.springframework.stereotype.Component;
@Component
@lombok.extern.slf4j.Slf4j
public class AiResultMapper {
    public java.util.Map<java.util.UUID, java.util.List<AiBatchResponse.Result>> group(
            AiBatchResponse response, java.util.Set<java.util.UUID> requested) {
        var grouped = response.results().stream().filter(result -> result.imageId() != null)
                .collect(java.util.stream.Collectors.groupingBy(AiBatchResponse.Result::imageId));
        var unknown = new java.util.HashSet<>(grouped.keySet());
        unknown.removeAll(requested);
        if (!unknown.isEmpty()) log.warn("AI response contained unknown imageIds={}", unknown);
        return grouped;
    }

    public OcrData toOcrData(AiBatchResponse.OcrItem item) {
        AiBatchResponse.BoundingBox box = item.boundingBox();
        OcrData.BoundingBox boundingBox = box == null ? null : new OcrData.BoundingBox(
                safeFloat(box.x()), safeFloat(box.y()), safeFloat(box.width()), safeFloat(box.height()));
        return new OcrData(
                item.text(),
                item.normalizedText(),
                safeFloat(item.confidence()),
                boundingBox);
    }

    private float safeFloat(Float value) {
        return value == null ? 0.0f : value;
    }

}
