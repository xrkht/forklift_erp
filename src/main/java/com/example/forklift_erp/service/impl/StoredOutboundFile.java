package com.example.forklift_erp.service.impl;

import java.time.LocalDateTime;

public record StoredOutboundFile(
        String storedFileName,
        String originalName,
        String contentType,
        Long fileSize,
        LocalDateTime uploadedAt
) {
}
