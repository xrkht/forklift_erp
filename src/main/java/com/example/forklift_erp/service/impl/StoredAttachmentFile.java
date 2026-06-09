package com.example.forklift_erp.service.impl;

import java.nio.file.Path;

public record StoredAttachmentFile(
        Path filePath,
        String storedFileName,
        String originalName,
        String contentType,
        long fileSize,
        String fileExtension,
        boolean previewable,
        String storageScope
) {
}
