package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Component
public class FileStorageSupport {

    record UploadConstraints(
            long maxSize,
            Set<String> allowedExtensions,
            String missingMessage,
            String sizeMessage,
            String typeMessage
    ) {
    }

    record StoredFile(
            Path filePath,
            String storedFileName,
            String originalName,
            String contentType,
            long fileSize,
            String fileExtension
    ) {
    }

    StoredFile store(
            MultipartFile file,
            Path root,
            String storedFileName,
            String originalName,
            UploadConstraints constraints,
            String invalidPathMessage,
            String failureMessage
    ) {
        ensureFilePresent(file, constraints.missingMessage());
        String extension = validateUpload(file, originalName, constraints);
        Path target = resolveInRoot(root, storedFileName, invalidPathMessage);
        Path normalizedRoot = root.toAbsolutePath().normalize();

        Path tempFile = null;
        try {
            Files.createDirectories(normalizedRoot);
            tempFile = Files.createTempFile(normalizedRoot, tempPrefix(storedFileName), ".tmp");
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            moveIntoPlace(tempFile, target);
            return new StoredFile(
                    target,
                    target.getFileName().toString(),
                    originalName,
                    firstNonBlank(file.getContentType(), probeContentType(target), "application/octet-stream"),
                    file.getSize(),
                    extension
            );
        } catch (IOException e) {
            deleteQuietly(tempFile, "Failed to delete temp upload");
            throw new BusinessException(ResultCode.SYSTEM_ERROR, failureMessage);
        }
    }

    Path storageRoot(String configuredDirectory) {
        return Paths.get(configuredDirectory).toAbsolutePath().normalize();
    }

    String cleanOriginalName(String originalFilename, String fallbackName, String invalidMessage) {
        String originalName = StringUtils.cleanPath(Objects.requireNonNullElse(originalFilename, fallbackName)).trim();
        if (originalName.isBlank() || originalName.contains("..") || originalName.contains("/") || originalName.contains("\\")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, invalidMessage);
        }
        return originalName;
    }

    Path resolveInRoot(Path root, String storedFileName, String invalidPathMessage) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        String cleanName = StringUtils.cleanPath(Objects.requireNonNullElse(storedFileName, "")).trim();
        Path filePath = normalizedRoot.resolve(cleanName).normalize();
        if (cleanName.isBlank() || !filePath.startsWith(normalizedRoot)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, invalidPathMessage);
        }
        return filePath;
    }

    void registerStoredFileLifecycle(Path newFile, Runnable afterCommitAction, String rollbackMessage) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                afterCommitAction.run();
            }

            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    deleteQuietly(newFile, rollbackMessage);
                }
            }
        });
    }

    void registerRollbackCleanup(Path file, String message) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    deleteQuietly(file, message);
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

    boolean isPreviewable(String contentType, String originalName) {
        String normalized = firstNonBlank(contentType, "");
        if (normalized.startsWith("image/")) {
            return true;
        }
        if ("application/pdf".equalsIgnoreCase(normalized)) {
            return true;
        }
        String extension = StringUtils.getFilenameExtension(originalName);
        return extension != null && Set.of("jpg", "jpeg", "png", "webp", "gif", "bmp", "pdf")
                .contains(extension.toLowerCase(Locale.ROOT));
    }

    String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String validateUpload(MultipartFile file, String originalName, UploadConstraints constraints) {
        if (file.getSize() > constraints.maxSize()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, constraints.sizeMessage());
        }
        String extension = StringUtils.getFilenameExtension(originalName);
        String normalizedExtension = extension == null ? null : extension.toLowerCase(Locale.ROOT);
        if (normalizedExtension == null || !constraints.allowedExtensions().contains(normalizedExtension)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, constraints.typeMessage());
        }
        return normalizedExtension;
    }

    private void ensureFilePresent(MultipartFile file, String missingMessage) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, missingMessage);
        }
    }

    private void moveIntoPlace(Path tempFile, Path target) throws IOException {
        try {
            Files.move(tempFile, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String tempPrefix(String storedFileName) {
        String prefix = StringUtils.cleanPath(Objects.requireNonNullElse(storedFileName, "upload"))
                .replace("/", "-")
                .replace("\\", "-");
        return prefix.length() < 3 ? "upload-" + prefix : prefix + "-";
    }

    private String probeContentType(Path target) {
        try {
            return Files.probeContentType(target);
        } catch (IOException e) {
            return null;
        }
    }
}
