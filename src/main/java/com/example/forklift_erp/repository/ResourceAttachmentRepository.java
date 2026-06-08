package com.example.forklift_erp.repository;

import com.example.forklift_erp.entity.ResourceAttachment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ResourceAttachmentRepository extends JpaRepository<ResourceAttachment, Long> {

    List<ResourceAttachment> findByResourceTypeAndResourceIdAndDeletedFalseOrderByUploadedAtDesc(String resourceType, Long resourceId);

    List<ResourceAttachment> findByResourceTypeAndResourceIdAndAttachmentCategoryAndDeletedFalseOrderByUploadedAtDesc(
            String resourceType,
            Long resourceId,
            String attachmentCategory
    );

    Optional<ResourceAttachment> findFirstByResourceTypeAndResourceIdAndAttachmentCategoryAndDeletedFalseOrderByUploadedAtDesc(
            String resourceType,
            Long resourceId,
            String attachmentCategory
    );

    Optional<ResourceAttachment> findByIdAndDeletedFalse(Long id);

    @Query("""
            select a from ResourceAttachment a
            where (:resourceType is null or :resourceType = '' or a.resourceType = :resourceType)
              and (:resourceId is null or a.resourceId = :resourceId)
              and (:category is null or :category = '' or a.attachmentCategory = :category)
              and (:includeDeleted = true or a.deleted = false)
              and (
                :keyword is null or :keyword = '' or
                lower(coalesce(a.originalName, '')) like lower(concat('%', :keyword, '%')) or
                lower(coalesce(a.attachmentLabel, '')) like lower(concat('%', :keyword, '%')) or
                lower(coalesce(a.resourceCode, '')) like lower(concat('%', :keyword, '%')) or
                lower(coalesce(a.resourceName, '')) like lower(concat('%', :keyword, '%'))
              )
            order by a.uploadedAt desc, a.id desc
            """)
    Page<ResourceAttachment> search(
            @Param("resourceType") String resourceType,
            @Param("resourceId") Long resourceId,
            @Param("category") String category,
            @Param("keyword") String keyword,
            @Param("includeDeleted") boolean includeDeleted,
            Pageable pageable
    );
}
