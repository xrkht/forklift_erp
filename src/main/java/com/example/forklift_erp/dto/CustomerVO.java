package com.example.forklift_erp.dto;

import com.example.forklift_erp.entity.Customer;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CustomerVO {
    private Long id;
    private Long version;
    private String companyName;
    private String address;
    private String contactName;
    private String contactPhone;
    private String taxOrIdNumber;
    private String remarks;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CustomerVO fromEntity(Customer entity) {
        CustomerVO vo = new CustomerVO();
        vo.setId(entity.getId());
        vo.setVersion(entity.getVersion());
        vo.setCompanyName(entity.getCompanyName());
        vo.setAddress(entity.getAddress());
        vo.setContactName(entity.getContactName());
        vo.setContactPhone(entity.getContactPhone());
        vo.setTaxOrIdNumber(entity.getTaxOrIdNumber());
        vo.setRemarks(entity.getRemarks());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
