package com.example.forklift_erp.dto;

import com.example.forklift_erp.entity.OutboundOrder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class OutboundOrderVO {
    private Long id;
    private Long version;
    private String orderNo;
    private String resourceType;
    private Long resourceId;
    private String resourceCode;
    private String resourceName;
    private String specificationModel;
    private Integer quantity;
    private String unit;
    private Long customerId;
    private String customerName;
    private String customerAddress;
    private String contactName;
    private String contactPhone;
    private String taxOrIdNumber;
    private BigDecimal settlementPrice;
    private Boolean paymentSettled;
    private Boolean salesReported;
    private Boolean invoiceApplied;
    private LocalDate salesReportDate;
    private LocalDate invoiceApplicationDate;
    private String orderRemark;
    private String operator;
    private Long stockOperationLogId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static OutboundOrderVO fromEntity(OutboundOrder entity) {
        OutboundOrderVO vo = new OutboundOrderVO();
        vo.setId(entity.getId());
        vo.setVersion(entity.getVersion());
        vo.setOrderNo(entity.getOrderNo());
        vo.setResourceType(entity.getResourceType());
        vo.setResourceId(entity.getResourceId());
        vo.setResourceCode(entity.getResourceCode());
        vo.setResourceName(entity.getResourceName());
        vo.setSpecificationModel(entity.getSpecificationModel());
        vo.setQuantity(entity.getQuantity());
        vo.setUnit(entity.getUnit());
        vo.setCustomerId(entity.getCustomerId());
        vo.setCustomerName(entity.getCustomerName());
        vo.setCustomerAddress(entity.getCustomerAddress());
        vo.setContactName(entity.getContactName());
        vo.setContactPhone(entity.getContactPhone());
        vo.setTaxOrIdNumber(entity.getTaxOrIdNumber());
        vo.setSettlementPrice(entity.getSettlementPrice());
        vo.setPaymentSettled(Boolean.TRUE.equals(entity.getPaymentSettled()));
        vo.setSalesReported(Boolean.TRUE.equals(entity.getSalesReported()));
        vo.setInvoiceApplied(Boolean.TRUE.equals(entity.getInvoiceApplied()));
        vo.setSalesReportDate(entity.getSalesReportDate());
        vo.setInvoiceApplicationDate(entity.getInvoiceApplicationDate());
        vo.setOrderRemark(entity.getOrderRemark());
        vo.setOperator(entity.getOperator());
        vo.setStockOperationLogId(entity.getStockOperationLogId());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
