package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.common.PageResult;
import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.dto.ResourceAttachmentDownload;
import com.example.forklift_erp.dto.ResourceAttachmentVO;
import com.example.forklift_erp.entity.Customer;
import com.example.forklift_erp.entity.MachineInventory;
import com.example.forklift_erp.entity.OutboundOrder;
import com.example.forklift_erp.entity.PartInventory;
import com.example.forklift_erp.entity.RepairRecord;
import com.example.forklift_erp.entity.ResourceAttachment;
import com.example.forklift_erp.exception.BusinessException;
import com.example.forklift_erp.repository.CustomerRepository;
import com.example.forklift_erp.repository.MachineInventoryRepository;
import com.example.forklift_erp.repository.OutboundOrderRepository;
import com.example.forklift_erp.repository.PartInventoryRepository;
import com.example.forklift_erp.repository.RepairRecordRepository;
import com.example.forklift_erp.repository.ResourceAttachmentRepository;
import com.example.forklift_erp.service.OperationAuditService;
import com.example.forklift_erp.service.ResourceAttachmentService;
import com.example.forklift_erp.util.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class ResourceAttachmentServiceImpl implements ResourceAttachmentService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ResourceAttachmentServiceImpl.class);
    private static final int LEGACY_BACKFILL_BATCH_SIZE = 100;

    @Autowired
    private ResourceAttachmentRepository attachmentRepository;

    @Autowired
    private OutboundOrderRepository outboundOrderRepository;

    @Autowired
    private MachineInventoryRepository machineInventoryRepository;

    @Autowired
    private RepairRecordRepository repairRecordRepository;

    @Autowired
    private PartInventoryRepository partInventoryRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OperationAuditService operationAuditService;

    @Autowired
    private ResourceAttachmentStorage attachmentStorage;

    @Autowired
    private OutboundUploadReadinessPolicy uploadReadinessPolicy;

    @Override
    @Transactional(readOnly = true)
    public PageResult<ResourceAttachmentVO> findPage(String resourceType, Long resourceId, String category,
                                                     String keyword, boolean includeDeleted, Integer page, Integer size) {
        int pageNumber = normalizePage(page);
        int pageSize = normalizeSize(size);
        Page<ResourceAttachment> result = attachmentRepository.search(
                trimToNull(resourceType),
                resourceId,
                trimToNull(category),
                trimToNull(keyword),
                includeDeleted,
                PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "uploadedAt", "id"))
        );
        return PageResult.of(result.getContent().stream().map(ResourceAttachmentVO::fromEntity).toList(),
                pageNumber, pageSize, result.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResourceAttachmentVO> findByResource(String resourceType, Long resourceId) {
        return attachmentRepository.findByResourceTypeAndResourceIdAndDeletedFalseOrderByUploadedAtDesc(resourceType, resourceId)
                .stream()
                .map(ResourceAttachmentVO::fromEntity)
                .toList();
    }

    @Override
    @Transactional
    public List<ResourceAttachmentVO> upload(String resourceType, Long resourceId, String category,
                                             String attachmentLabel, String uploadNote, MultipartFile[] files) {
        String normalizedType = normalizeResourceType(resourceType);
        String normalizedCategory = normalizeCategory(category);
        ensureResourcePermission(normalizedType);
        ResourceContext context = resolveResourceContext(normalizedType, resourceId);
        if (isOutboundOrder(normalizedType) && isOrderManagedCategory(normalizedCategory)) {
            OutboundOrder order = outboundOrderRepository.findById(resourceId)
                    .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Outbound order not found"));
            if (Boolean.TRUE.equals(order.getIsLocked()) && !SecurityUtils.isAdminOrSuperAdmin()) {
                throw new BusinessException(ResultCode.NOT_FOUND, "Outbound order not found");
            }
            if ("INVOICE".equals(normalizedCategory) && !uploadReadinessPolicy.isInvoiceUploadReady(order)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "Order is not ready for invoice upload");
            }
            if ("CONTRACT".equals(normalizedCategory) && !uploadReadinessPolicy.isContractUploadReady(order)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "Order is not ready for contract upload");
            }
        }
        MultipartFile[] payloadFiles = files == null ? new MultipartFile[0] : files;
        if (payloadFiles.length == 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请选择附件文件");
        }

        List<ResourceAttachmentVO> created = Arrays.stream(payloadFiles)
                .filter(file -> file != null && !file.isEmpty())
                .map(file -> storeAndPersist(normalizedType, normalizedCategory, context, attachmentLabel, uploadNote, file))
                .map(ResourceAttachmentVO::fromEntity)
                .toList();
        refreshOutboundOrderSnapshot(normalizedType, resourceId);
        return created;
    }

    @Override
    @Transactional
    public ResourceAttachmentVO recordLegacyOrderAttachment(OutboundOrder order, String category, StoredOutboundFile storedFile) {
        if (order == null || order.getId() == null || storedFile == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Invalid order attachment");
        }
        ResourceContext context = new ResourceContext(
                "OUTBOUND_ORDER",
                order.getId(),
                order.getOrderNo(),
                order.getCustomerName()
        );
        ResourceAttachment attachment = persistExistingFile(
                context,
                normalizeCategory(category),
                defaultAttachmentLabel(category),
                order.getOrderRemark(),
                storedFile,
                chooseStorageScope(normalizeResourceType("OUTBOUND_ORDER"), category)
        );
        refreshOutboundOrderSnapshot(order.getId());
        return ResourceAttachmentVO.fromEntity(attachment);
    }

    @Override
    @Transactional(readOnly = true)
    public ResourceAttachmentDownload download(Long id, boolean inlinePreview) {
        ResourceAttachment attachment = attachmentRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Attachment not found"));
        Path filePath = attachmentStorage.resolveAttachmentPath(attachment);
        if (!Files.isRegularFile(filePath)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Attachment file not found");
        }
        try {
            Resource resource = new UrlResource(filePath.toUri());
            String contentType = firstNonBlank(attachment.getContentType(), Files.probeContentType(filePath), "application/octet-stream");
            boolean previewable = Boolean.TRUE.equals(attachment.getPreviewable()) || inlinePreview;
            return new ResourceAttachmentDownload(
                    resource,
                    firstNonBlank(attachment.getOriginalName(), attachment.getAttachmentLabel(), "attachment"),
                    contentType,
                    Files.size(filePath),
                    previewable
            );
        } catch (IOException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "Attachment download failed");
        }
    }

    @Override
    @Transactional
    public ResourceAttachmentVO delete(Long id, String reason) {
        ResourceAttachment attachment = attachmentRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Attachment not found"));
        attachment.setDeleted(true);
        attachment.setDeletedAt(LocalDateTime.now());
        attachment.setDeletedBy(SecurityUtils.currentUsername());
        attachment.setDeleteReason(trimToNull(reason));
        ResourceAttachment saved = attachmentRepository.save(attachment);
        refreshOutboundOrderSnapshot(saved.getResourceType(), saved.getResourceId());
        Path filePath = attachmentStorage.resolveAttachmentPath(saved);
        attachmentStorage.registerAfterCommit(() -> attachmentStorage.deleteQuietly(filePath, "Failed to delete attachment file"));
        operationAuditService.record(
                "ATTACHMENT",
                "DELETE",
                saved.getResourceType(),
                saved.getResourceId(),
                saved.getResourceCode(),
                saved.getResourceName(),
                "Delete attachment: " + saved.getOriginalName(),
                saved.getDeletedBy(),
                saved.getDeleteReason()
        );
        return ResourceAttachmentVO.fromEntity(saved);
    }

    @Override
    @Transactional
    public void backfillLegacyOutboundAttachments() {
        int backfilled = 0;
        while (true) {
            List<OutboundOrder> candidates = outboundOrderRepository.findLegacyAttachmentBackfillCandidates(
                    PageRequest.of(0, LEGACY_BACKFILL_BATCH_SIZE)
            );
            if (candidates.isEmpty()) {
                break;
            }
            for (OutboundOrder order : candidates) {
                boolean changed = false;
                if (hasText(order.getInvoiceStoredFileName()) && !hasLegacyAttachment(order.getId(), "INVOICE")) {
                    changed = persistLegacyRow(order, "INVOICE", order.getInvoiceStoredFileName(), order.getInvoiceOriginalName(),
                            order.getInvoiceContentType(), order.getInvoiceFileSize(), order.getInvoiceUploadedAt()) || changed;
                }
                if (hasText(order.getContractStoredFileName()) && !hasLegacyAttachment(order.getId(), "CONTRACT")) {
                    changed = persistLegacyRow(order, "CONTRACT", order.getContractStoredFileName(), order.getContractOriginalName(),
                            order.getContractContentType(), order.getContractFileSize(), order.getContractUploadedAt()) || changed;
                }
                if (changed) {
                    refreshOutboundOrderSnapshot(order.getId());
                    backfilled++;
                }
            }
            if (candidates.size() < LEGACY_BACKFILL_BATCH_SIZE) {
                break;
            }
        }
        if (backfilled > 0) {
            log.info("Legacy outbound attachment backfill completed for {} orders", backfilled);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUpLegacyAttachments() {
        try {
            backfillLegacyOutboundAttachments();
        } catch (Exception error) {
            log.warn("Legacy attachment backfill skipped", error);
        }
    }

    private ResourceAttachment storeAndPersist(String resourceType, String category, ResourceContext context,
                                               String attachmentLabel, String uploadNote, MultipartFile file) {
        String storageScope = chooseStorageScope(resourceType, category);
        StoredAttachmentFile stored = attachmentStorage.storeFile(file, storageScope);
        attachmentStorage.registerRollbackCleanup(stored.filePath());

        ResourceAttachment attachment = new ResourceAttachment();
        attachment.setResourceType(resourceType);
        attachment.setResourceId(context.resourceId());
        attachment.setResourceCode(context.resourceCode());
        attachment.setResourceName(context.resourceName());
        attachment.setAttachmentCategory(category);
        attachment.setAttachmentLabel(trimToNull(attachmentLabel));
        attachment.setStorageScope(storageScope);
        attachment.setOriginalName(stored.originalName());
        attachment.setStoredFileName(stored.storedFileName());
        attachment.setContentType(stored.contentType());
        attachment.setFileSize(stored.fileSize());
        attachment.setFileExtension(stored.fileExtension());
        attachment.setPreviewable(stored.previewable());
        attachment.setUploadNote(trimToNull(uploadNote));
        attachment.setUploadedBy(SecurityUtils.currentUsername());
        attachment.setDeleted(false);
        ResourceAttachment saved = attachmentRepository.save(attachment);
        operationAuditService.record(
                "ATTACHMENT",
                "UPLOAD",
                resourceType,
                context.resourceId(),
                context.resourceCode(),
                context.resourceName(),
                "Upload attachment: " + saved.getOriginalName(),
                saved.getUploadedBy(),
                saved.getUploadNote()
        );
        if (isOutboundOrder(resourceType) && isOrderManagedCategory(category)) {
            refreshOutboundOrderSnapshot(context.resourceId());
        }
        return saved;
    }

    private ResourceAttachment persistExistingFile(ResourceContext context, String category, String label, String note,
                                                   StoredOutboundFile storedFile, String storageScope) {
        ResourceAttachment existing = attachmentRepository
                .findFirstByResourceTypeAndResourceIdAndAttachmentCategoryAndDeletedFalseOrderByUploadedAtDesc(
                        "OUTBOUND_ORDER",
                        context.resourceId(),
                        category
                )
                .orElse(null);
        if (existing != null
                && Objects.equals(existing.getStoredFileName(), storedFile.storedFileName())
                && Objects.equals(existing.getOriginalName(), storedFile.originalName())) {
            return existing;
        }
        ResourceAttachment attachment = new ResourceAttachment();
        attachment.setResourceType("OUTBOUND_ORDER");
        attachment.setResourceId(context.resourceId());
        attachment.setResourceCode(context.resourceCode());
        attachment.setResourceName(context.resourceName());
        attachment.setAttachmentCategory(category);
        attachment.setAttachmentLabel(trimToNull(label));
        attachment.setStorageScope(storageScope);
        attachment.setOriginalName(storedFile.originalName());
        attachment.setStoredFileName(storedFile.storedFileName());
        attachment.setContentType(storedFile.contentType());
        attachment.setFileSize(storedFile.fileSize());
        attachment.setFileExtension(StringUtils.getFilenameExtension(storedFile.originalName()));
        attachment.setPreviewable(isPreviewable(storedFile.contentType(), storedFile.originalName()));
        attachment.setUploadNote(trimToNull(note));
        attachment.setUploadedBy(SecurityUtils.currentUsername());
        attachment.setUploadedAt(storedFile.uploadedAt());
        attachment.setDeleted(false);
        return attachmentRepository.save(attachment);
    }

    private boolean persistLegacyRow(OutboundOrder order, String category, String storedFileName, String originalName,
                                     String contentType, Long fileSize, LocalDateTime uploadedAt) {
        ResourceAttachment attachment = new ResourceAttachment();
        attachment.setResourceType("OUTBOUND_ORDER");
        attachment.setResourceId(order.getId());
        attachment.setResourceCode(order.getOrderNo());
        attachment.setResourceName(order.getCustomerName());
        attachment.setAttachmentCategory(category);
        attachment.setAttachmentLabel(defaultAttachmentLabel(category));
        attachment.setStorageScope(chooseStorageScope("OUTBOUND_ORDER", category));
        attachment.setOriginalName(firstNonBlank(originalName, storedFileName));
        attachment.setStoredFileName(storedFileName);
        attachment.setContentType(firstNonBlank(contentType, "application/octet-stream"));
        attachment.setFileSize(fileSize);
        attachment.setFileExtension(StringUtils.getFilenameExtension(storedFileName));
        attachment.setPreviewable(isPreviewable(contentType, originalName));
        attachment.setUploadNote("Legacy backfill");
        attachment.setUploadedBy("system");
        attachment.setUploadedAt(uploadedAt == null ? LocalDateTime.now() : uploadedAt);
        attachment.setDeleted(false);
        attachmentRepository.save(attachment);
        return true;
    }

    private boolean hasLegacyAttachment(Long orderId, String category) {
        return attachmentRepository.findFirstByResourceTypeAndResourceIdAndAttachmentCategoryAndDeletedFalseOrderByUploadedAtDesc(
                "OUTBOUND_ORDER", orderId, category
        ).isPresent();
    }

    private void refreshOutboundOrderSnapshot(Long orderId) {
        refreshOutboundOrderSnapshot("OUTBOUND_ORDER", orderId);
    }

    private void refreshOutboundOrderSnapshot(String resourceType, Long resourceId) {
        if (!isOutboundOrder(resourceType) || resourceId == null) {
            return;
        }
        OutboundOrder order = outboundOrderRepository.findById(resourceId).orElse(null);
        if (order == null) {
            return;
        }
        Optional<ResourceAttachment> invoice = attachmentRepository
                .findFirstByResourceTypeAndResourceIdAndAttachmentCategoryAndDeletedFalseOrderByUploadedAtDesc(
                        "OUTBOUND_ORDER", resourceId, "INVOICE");
        Optional<ResourceAttachment> contract = attachmentRepository
                .findFirstByResourceTypeAndResourceIdAndAttachmentCategoryAndDeletedFalseOrderByUploadedAtDesc(
                        "OUTBOUND_ORDER", resourceId, "CONTRACT");
        if (invoice.isPresent()) {
            ResourceAttachment current = invoice.get();
            order.setInvoiceStoredFileName(current.getStoredFileName());
            order.setInvoiceOriginalName(current.getOriginalName());
            order.setInvoiceContentType(current.getContentType());
            order.setInvoiceFileSize(current.getFileSize());
            order.setInvoiceUploadedAt(current.getUploadedAt());
        } else {
            order.setInvoiceStoredFileName(null);
            order.setInvoiceOriginalName(null);
            order.setInvoiceContentType(null);
            order.setInvoiceFileSize(null);
            order.setInvoiceUploadedAt(null);
        }
        if (contract.isPresent()) {
            ResourceAttachment current = contract.get();
            order.setContractStoredFileName(current.getStoredFileName());
            order.setContractOriginalName(current.getOriginalName());
            order.setContractContentType(current.getContentType());
            order.setContractFileSize(current.getFileSize());
            order.setContractUploadedAt(current.getUploadedAt());
        } else {
            order.setContractStoredFileName(null);
            order.setContractOriginalName(null);
            order.setContractContentType(null);
            order.setContractFileSize(null);
            order.setContractUploadedAt(null);
        }
        outboundOrderRepository.save(order);
    }

    private void ensureResourcePermission(String resourceType) {
        if (SecurityUtils.hasAnyPermission(resourcePermission(resourceType), "stock:adjust")) {
            return;
        }
        throw new BusinessException(ResultCode.FORBIDDEN, "No permission to manage attachments");
    }

    private String resourcePermission(String resourceType) {
        return switch (normalizeResourceType(resourceType)) {
            case "MACHINE" -> "vehicle:write";
            case "REPAIR" -> "repair:write";
            case "OUTBOUND_ORDER" -> "stock:adjust";
            case "PART" -> "part:write";
            case "CUSTOMER" -> "vehicle:write";
            default -> "stock:adjust";
        };
    }

    private ResourceContext resolveResourceContext(String resourceType, Long resourceId) {
        if (resourceId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请选择业务对象");
        }
        return switch (normalizeResourceType(resourceType)) {
            case "MACHINE" -> machineInventoryRepository.findById(resourceId)
                    .map(machine -> new ResourceContext(
                            "MACHINE",
                            machine.getId(),
                            machine.getVehicleProductNumber(),
                            firstNonBlank(machine.getName(), machine.getSpecificationModel(), "Vehicle")
                    ))
                    .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Vehicle not found"));
            case "REPAIR" -> repairRecordRepository.findById(resourceId)
                    .map(repair -> new ResourceContext(
                            "REPAIR",
                            repair.getId(),
                            repair.getVehicleNumber(),
                            firstNonBlank(repair.getCustomerName(), repair.getFaultDescription(), "Repair")
                    ))
                    .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Repair record not found"));
            case "OUTBOUND_ORDER" -> outboundOrderRepository.findById(resourceId)
                    .map(order -> new ResourceContext(
                            "OUTBOUND_ORDER",
                            order.getId(),
                            order.getOrderNo(),
                            firstNonBlank(order.getCustomerName(), order.getResourceName(), "Outbound order")
                    ))
                    .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Outbound order not found"));
            case "PART" -> partInventoryRepository.findById(resourceId)
                    .map(part -> new ResourceContext(
                            "PART",
                            part.getId(),
                            part.getPartCode(),
                            firstNonBlank(part.getPartName(), "Part")
                    ))
                    .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Part not found"));
            case "CUSTOMER" -> customerRepository.findById(resourceId)
                    .map(customer -> new ResourceContext(
                            "CUSTOMER",
                            customer.getId(),
                            customer.getCompanyName(),
                            firstNonBlank(customer.getContactName(), customer.getCompanyName(), "Customer")
                    ))
                    .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Customer not found"));
            default -> throw new BusinessException(ResultCode.PARAM_ERROR, "Unsupported attachment resource type");
        };
    }

    private String chooseStorageScope(String resourceType, String category) {
        String normalizedType = normalizeResourceType(resourceType);
        String normalizedCategory = normalizeCategory(category);
        if ("OUTBOUND_ORDER".equals(normalizedType) && "INVOICE".equals(normalizedCategory)) {
            return "LEGACY_ORDER_INVOICE";
        }
        if ("OUTBOUND_ORDER".equals(normalizedType) && "CONTRACT".equals(normalizedCategory)) {
            return "LEGACY_ORDER_CONTRACT";
        }
        return "ATTACHMENT";
    }

    private boolean isOutboundOrder(String resourceType) {
        return "OUTBOUND_ORDER".equalsIgnoreCase(trimToNull(resourceType));
    }

    private boolean isOrderManagedCategory(String category) {
        String normalized = normalizeCategory(category);
        return "INVOICE".equals(normalized) || "CONTRACT".equals(normalized);
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

    private int normalizePage(Integer page) {
        return Math.max(0, page == null ? 0 : page);
    }

    private int normalizeSize(Integer size) {
        return Math.max(1, size == null ? 20 : size);
    }

    private String normalizeResourceType(String resourceType) {
        String normalized = trimToNull(resourceType);
        if (normalized == null) {
            return "OUTBOUND_ORDER";
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeCategory(String category) {
        String normalized = trimToNull(category);
        if (normalized == null) {
            return "OTHER";
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String defaultAttachmentLabel(String category) {
        return switch (normalizeCategory(category)) {
            case "INVOICE" -> "发票";
            case "CONTRACT" -> "合同";
            case "PHOTO" -> "图片";
            default -> "附件";
        };
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
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

record ResourceContext(String resourceType, Long resourceId, String resourceCode, String resourceName) {
}
