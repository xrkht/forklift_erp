package com.example.forklift_erp.service.impl;

record ResourceContext(
        String resourceType,
        Long resourceId,
        String resourceCode,
        String resourceName,
        Boolean locked
) {
    ResourceContext(String resourceType, Long resourceId, String resourceCode, String resourceName) {
        this(resourceType, resourceId, resourceCode, resourceName, null);
    }
}
