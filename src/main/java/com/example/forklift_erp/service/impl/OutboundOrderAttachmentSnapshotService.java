package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.entity.OutboundOrder;
import com.example.forklift_erp.entity.ResourceAttachment;
import com.example.forklift_erp.repository.OutboundOrderRepository;
import com.example.forklift_erp.repository.ResourceAttachmentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
class OutboundOrderAttachmentSnapshotService {
    private final OutboundOrderRepository outboundOrderRepository;
    private final ResourceAttachmentRepository attachmentRepository;

    OutboundOrderAttachmentSnapshotService(
            OutboundOrderRepository outboundOrderRepository,
            ResourceAttachmentRepository attachmentRepository
    ) {
        this.outboundOrderRepository = outboundOrderRepository;
        this.attachmentRepository = attachmentRepository;
    }

    @Transactional
    void refreshIfOutboundOrder(String resourceType, Long resourceId) {
        if (!"OUTBOUND_ORDER".equalsIgnoreCase(trimToNull(resourceType)) || resourceId == null) {
            return;
        }
        refresh(resourceId);
    }

    @Transactional
    void refresh(Long orderId) {
        OutboundOrder order = outboundOrderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return;
        }
        Optional<ResourceAttachment> invoice = latestAttachment(orderId, "INVOICE");
        Optional<ResourceAttachment> contract = latestAttachment(orderId, "CONTRACT");
        applyInvoice(order, invoice.orElse(null));
        applyContract(order, contract.orElse(null));
        outboundOrderRepository.save(order);
    }

    private Optional<ResourceAttachment> latestAttachment(Long orderId, String category) {
        return attachmentRepository.findFirstByResourceTypeAndResourceIdAndAttachmentCategoryAndDeletedFalseOrderByUploadedAtDesc(
                "OUTBOUND_ORDER", orderId, category);
    }

    private void applyInvoice(OutboundOrder order, ResourceAttachment attachment) {
        if (attachment == null) {
            order.setInvoiceStoredFileName(null);
            order.setInvoiceOriginalName(null);
            order.setInvoiceContentType(null);
            order.setInvoiceFileSize(null);
            order.setInvoiceUploadedAt(null);
            return;
        }
        order.setInvoiceStoredFileName(attachment.getStoredFileName());
        order.setInvoiceOriginalName(attachment.getOriginalName());
        order.setInvoiceContentType(attachment.getContentType());
        order.setInvoiceFileSize(attachment.getFileSize());
        order.setInvoiceUploadedAt(attachment.getUploadedAt());
    }

    private void applyContract(OutboundOrder order, ResourceAttachment attachment) {
        if (attachment == null) {
            order.setContractStoredFileName(null);
            order.setContractOriginalName(null);
            order.setContractContentType(null);
            order.setContractFileSize(null);
            order.setContractUploadedAt(null);
            return;
        }
        order.setContractStoredFileName(attachment.getStoredFileName());
        order.setContractOriginalName(attachment.getOriginalName());
        order.setContractContentType(attachment.getContentType());
        order.setContractFileSize(attachment.getFileSize());
        order.setContractUploadedAt(attachment.getUploadedAt());
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
