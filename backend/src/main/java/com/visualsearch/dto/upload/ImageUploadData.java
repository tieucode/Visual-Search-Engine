package com.visualsearch.dto.upload;

import com.visualsearch.enums.BatchStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageUploadData {

    private UUID batchId;
    private int uploadedCount;
    private int failedCount;
    private BatchStatus batchStatus;
    private List<String> errors;
}
