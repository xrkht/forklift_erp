package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.dto.OutboundInvoiceDownload;
import com.example.forklift_erp.entity.OutboundOrder;
import com.example.forklift_erp.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class OutboundOrderFileStorage {

    private static final long MAX_INVOICE_FILE_SIZE = 20L * 1024 * 1024;
    private static final long MAX_CONTRACT_FILE_SIZE = 20L * 1024 * 1024;
    private static final FileStorageSupport.UploadConstraints INVOICE_CONSTRAINTS =
            new FileStorageSupport.UploadConstraints(
                    MAX_INVOICE_FILE_SIZE,
                    Set.of("pdf", "ofd", "jpg", "jpeg", "png", "webp"),
                    "Invoice file is required",
                    "Invoice file cannot exceed 20MB",
                    "Unsupported invoice file type"
            );
    private static final FileStorageSupport.UploadConstraints CONTRACT_CONSTRAINTS =
            new FileStorageSupport.UploadConstraints(
                    MAX_CONTRACT_FILE_SIZE,
                    Set.of("pdf", "ofd", "doc", "docx", "jpg", "jpeg", "png", "webp"),
                    "Contract file is required",
                    "Contract file cannot exceed 20MB",
                    "Unsupported contract file type"
            );

    private final FileStorageSupport fileStorageSupport;
    private final String invoiceStorageDir;
    private final String contractStorageDir;

    public OutboundOrderFileStorage(
            FileStorageSupport fileStorageSupport,
            @Value("${forklift-erp.invoice-storage-dir:${forklift.invoice-storage-dir:${FORKLIFT_ERP_INVOICE_STORAGE_DIR:uploads/invoices}}}")
            String invoiceStorageDir,
            @Value("${forklift-erp.contract-storage-dir:${forklift.contract-storage-dir:${FORKLIFT_ERP_CONTRACT_STORAGE_DIR:uploads/contracts}}}")
            String contractStorageDir
    ) {
        this.fileStorageSupport = fileStorageSupport;
        this.invoiceStorageDir = invoiceStorageDir;
        this.contractStorageDir = contractStorageDir;
    }

    StoredOutboundFile storeInvoice(Long orderId, MultipartFile file, String previousFileName) {
        String originalName = fileStorageSupport.cleanOriginalName(
                file == null ? null : file.getOriginalFilename(),
                "invoice.pdf",
                "Invoice file name is invalid"
        );
        FileStorageSupport.StoredFile stored = fileStorageSupport.store(
                file,
                invoiceStorageRoot(),
                storedName(orderId, originalName),
                originalName,
                INVOICE_CONSTRAINTS,
                "File path is invalid",
                "Invoice file save failed"
        );
        fileStorageSupport.registerStoredFileLifecycle(
                stored.filePath(),
                () -> deleteStoredInvoice(previousFileName),
                "Failed to delete rolled back upload"
        );
        return new StoredOutboundFile(
                stored.storedFileName(),
                stored.originalName(),
                stored.contentType(),
                stored.fileSize(),
                LocalDateTime.now()
        );
    }

    OutboundInvoiceDownload downloadInvoice(OutboundOrder order) {
        if (order.getInvoiceStoredFileName() == null || order.getInvoiceStoredFileName().isBlank()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Invoice has not been uploaded");
        }
        return download(
                resolveInvoiceFile(order.getInvoiceStoredFileName()),
                fileStorageSupport.firstNonBlank(order.getInvoiceOriginalName(), "invoice-" + order.getOrderNo()),
                fileStorageSupport.firstNonBlank(order.getInvoiceContentType(), "application/octet-stream"),
                "Invoice file not found",
                "Invoice file download failed"
        );
    }

    StoredOutboundFile storeContract(Long orderId, MultipartFile file, String previousFileName) {
        String originalName = fileStorageSupport.cleanOriginalName(
                file == null ? null : file.getOriginalFilename(),
                "contract.pdf",
                "Contract file name is invalid"
        );
        FileStorageSupport.StoredFile stored = fileStorageSupport.store(
                file,
                contractStorageRoot(),
                storedName(orderId, originalName),
                originalName,
                CONTRACT_CONSTRAINTS,
                "File path is invalid",
                "Contract file save failed"
        );
        fileStorageSupport.registerStoredFileLifecycle(
                stored.filePath(),
                () -> deleteStoredContract(previousFileName),
                "Failed to delete rolled back upload"
        );
        return new StoredOutboundFile(
                stored.storedFileName(),
                stored.originalName(),
                stored.contentType(),
                stored.fileSize(),
                LocalDateTime.now()
        );
    }

    OutboundInvoiceDownload downloadContract(OutboundOrder order) {
        if (order.getContractStoredFileName() == null || order.getContractStoredFileName().isBlank()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Contract has not been uploaded");
        }
        return download(
                resolveContractFile(order.getContractStoredFileName()),
                fileStorageSupport.firstNonBlank(order.getContractOriginalName(), "contract-" + order.getOrderNo()),
                fileStorageSupport.firstNonBlank(order.getContractContentType(), "application/octet-stream"),
                "Contract file not found",
                "Contract file download failed"
        );
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
        return fileStorageSupport.storageRoot(invoiceStorageDir);
    }

    private Path contractStorageRoot() {
        return fileStorageSupport.storageRoot(contractStorageDir);
    }

    private Path resolveInvoiceFile(String storedFileName) {
        return fileStorageSupport.resolveInRoot(invoiceStorageRoot(), storedFileName, "Invoice file path is invalid");
    }

    private Path resolveContractFile(String storedFileName) {
        return fileStorageSupport.resolveInRoot(contractStorageRoot(), storedFileName, "Contract file path is invalid");
    }

    private void deleteStoredInvoice(String storedFileName) {
        if (storedFileName == null || storedFileName.isBlank()) {
            return;
        }
        fileStorageSupport.deleteQuietly(resolveInvoiceFile(storedFileName), "Failed to delete invoice file");
    }

    private void deleteStoredContract(String storedFileName) {
        if (storedFileName == null || storedFileName.isBlank()) {
            return;
        }
        fileStorageSupport.deleteQuietly(resolveContractFile(storedFileName), "Failed to delete contract file");
    }
}
