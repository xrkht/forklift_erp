package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.entity.ResourceAttachment;
import com.example.forklift_erp.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
public class ResourceAttachmentStorage {
    private static final long MAX_ATTACHMENT_FILE_SIZE = 30L * 1024 * 1024;
    private static final Set<String> ALLOWED_ATTACHMENT_EXTENSIONS = new HashSet<>(Arrays.asList(
            "pdf", "ofd", "jpg", "jpeg", "png", "webp", "gif", "bmp",
            "doc", "docx", "xls", "xlsx", "csv"
    ));

    @Value("${forklift-erp.attachment-storage-dir:${FORKLIFT_ERP_ATTACHMENT_STORAGE_DIR:uploads/attachments}}")
    private String attachmentStorageDir;

    @Value("${forklift-erp.invoice-storage-dir:${FORKLIFT_ERP_INVOICE_STORAGE_DIR:uploads/invoices}}")
    private String invoiceStorageDir;

    @Value("${forklift-erp.contract-storage-dir:${FORKLIFT_ERP_CONTRACT_STORAGE_DIR:uploads/contracts}}")
    private String contractStorageDir;

    StoredAttachmentFile storeFile(MultipartFile file, String storageScope) {
        ensureFilePresent(file);
        String originalName = cleanOriginalName(file.getOriginalFilename(), "attachment.bin");
        String extension = StringUtils.getFilenameExtension(originalName);
        if (extension == null || !ALLOWED_ATTACHMENT_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Unsupported attachment file type");
        }
        if (file.getSize() > MAX_ATTACHMENT_FILE_SIZE) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Attachment file cannot exceed 30MB");
        }

        Path root = storageRoot(storageScope);
        String storedFileName = buildStoredFileName(storageScope, originalName);
        Path target = root.resolve(storedFileName).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Attachment path is invalid");
        }

        Path tempFile = null;
        try {
            Files.createDirectories(root);
            tempFile = Files.createTempFile(root, storedFileName + "-", ".tmp");
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            moveIntoPlace(tempFile, target);
            String contentType = firstNonBlank(file.getContentType(), Files.probeContentType(target), "application/octet-stream");
            return new StoredAttachmentFile(
                    target,
                    storedFileName,
                    originalName,
                    contentType,
                    file.getSize(),
                    extension.toLowerCase(Locale.ROOT),
                    isPreviewable(contentType, originalName),
                    storageScope
            );
        } catch (IOException e) {
            deleteQuietly(tempFile, "Failed to delete temp attachment");
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "Attachment file save failed");
        }
    }

    Path resolveAttachmentPath(ResourceAttachment attachment) {
        Path root = storageRoot(attachment.getStorageScope());
        Path path = root.resolve(attachment.getStoredFileName()).normalize();
        if (!path.startsWith(root)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Attachment path is invalid");
        }
        return path;
    }

    void registerRollbackCleanup(Path file) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    deleteQuietly(file, "Failed to delete rolled back attachment");
                }
            }
        });
    }

    void registerAfterCommit(Runnable runnable) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            runnable.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runnable.run();
            }
        });
    }

    void deleteQuietly(Path path, String message) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("{}: {}", message, path, e);
        }
    }

    private void ensureFilePresent(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请选择附件文件");
        }
    }

    private String cleanOriginalName(String originalFilename, String fallbackName) {
        String originalName = StringUtils.cleanPath(Objects.requireNonNullElse(originalFilename, fallbackName)).trim();
        if (originalName.isBlank() || originalName.contains("..") || originalName.contains("/") || originalName.contains("\\")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Attachment file name is invalid");
        }
        return originalName;
    }

    private Path storageRoot(String storageScope) {
        return switch (storageScope) {
            case "LEGACY_ORDER_INVOICE" -> Paths.get(invoiceStorageDir).toAbsolutePath().normalize();
            case "LEGACY_ORDER_CONTRACT" -> Paths.get(contractStorageDir).toAbsolutePath().normalize();
            default -> Paths.get(attachmentStorageDir).toAbsolutePath().normalize();
        };
    }

    private String buildStoredFileName(String storageScope, String originalName) {
        String extension = StringUtils.getFilenameExtension(originalName);
        String safeExtension = extension == null ? "bin" : extension.toLowerCase(Locale.ROOT);
        String prefix = switch (storageScope) {
            case "LEGACY_ORDER_INVOICE" -> "invoice";
            case "LEGACY_ORDER_CONTRACT" -> "contract";
            default -> "attachment";
        };
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "") + "." + safeExtension;
    }

    private void moveIntoPlace(Path tempFile, Path target) throws IOException {
        try {
            Files.move(tempFile, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private boolean isPreviewable(String contentType, String originalName) {
        String normalized = firstNonBlank(contentType, "");
        if (normalized.startsWith("image/")) {
            return true;
        }
        if ("application/pdf".equalsIgnoreCase(normalized)) {
            return true;
        }
        String extension = StringUtils.getFilenameExtension(originalName);
        return extension != null && Set.of("jpg", "jpeg", "png", "webp", "gif", "bmp", "pdf").contains(extension.toLowerCase(Locale.ROOT));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
