package com.example.forklift_erp.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class TodoCenterVO {
    private LocalDateTime generatedAt;
    private Integer totalTodoCount = 0;
    private Integer pendingPaymentCount = 0;
    private Integer overduePaymentCount = 0;
    private Integer pendingSalesReportCount = 0;
    private Integer pendingInvoiceApplicationCount = 0;
    private Integer pendingInvoiceFileCount = 0;
    private Integer pendingContractFileCount = 0;
    private Integer pendingRepairCount = 0;
    private Integer activeRentalCount = 0;
    private Integer lowStockCount = 0;
    private Integer inStockVehicleCount = 0;
    private BigDecimal receivableAmount = BigDecimal.ZERO;
    private BigDecimal receivedAmount = BigDecimal.ZERO;
    private BigDecimal outstandingAmount = BigDecimal.ZERO;
    private BigDecimal overdueAmount = BigDecimal.ZERO;
    private List<TodoItem> items = new ArrayList<>();

    @Data
    public static class TodoItem {
        private String type;
        private String label;
        private String title;
        private String detail;
        private String priority;
        private String resourceType;
        private Long resourceId;
        private BigDecimal amount;
        private LocalDate dueDate;
        private Integer overdueDays = 0;
        private LocalDateTime sortTime;
    }
}
