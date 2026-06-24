package com.example.forklift_erp.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private Integer rentalDueCount = 0;
    private Integer lowStockCount = 0;
    private Integer inStockVehicleCount = 0;
    private Integer longIdleVehicleCount = 0;
    private Integer criticalTodoCount = 0;
    private Integer lowStockThreshold = 5;
    private Integer rentalDueSoonDays = 7;
    private Integer longIdleDays = 90;
    private BigDecimal receivableAmount = BigDecimal.ZERO;
    private BigDecimal receivedAmount = BigDecimal.ZERO;
    private BigDecimal outstandingAmount = BigDecimal.ZERO;
    private BigDecimal overdueAmount = BigDecimal.ZERO;
    private List<TodoQueue> queues = new ArrayList<>();
    private List<TodoItem> items = new ArrayList<>();

    @Data
    public static class TodoQueue {
        private String key;
        private String label;
        private String description;
        private String priority;
        private Integer count = 0;
        private BigDecimal amount = BigDecimal.ZERO;
        private String targetTab;
        private Map<String, String> targetFilter = new LinkedHashMap<>();
        private List<TodoItem> items = new ArrayList<>();
    }

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
        private Integer ageDays = 0;
        private LocalDateTime sortTime;
        private String targetTab;
        private Map<String, String> targetFilter = new LinkedHashMap<>();
    }
}
