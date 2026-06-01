package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.dto.OutboundInvoiceDownload;
import com.example.forklift_erp.entity.OutboundOrder;
import com.example.forklift_erp.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
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
@Service
public class OutboundOrderFileStorage {

    private static final long MAX_INVOICE_FILE_SIZE = 20L * 1024 * 1024;
    private static final long MAX_CONTRACT_FILE_SIZE = 20L * 1024 * 1024;
    private static final Set<String> ALLOWED_INVOICE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "pdf", "ofd", "jpg", "jpeg", "png", "webp"
    ));
    private static final Set<String> ALLOWED_CONTRACT_EXTENSIONS = new HashSet<>(Arrays.asList(
            "pdf", "ofd", "doc", "docx", "jpg", "jpeg", "png", "webp"
    ));

    @Value("${forklift-erp.invoice-storage-dir:${FORKLIFT_ERP_INVOICE_STORAGE_DIR:uploads/invoices}}")
    private String invoiceStorageDir;

    @Value("${forklift-erp.contract-storage-dir:${FORKLIFT_ERP_CONTRACT_STORAGE_DIR:uploads/contracts}}")
    private String contractStorageDir;

    StoredOutboundFile storeInvoice(Long orderId, MultipartFile file, String previousFileName) {
        ensureFilePresent(file, "Invoice file is required");
        String originalName = cleanOriginalName(file.getOriginalFilename(), "invoice.pdf", "Invoice file name is invalid");
        validateUpload(file, originalName, MAX_INVOICE_FILE_SIZE, ALLOWED_INVOICE_EXTENSIONS,
                "Invoice file is required", "Invoice file cannot exceed 20MB", "Unsupported invoice file type");
        Path target = storeUploadedFile(file, invoiceStorageRoot(), storedName(orderId, originalName), "Invoice file save failed");
        registerStoredFileLifecycle(target, () -> deleteStoredInvoice(previousFileName));
        return new StoredOutboundFile(
                target.getFileName().toString(),
                originalName,
                firstNonBlank(file.getContentType(), probeContentType(target), "application/octet-stream"),
                file.getSize(),
                LocalDateTime.now()
        );
    }

    OutboundInvoiceDownload downloadInvoice(OutboundOrder order) {
        if (order.getInvoiceStoredFileName() == null || order.getInvoiceStoredFileName().isBlank()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Invoice has not been uploaded");
        }
        return download(
                resolveInvoiceFile(order.getInvoiceStoredFileName()),
                firstNonBlank(order.getInvoiceOriginalName(), "invoice-" + order.getOrderNo()),
                firstNonBlank(order.getInvoiceContentType(), "application/octet-stream"),
                "Invoice file not found",
                "Invoice file download failed"
        );
    }

    StoredOutboundFile storeContract(Long orderId, MultipartFile file, String previousFileName) {
        ensureFilePresent(file, "Contract file is required");
        String originalName = cleanOriginalName(file.getOriginalFilename(), "contract.pdf", "Contract file name is invalid");
        validateUpload(file, originalName, MAX_CONTRACT_FILE_SIZE, ALLOWED_CONTRACT_EXTENSIONS,
                "Contract file is required", "Contract file cannot exceed 20MB", "Unsupported contract file type");
        Path target = storeUploadedFile(file, contractStorageRoot(), storedName(orderId, originalName), "Contract file save failed");
        registerStoredFileLifecycle(target, () -> deleteStoredContract(previousFileName));
        return new StoredOutboundFile(
                target.getFileName().toString(),
                originalName,
                firstNonBlank(file.getContentType(), probeContentType(target), "application/octet-stream"),
                file.getSize(),
                LocalDateTime.now()
        );
    }

    OutboundInvoiceDownload downloadContract(OutboundOrder order) {
        if (order.getContractStoredFileName() == null || order.getContractStoredFileName().isBlank()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Contract has not been uploaded");
        }
        return download(
                resolveContractFile(order.getContractStoredFileName()),
                firstNonBlank(order.getContractOriginalName(), "contract-" + order.getOrderNo()),
                firstNonBlank(order.getContractContentType(), "application/octet-stream"),
                "Contract file not found",
                "Contract file download failed"
        );
    }

    private void validateUpload(
            MultipartFile file,
            String originalName,
            long maxSize,
            Set<String> allowedExtensions,
            String missingMessage,
            String sizeMessage,
            String typeMessage
    ) {
        ensureFilePresent(file, missingMessage);
        if (file.getSize() > maxSize) {
            throw new BusinessException(ResultCode.PARAM_ERROR, sizeMessage);
        }
        String extension = StringUtils.getFilenameExtension(originalName);
        if (extension == null || !allowedExtensions.contains(extension.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ResultCode.PARAM_ERROR, typeMessage);
        }
    }

    private void ensureFilePresent(MultipartFile file, String missingMessage) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, missingMessage);
        }
    }

    private String cleanOriginalName(String originalFilename, String fallbackName, String invalidMessage) {
        String originalName = StringUtils.cleanPath(Objects.requireNonNullElse(originalFilename, fallbackName)).trim();
        if (originalName.isBlank() || originalName.contains("..") || originalName.contains("/") || originalName.contains("\\")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, invalidMessage);
        }
        return originalName;
    }

    private String storedName(Long orderId, String originalName) {
        String extension = Objects.requireNonNull(StringUtils.getFilenameExtension(originalName)).toLowerCase(Locale.ROOT);
        return "order-" + orderId + "-" + UUID.randomUUID().toString().replace("-", "") + "." + extension;
    }

    private OutboundInvoiceDownload download(
            Path filePath,
            String originalName,
            String contentType,
            String notFoundMessage,
            String failureMessage
    ) {
        if (!Files.isRegularFile(filePath)) {
            throw new BusinessException(ResultCode.NOT_FOUND, notFoundMessage);
        }
        try {
            Resource resource = new UrlResource(filePath.toUri());
            return new OutboundInvoiceDownload(resource, originalName, contentType, Files.size(filePath));
        } catch (IOException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, failureMessage);
        }
    }

    private Path invoiceStorageRoot() {
        return Paths.get(invoiceStorageDir).toAbsolutePath().normalize();
    }

    private Path contractStorageRoot() {
        return Paths.get(contractStorageDir).toAbsolutePath().normalize();
    }

    private Path storeUploadedFile(MultipartFile file, Path root, String storedFileName, String failureMessage) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path target = normalizedRoot.resolve(storedFileName).normalize();
        if (!target.startsWith(normalizedRoot)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "File path is invalid");
        }

        Path tempFile = null;
        try {
            Files.createDirectories(normalizedRoot);
            tempFile = Files.createTempFile(normalizedRoot, storedFileName + "-", ".tmp");
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            moveIntoPlace(tempFile, target);
            return target;
        } catch (IOException e) {
            deleteQuietly(tempFile, "Failed to delete temp upload");
            throw new BusinessException(ResultCode.SYSTEM_ERROR, failureMessage);
        }
    }

    private void moveIntoPlace(Path tempFile, Path target) throws IOException {
        try {
            Files.move(tempFile, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void registerStoredFileLifecycle(Path newFile, Runnable deletePreviousFile) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deletePreviousFile.run();
            }

            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    deleteQuietly(newFile, "Failed to delete rolled back upload");
                }
            }
        });
    }

    private Path resolveInvoiceFile(String storedFileName) {
        Path root = invoiceStorageRoot();
        Path filePath = root.resolve(storedFileName).normalize();
        if (!filePath.startsWith(root)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Invoice file path is invalid");
        }
        return filePath;
    }

    private Path resolveContractFile(String storedFileName) {
        Path root = contractStorageRoot();
        Path filePath = root.resolve(storedFileName).normalize();
        if (!filePath.startsWith(root)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Contract file path is invalid");
        }
        return filePath;
    }

    private void deleteStoredInvoice(String storedFileName) {
        if (storedFileName == null || storedFileName.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolveInvoiceFile(storedFileName));
        } catch (IOException e) {
            log.warn("Failed to delete invoice file: {}", storedFileName, e);
        }
    }

    private void deleteStoredContract(String storedFileName) {
        if (storedFileName == null || storedFileName.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolveContractFile(storedFileName));
        } catch (IOException e) {
            log.warn("Failed to delete contract file: {}", storedFileName, e);
        }
    }

    private void deleteQuietly(Path path, String message) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("{}: {}", message, path, e);
        }
    }

    private String probeContentType(Path target) {
        try {
            return Files.probeContentType(target);
        } catch (IOException e) {
            return null;
        }
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
