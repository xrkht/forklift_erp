package com.example.forklift_erp.dto;

import com.example.forklift_erp.entity.ResourceAttachment;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ResourceAttachmentVO {
    private Long id;
    private Long version;
    private String resourceType;
    private Long resourceId;
    private String resourceCode;
    private String resourceName;
    private String attachmentCategory;
    private String attachmentLabel;
    private String originalName;
    private String contentType;
    private Long fileSize;
    private String fileExtension;
    private Boolean previewable;
    private String uploadNote;
    private String uploadedBy;
    private LocalDateTime uploadedAt;
    private Boolean deleted;
    private String deletedBy;
    private LocalDateTime deletedAt;
    private String deleteReason;
    private String downloadUrl;
    private String previewUrl;

    public static ResourceAttachmentVO fromEntity(ResourceAttachment entity) {
        ResourceAttachmentVO vo = new ResourceAttachmentVO();
        vo.setId(entity.getId());
        vo.setVersion(entity.getVersion());
        vo.setResourceType(entity.getResourceType());
        vo.setResourceId(entity.getResourceId());
        vo.setResourceCode(entity.getResourceCode());
        vo.setResourceName(entity.getResourceName());
        vo.setAttachmentCategory(entity.getAttachmentCategory());
        vo.setAttachmentLabel(entity.getAttachmentLabel());
        vo.setOriginalName(entity.getOriginalName());
        vo.setContentType(entity.getContentType());
        vo.setFileSize(entity.getFileSize());
        vo.setFileExtension(entity.getFileExtension());
        vo.setPreviewable(Boolean.TRUE.equals(entity.getPreviewable()));
        vo.setUploadNote(entity.getUploadNote());
        vo.setUploadedBy(entity.getUploadedBy());
        vo.setUploadedAt(entity.getUploadedAt());
        vo.setDeleted(Boolean.TRUE.equals(entity.getDeleted()));
        vo.setDeletedBy(entity.getDeletedBy());
        vo.setDeletedAt(entity.getDeletedAt());
        vo.setDeleteReason(entity.getDeleteReason());
        vo.setDownloadUrl("/api/attachments/" + entity.getId() + "/download");
        vo.setPreviewUrl("/api/attachments/" + entity.getId() + "/preview");
        return vo;
    }
}
