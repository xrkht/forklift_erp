package com.example.forklift_erp.service;

import com.example.forklift_erp.constant.RentalStatuses;
import com.example.forklift_erp.dto.TodoCenterVO;
import com.example.forklift_erp.entity.MachineInventory;
import com.example.forklift_erp.entity.OutboundOrder;
import com.example.forklift_erp.entity.PartInventory;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

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

        List<OutboundOrder> orders = SecurityUtils.isAdminOrSuperAdmin()
                ? outboundOrderRepository.findAllByOrderByCreatedAtDesc()
                : outboundOrderRepository.findAllByIsLockedFalseOrderByCreatedAtDesc();
        orders.forEach(order -> addOrderTodos(dashboard, order));

        List<RepairRecord> repairs = SecurityUtils.isAdminOrSuperAdmin()
                ? repairRepository.findAll()
                : repairRepository.findAllByIsLockedFalse();
        repairs.stream()
                .filter(repair -> !"COMPLETED".equals(repair.getStatus()))
                .forEach(repair -> {
                    dashboard.setPendingRepairCount(dashboard.getPendingRepairCount() + 1);
                    dashboard.getItems().add(item(
                            "REPAIR_PENDING",
                            "维修跟进",
                            safe(repair.getCustomerName(), repair.getVehicleNumber(), "维修记录 " + repair.getId()),
                            safe(repair.getFaultDescription(), repair.getStatus(), "待处理"),
                            "primary",
                            "REPAIR",
                            repair.getId(),
                            amount(repair.getTotalFee()),
                            null,
                            repair.getUpdatedAt()
                    ));
                });

        rentalRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(rental -> RentalStatuses.ACTIVE.equals(rental.getStatus()))
                .forEach(rental -> {
                    dashboard.setActiveRentalCount(dashboard.getActiveRentalCount() + 1);
                    dashboard.getItems().add(item(
                            "RENTAL_ACTIVE",
                            "租赁跟进",
                            safe(rental.getVehicleNumber(), rental.getRentalNo(), "租赁记录 " + rental.getId()),
                            safe(rental.getCustomerName(), rental.getDestination(), "租赁中"),
                            "primary",
                            "RENTAL",
                            rental.getId(),
                            amount(rental.getMonthlyRentalPrice()),
                            rental.getEndDate(),
                            rental.getUpdatedAt()
                    ));
                });

        List<PartInventory> parts = SecurityUtils.isAdminOrSuperAdmin()
                ? partRepository.findAll()
                : partRepository.findAllByIsLockedFalse();
        parts.stream()
                .filter(part -> quantity(part.getQuantity()) <= 0)
                .forEach(part -> {
                    dashboard.setLowStockCount(dashboard.getLowStockCount() + 1);
                    dashboard.getItems().add(item(
                            "PART_LOW_STOCK",
                            "库存预警",
                            safe(part.getPartCode(), "配件 " + part.getId()),
                            safe(part.getPartName(), "库存为 0"),
                            "danger",
                            "PART",
                            part.getId(),
                            null,
                            null,
                            part.getUpdatedAt()
                    ));
                });

        List<MachineInventory> machines = SecurityUtils.isAdminOrSuperAdmin()
                ? machineRepository.findAll()
                : machineRepository.findAllByIsLockedFalse();
        machines.stream()
                .filter(machine -> !Boolean.TRUE.equals(machine.getModelOnly()))
                .filter(machine -> quantity(machine.getInventoryCount()) > 0)
                .forEach(machine -> dashboard.setInStockVehicleCount(dashboard.getInStockVehicleCount() + 1));

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

    private void addOrderTodos(TodoCenterVO dashboard, OutboundOrder order) {
        BigDecimal receivable = amount(firstAmount(order.getReceivableAmount(), order.getSettlementPrice()));
        BigDecimal received = amount(order.getReceivedAmount());
        BigDecimal outstanding = receivable.subtract(received).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        int overdueDays = overdueDays(outstanding, order.getPaymentDueDate());

        dashboard.setReceivableAmount(dashboard.getReceivableAmount().add(receivable));
        dashboard.setReceivedAmount(dashboard.getReceivedAmount().add(received));
        dashboard.setOutstandingAmount(dashboard.getOutstandingAmount().add(outstanding));

        String title = safe(order.getResourceCode(), order.getOrderNo(), "订单 " + order.getId());
        String detail = safe(order.getCustomerName(), order.getPaymentRemark(), order.getOrderNo());
        if (outstanding.signum() > 0) {
            dashboard.setPendingPaymentCount(dashboard.getPendingPaymentCount() + 1);
            if (overdueDays > 0) {
                dashboard.setOverduePaymentCount(dashboard.getOverduePaymentCount() + 1);
                dashboard.setOverdueAmount(dashboard.getOverdueAmount().add(outstanding));
            }
            TodoCenterVO.TodoItem item = item(
                    "ORDER_PAYMENT",
                    overdueDays > 0 ? "逾期收款" : "待收款",
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
            dashboard.setPendingSalesReportCount(dashboard.getPendingSalesReportCount() + 1);
            dashboard.getItems().add(item("ORDER_SALES_REPORT", "待报销售", title, detail, "primary",
                    "OUTBOUND_ORDER", order.getId(), null, null, order.getUpdatedAt()));
        }
        if (Boolean.TRUE.equals(order.getPaymentSettled())
                && Boolean.TRUE.equals(order.getSalesReported())
                && !Boolean.TRUE.equals(order.getInvoiceApplied())) {
            dashboard.setPendingInvoiceApplicationCount(dashboard.getPendingInvoiceApplicationCount() + 1);
            dashboard.getItems().add(item("ORDER_INVOICE_APPLICATION", "待申请发票", title, detail, "warn",
                    "OUTBOUND_ORDER", order.getId(), null, null, order.getUpdatedAt()));
        }
        if (uploadReadinessPolicy.isInvoiceUploadReady(order) && isBlank(order.getInvoiceStoredFileName())) {
            dashboard.setPendingInvoiceFileCount(dashboard.getPendingInvoiceFileCount() + 1);
            dashboard.getItems().add(item("ORDER_INVOICE_FILE", "待上传发票", title,
                    safe(order.getInvoiceStatus(), detail), "primary", "OUTBOUND_ORDER", order.getId(),
                    null, null, order.getUpdatedAt()));
        }
        if (uploadReadinessPolicy.isContractUploadReady(order) && isBlank(order.getContractStoredFileName())) {
            dashboard.setPendingContractFileCount(dashboard.getPendingContractFileCount() + 1);
            dashboard.getItems().add(item("ORDER_CONTRACT_FILE", "待上传合同", title,
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

    private int quantity(Integer value) {
        return value == null ? 0 : value;
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
