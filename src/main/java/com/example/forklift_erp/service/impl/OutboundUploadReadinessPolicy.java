package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.entity.OutboundOrder;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class OutboundUploadReadinessPolicy {

    public boolean isInvoiceUploadReady(OutboundOrder order) {
        if (Boolean.TRUE.equals(order.getInvoiceApplied())) {
            return true;
        }
        if (order.getInvoiceIssuedDate() != null) {
            return true;
        }
        String status = blankToNull(order.getInvoiceStatus());
        if (status == null) {
            return false;
        }
        String lowerStatus = status.toLowerCase(Locale.ROOT);
        return status.contains("issued")
                || status.contains("\u5f00\u7968\u5b8c\u6210")
                || status.contains("\u5b8c\u6210\u5f00\u7968")
                || status.contains("\u5df2\u51fa\u7968")
                || lowerStatus.contains("issued")
                || lowerStatus.contains("invoiced");
    }

    public boolean isContractUploadReady(OutboundOrder order) {
        String contractType = blankToNull(order.getContractType());
        if (contractType == null) {
            return false;
        }
        String lower = contractType.toLowerCase(Locale.ROOT);
        if (lower.startsWith("no") || lower.startsWith("none") || lower.startsWith("false")) {
            return false;
        }
        if (contractType.startsWith("\u5426")
                || contractType.startsWith("\u65e0")
                || contractType.startsWith("\u6ca1")
                || contractType.startsWith("\u4e0d")
                || contractType.contains("\u65e0\u5408\u540c")) {
            return false;
        }
        return true;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
