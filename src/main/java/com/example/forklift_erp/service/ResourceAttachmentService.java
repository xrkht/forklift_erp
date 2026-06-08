package com.example.forklift_erp.service;

import com.example.forklift_erp.common.PageResult;
import com.example.forklift_erp.dto.ResourceAttachmentDownload;
import com.example.forklift_erp.dto.ResourceAttachmentVO;
import com.example.forklift_erp.entity.OutboundOrder;
import com.example.forklift_erp.service.impl.StoredOutboundFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ResourceAttachmentService {
    PageResult<ResourceAttachmentVO> findPage(String resourceType, Long resourceId, String category,
                                              String keyword, boolean includeDeleted, Integer page, Integer size);

    List<ResourceAttachmentVO> findByResource(String resourceType, Long resourceId);

    List<ResourceAttachmentVO> upload(String resourceType, Long resourceId, String category,
                                      String attachmentLabel, String uploadNote, MultipartFile[] files);

    ResourceAttachmentVO recordLegacyOrderAttachment(OutboundOrder order, String category, StoredOutboundFile storedFile);

    ResourceAttachmentDownload download(Long id, boolean inlinePreview);

    ResourceAttachmentVO delete(Long id, String reason);

    void backfillLegacyOutboundAttachments();
}
