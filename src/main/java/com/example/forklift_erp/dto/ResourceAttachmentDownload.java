package com.example.forklift_erp.dto;

import org.springframework.core.io.Resource;

public record ResourceAttachmentDownload(
        Resource resource,
        String originalName,
        String contentType,
        long contentLength,
        boolean previewable
) {
}
