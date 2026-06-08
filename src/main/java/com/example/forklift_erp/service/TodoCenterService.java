package com.example.forklift_erp.service;

import com.example.forklift_erp.constant.RentalStatuses;
import com.example.forklift_erp.dto.TodoCenterVO;
import com.example.forklift_erp.entity.OutboundOrder;
import com.example.forklift_erp.entity.RentalRecord;
import com.example.forklift_erp.entity.RepairRecord;
import com.example.forklift_erp.repository.MachineInventoryRepository;
import com.example.forklift_erp.repository.OutboundOrderRepository;
import com.example.forklift_erp.repository.PartInventoryRepository;
import com.example.forklift_erp.repository.RentalRecordRepository;
import com.example.forklift_erp.repository.RepairRecordRepository;
import com.example.forklift_erp.service.impl.OutboundUploadReadinessPolicy;
import com.example.forklift_erp.util.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TodoCenterService {
    private static final int ITEM_LIMIT = 30;

    @Autowired
    private OutboundOrderRepository outboundOrderRepository;

    @Autowired
    private PartInventoryRepository partRepository;

    @Autowired
    private RepairRecordRepository repairRepository;

    @Autowired
    private RentalRecordRepository rentalRepository;

    @Autowired
    private MachineInventoryRepository machineRepository;

    @Autowired
    private OutboundUploadReadinessPolicy uploadReadinessPolicy;

    @Transactional(readOnly = true)
    public TodoCenterVO dashboard() {
        TodoCenterVO dashboard = new TodoCenterVO();
        dashboard.setGeneratedAt(LocalDateTime.now());
        boolean includeLocked = SecurityUtils.isAdminOrSuperAdmin();

        applyOrderSummary(dashboard, outboundOrderRepository.summarizeTodos(includeLocked));
        loadOrderTodoCandidates(includeLocked).forEach(order -> addOrderTodoItems(dashboard, order));

        dashboard.setPendingRepairCount(toSafeInt(repairRepository.countPendingTodos(includeLocked)));
        repairRepository.findPendingTodos(includeLocked, todoPage()).forEach(repair ->
                dashboard.getItems().add(item(
                        "REPAIR_PENDING",
                        "\u7ef4\u4fee\u8ddf\u8fdb",
                        safe(repair.getCustomerName(), repair.getVehicleNumber(), "\u7ef4\u4fee\u8bb0\u5f55 " + repair.getId()),
                        safe(repair.getFaultDescription(), repair.getStatus(), "\u5f85\u5904\u7406"),
                        "primary",
                        "REPAIR",
                        repair.getId(),
                        amount(repair.getTotalFee()),
                        null,
                        repair.getUpdatedAt()
                )));

        dashboard.setActiveRentalCount(toSafeInt(rentalRepository.countByStatus(RentalStatuses.ACTIVE)));
        rentalRepository.findByStatusOrderByUpdatedAtDescIdDesc(RentalStatuses.ACTIVE, todoPage()).forEach(rental ->
                dashboard.getItems().add(item(
                        "RENTAL_ACTIVE",
                        "\u79df\u8d41\u8ddf\u8fdb",
                        safe(rental.getVehicleNumber(), rental.getRentalNo(), "\u79df\u8d41\u8bb0\u5f55 " + rental.getId()),
                        safe(rental.getCustomerName(), rental.getDestination(), "\u79df\u8d41\u4e2d"),
                        "primary",
                        "RENTAL",
                        rental.getId(),
                        amount(rental.getMonthlyRentalPrice()),
                        rental.getEndDate(),
                        rental.getUpdatedAt()
                )));

        dashboard.setLowStockCount(toSafeInt(partRepository.countLowStockTodos(0, includeLocked)));
        partRepository.findLowStockTodos(0, includeLocked, todoPage()).forEach(part ->
                dashboard.getItems().add(item(
                        "PART_LOW_STOCK",
                        "\u5e93\u5b58\u9884\u8b66",
                        safe(part.getPartCode(), "\u914d\u4ef6 " + part.getId()),
                        safe(part.getPartName(), "\u5e93\u5b58\u4e3a 0"),
                        "danger",
                        "PART",
                        part.getId(),
                        null,
                        null,
                        part.getUpdatedAt()
                )));

        dashboard.setInStockVehicleCount(toSafeInt(machineRepository.countInStockVehicleTodos(includeLocked)));

        dashboard.getItems().sort(
                Comparator.comparing((TodoCenterVO.TodoItem item) -> priorityRank(item.getPriority()))
                        .thenComparing(TodoCenterVO.TodoItem::getSortTime, Comparator.nullsLast(Comparator.reverseOrder()))
        );
        if (dashboard.getItems().size() > ITEM_LIMIT) {
            dashboard.setItems(dashboard.getItems().subList(0, ITEM_LIMIT));
        }
        dashboard.setTotalTodoCount(
                dashboard.getPendingPaymentCount()
                        + dashboard.getPendingSalesReportCount()
                        + dashboard.getPendingInvoiceApplicationCount()
                        + dashboard.getPendingInvoiceFileCount()
                        + dashboard.getPendingContractFileCount()
                        + dashboard.getPendingRepairCount()
                        + dashboard.getActiveRentalCount()
                        + dashboard.getLowStockCount()
        );
        return dashboard;
    }

    private void applyOrderSummary(TodoCenterVO dashboard, OutboundOrderRepository.OutboundTodoSummaryProjection summary) {
        if (summary == null) {
            return;
        }
        dashboard.setReceivableAmount(amount(summary.getReceivableAmount()));
        dashboard.setReceivedAmount(amount(summary.getReceivedAmount()));
        dashboard.setOutstandingAmount(amount(summary.getOutstandingAmount()));
        dashboard.setOverdueAmount(amount(summary.getOverdueAmount()));
        dashboard.setPendingPaymentCount(toSafeInt(summary.getPendingPaymentCount()));
        dashboard.setOverduePaymentCount(toSafeInt(summary.getOverduePaymentCount()));
        dashboard.setPendingSalesReportCount(toSafeInt(summary.getPendingSalesReportCount()));
        dashboard.setPendingInvoiceApplicationCount(toSafeInt(summary.getPendingInvoiceApplicationCount()));
        dashboard.setPendingInvoiceFileCount(toSafeInt(summary.getPendingInvoiceFileCount()));
        dashboard.setPendingContractFileCount(toSafeInt(summary.getPendingContractFileCount()));
    }

    private List<OutboundOrder> loadOrderTodoCandidates(boolean includeLocked) {
        Map<Long, OutboundOrder> orders = new LinkedHashMap<>();
        addOrderCandidates(orders, outboundOrderRepository.findOverduePaymentTodos(includeLocked, todoPage()));
        addOrderCandidates(orders, outboundOrderRepository.findPendingPaymentTodos(includeLocked, todoPage()));
        addOrderCandidates(orders, outboundOrderRepository.findSalesReportTodos(includeLocked, todoPage()));
        addOrderCandidates(orders, outboundOrderRepository.findInvoiceApplicationTodos(includeLocked, todoPage()));
        addOrderCandidates(orders, outboundOrderRepository.findInvoiceFileTodos(includeLocked, todoPage()));
        addOrderCandidates(orders, outboundOrderRepository.findContractFileTodos(includeLocked, todoPage()));
        return orders.values().stream().toList();
    }

    private void addOrderCandidates(Map<Long, OutboundOrder> target, List<OutboundOrder> rows) {
        for (OutboundOrder order : rows) {
            if (order.getId() != null) {
                target.putIfAbsent(order.getId(), order);
            }
        }
    }

    private PageRequest todoPage() {
        return PageRequest.of(0, ITEM_LIMIT);
    }

    private void addOrderTodoItems(TodoCenterVO dashboard, OutboundOrder order) {
        BigDecimal receivable = amount(firstAmount(order.getReceivableAmount(), order.getSettlementPrice()));
        BigDecimal received = amount(order.getReceivedAmount());
        BigDecimal outstanding = receivable.subtract(received).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        int overdueDays = overdueDays(outstanding, order.getPaymentDueDate());

        String title = safe(order.getResourceCode(), order.getOrderNo(), "\u8ba2\u5355 " + order.getId());
        String detail = safe(order.getCustomerName(), order.getPaymentRemark(), order.getOrderNo());
        if (outstanding.signum() > 0) {
            TodoCenterVO.TodoItem item = item(
                    "ORDER_PAYMENT",
                    overdueDays > 0 ? "\u903e\u671f\u6536\u6b3e" : "\u5f85\u6536\u6b3e",
                    title,
                    detail,
                    overdueDays > 0 ? "danger" : "warn",
                    "OUTBOUND_ORDER",
                    order.getId(),
                    outstanding,
                    order.getPaymentDueDate(),
                    order.getUpdatedAt()
            );
            item.setOverdueDays(overdueDays);
            dashboard.getItems().add(item);
        }
        if (Boolean.TRUE.equals(order.getPaymentSettled()) && !Boolean.TRUE.equals(order.getSalesReported())) {
            dashboard.getItems().add(item("ORDER_SALES_REPORT", "\u5f85\u62a5\u9500\u552e", title, detail, "primary",
                    "OUTBOUND_ORDER", order.getId(), null, null, order.getUpdatedAt()));
        }
        if (Boolean.TRUE.equals(order.getPaymentSettled())
                && Boolean.TRUE.equals(order.getSalesReported())
                && !Boolean.TRUE.equals(order.getInvoiceApplied())) {
            dashboard.getItems().add(item("ORDER_INVOICE_APPLICATION", "\u5f85\u7533\u8bf7\u53d1\u7968", title, detail, "warn",
                    "OUTBOUND_ORDER", order.getId(), null, null, order.getUpdatedAt()));
        }
        if (uploadReadinessPolicy.isInvoiceUploadReady(order) && isBlank(order.getInvoiceStoredFileName())) {
            dashboard.getItems().add(item("ORDER_INVOICE_FILE", "\u5f85\u4e0a\u4f20\u53d1\u7968", title,
                    safe(order.getInvoiceStatus(), detail), "primary", "OUTBOUND_ORDER", order.getId(),
                    null, null, order.getUpdatedAt()));
        }
        if (uploadReadinessPolicy.isContractUploadReady(order) && isBlank(order.getContractStoredFileName())) {
            dashboard.getItems().add(item("ORDER_CONTRACT_FILE", "\u5f85\u4e0a\u4f20\u5408\u540c", title,
                    safe(order.getContractType(), detail), "teal", "OUTBOUND_ORDER", order.getId(),
                    null, null, order.getUpdatedAt()));
        }
    }

    private TodoCenterVO.TodoItem item(
            String type,
            String label,
            String title,
            String detail,
            String priority,
            String resourceType,
            Long resourceId,
            BigDecimal amount,
            LocalDate dueDate,
            LocalDateTime sortTime
    ) {
        TodoCenterVO.TodoItem item = new TodoCenterVO.TodoItem();
        item.setType(type);
        item.setLabel(label);
        item.setTitle(title);
        item.setDetail(detail);
        item.setPriority(priority);
        item.setResourceType(resourceType);
        item.setResourceId(resourceId);
        item.setAmount(amount);
        item.setDueDate(dueDate);
        item.setSortTime(sortTime);
        item.setOverdueDays(overdueDays(amount, dueDate));
        return item;
    }

    private BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal firstAmount(BigDecimal... values) {
        for (BigDecimal value : values) {
            if (value != null) {
                return value;
            }
        }
        return BigDecimal.ZERO;
    }

    private int overdueDays(BigDecimal outstanding, LocalDate dueDate) {
        if (dueDate == null || outstanding == null || outstanding.signum() <= 0) {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(dueDate, LocalDate.now());
        return days > 0 ? Math.toIntExact(days) : 0;
    }

    private int priorityRank(String priority) {
        if ("danger".equals(priority)) return 0;
        if ("warn".equals(priority)) return 1;
        return 2;
    }

    private String safe(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "-";
    }

    private int toSafeInt(Long value) {
        return value == null ? 0 : toSafeInt(value.longValue());
    }

    private int toSafeInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.toIntExact(value);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
