package com.example.forklift_erp.dto;

import org.springframework.core.io.Resource;

public record OutboundInvoiceDownload(
        Resource resource,
        String originalName,
        String contentType,
        long contentLength
) {
}
