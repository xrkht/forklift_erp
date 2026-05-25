package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.dto.OutboundOrderUpdateDTO;
import com.example.forklift_erp.dto.OutboundOrderVO;
import com.example.forklift_erp.dto.PartOutboundOrderCreateDTO;
import com.example.forklift_erp.dto.VehicleOutboundOrderCreateDTO;
import com.example.forklift_erp.entity.Customer;
import com.example.forklift_erp.entity.MachineInventory;
import com.example.forklift_erp.entity.OutboundOrder;
import com.example.forklift_erp.entity.PartInventory;
import com.example.forklift_erp.entity.StockOperationLog;
import com.example.forklift_erp.exception.BusinessException;
import com.example.forklift_erp.repository.CustomerRepository;
import com.example.forklift_erp.repository.MachineInventoryRepository;
import com.example.forklift_erp.repository.OutboundOrderRepository;
import com.example.forklift_erp.repository.PartInventoryRepository;
import com.example.forklift_erp.repository.StockOperationLogRepository;
import com.example.forklift_erp.service.CollaborationService;
import com.example.forklift_erp.service.OperationAuditService;
import com.example.forklift_erp.service.OutboundOrderService;
import com.example.forklift_erp.service.StockLedgerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class OutboundOrderServiceImpl implements OutboundOrderService {

    private static final String SOURCE_TYPE = "OUTBOUND_ORDER";
    private static final DateTimeFormatter ORDER_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    @Autowired
    private OutboundOrderRepository outboundOrderRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private MachineInventoryRepository machineRepository;

    @Autowired
    private PartInventoryRepository partRepository;

    @Autowired
    private StockOperationLogRepository stockOperationLogRepository;

    @Autowired
    private StockLedgerService stockLedgerService;

    @Autowired
    private OperationAuditService operationAuditService;

    @Autowired
    private CollaborationService collaborationService;

    @Override
    @Transactional(readOnly = true)
    public List<OutboundOrderVO> findAll() {
        return outboundOrderRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(OutboundOrderVO::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OutboundOrderVO findById(Long id) {
        return outboundOrderRepository.findById(id)
                .map(OutboundOrderVO::fromEntity)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "出库订单不存在"));
    }

    @Override
    @Transactional
    public OutboundOrderVO createVehicleOutbound(VehicleOutboundOrderCreateDTO request) {
        MachineInventory machine = machineRepository.findByIdForUpdate(request.getMachineId())
                .orElseThrow(() -> new BusinessException(ResultCode.VEHICLE_NOT_FOUND, "出库车辆不存在"));
        if (Boolean.TRUE.equals(machine.getModelOnly())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "车型模板不能直接出库，请选择具体库存车号");
        }
        collaborationService.validateWrite(machine, request.getMachineVersion());
        int before = machine.getInventoryCount() == null ? 0 : machine.getInventoryCount();
        if (before < 1) {
            throw new BusinessException(ResultCode.INSUFFICIENT_STOCK, "整车不在库，不能出库");
        }

        Customer customer = findCustomer(request.getCustomerId());
        OutboundOrder order = new OutboundOrder();
        order.setOrderNo(nextOrderNo());
        order.setResourceType(OutboundOrder.RESOURCE_MACHINE);
        order.setResourceId(machine.getId());
        order.setResourceCode(machine.getVehicleProductNumber());
        order.setResourceName(machine.getName());
        order.setSpecificationModel(machine.getSpecificationModel());
        order.setQuantity(1);
        order.setUnit("台");
        copyCustomer(order, customer);
        order.setSettlementPrice(request.getSettlementPrice());
        order.setSalesDate(request.getSalesDate());
        order.setSalePrice(firstPrice(request.getSalePrice(), machine.getSalePrice()));
        if (request.getPaymentSettled() != null) {
            order.setPaymentSettled(request.getPaymentSettled());
        }
        order.setPaymentRemark(blankToNull(request.getPaymentRemark()));
        if (request.getSalesReported() != null) {
            order.setSalesReported(request.getSalesReported());
        }
        if (request.getInvoiceApplied() != null) {
            order.setInvoiceApplied(request.getInvoiceApplied());
        }
        order.setSalesReportDate(request.getSalesReportDate());
        order.setInvoiceApplicationDate(request.getInvoiceApplicationDate());
        order.setInvoiceStatus(blankToNull(request.getInvoiceStatus()));
        order.setInvoiceIssuedDate(request.getInvoiceIssuedDate());
        order.setRegistrationStatus(blankToNull(request.getRegistrationStatus()));
        order.setContractType(blankToNull(request.getContractType()));
        order.setOperator(blankToNull(request.getOperator()));
        order.setOrderRemark(blankToNull(request.getOrderRemark()));
        collaborationService.stampWrite(order);
        OutboundOrder savedOrder = outboundOrderRepository.saveAndFlush(order);

        int after = before - 1;
        machine.setInventoryCount(after);
        machine.setStockStatus(after > 0 ? "IN_STOCK" : "OUTBOUND");
        machine.setSettlementPrice(request.getSettlementPrice());
        machine.setSalePrice(firstPrice(request.getSalePrice(), machine.getSalePrice()));
        machine.setSalesDate(toMachineSalesDate(request.getSalesDate()));
        machine.setDestination1(customer.getCompanyName());
        machine.setIsSalesReported(yesNo(order.getSalesReported()));
        machine.setSalesReportDate(order.getSalesReportDate());
        machine.setIsInvoiceApplied(yesNo(order.getInvoiceApplied()));
        collaborationService.stampWrite(machine);
        MachineInventory savedMachine = machineRepository.saveAndFlush(machine);

        StockOperationLog stockLog = saveStockLog(
                OutboundOrder.RESOURCE_MACHINE,
                savedMachine.getId(),
                savedMachine.getVehicleProductNumber(),
                savedMachine.getName(),
                savedMachine.getWarehouseId(),
                1,
                before,
                after,
                request.getOperator(),
                joinRemark("整车出库订单 " + savedOrder.getOrderNo(), request.getOrderRemark()),
                savedOrder.getId()
        );
        savedOrder.setStockOperationLogId(stockLog.getId());
        OutboundOrder result = outboundOrderRepository.saveAndFlush(savedOrder);

        operationAuditService.record("订单列表", "CREATE", "OUTBOUND_ORDER", result.getId(),
                result.getOrderNo(), result.getCustomerName(),
                "新增整车出库订单 " + result.getResourceCode(), result.getOperator(), result.getOrderRemark(),
                SOURCE_TYPE, result.getId());
        return OutboundOrderVO.fromEntity(result);
    }

    @Override
    @Transactional
    public OutboundOrderVO createPartOutbound(PartOutboundOrderCreateDTO request) {
        PartInventory part = partRepository.findByPartCodeForUpdate(request.getPartCode())
                .orElseThrow(() -> new BusinessException(ResultCode.PART_NOT_FOUND, "出库配件不存在"));
        collaborationService.validateWrite(part, request.getPartVersion());
        int quantity = request.getQuantity() == null ? 0 : request.getQuantity();
        if (quantity < 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "出库数量必须大于0");
        }
        int before = part.getQuantity() == null ? 0 : part.getQuantity();
        if (before < quantity) {
            throw new BusinessException(ResultCode.INSUFFICIENT_STOCK, "配件库存不足，当前库存：" + before);
        }

        Customer customer = findCustomer(request.getCustomerId());
        OutboundOrder order = new OutboundOrder();
        order.setOrderNo(nextOrderNo());
        order.setResourceType(OutboundOrder.RESOURCE_PART);
        order.setResourceId(part.getId());
        order.setResourceCode(part.getPartCode());
        order.setResourceName(part.getPartName());
        order.setSpecificationModel(part.getSpecification());
        order.setQuantity(quantity);
        order.setUnit(part.getUnit());
        copyCustomer(order, customer);
        order.setSettlementPrice(firstPrice(request.getSettlementPrice(), part.getSettlementPrice(), part.getSalePrice()));
        order.setOperator(blankToNull(request.getOperator()));
        order.setOrderRemark(blankToNull(request.getOrderRemark()));
        collaborationService.stampWrite(order);
        OutboundOrder savedOrder = outboundOrderRepository.saveAndFlush(order);

        int after = before - quantity;
        part.setQuantity(after);
        part.setIsSalesReported("否");
        collaborationService.stampWrite(part);
        PartInventory savedPart = partRepository.saveAndFlush(part);

        StockOperationLog stockLog = saveStockLog(
                OutboundOrder.RESOURCE_PART,
                savedPart.getId(),
                savedPart.getPartCode(),
                savedPart.getPartName(),
                savedPart.getWarehouseId(),
                quantity,
                before,
                after,
                request.getOperator(),
                joinRemark("配件出库订单 " + savedOrder.getOrderNo(), request.getOrderRemark()),
                savedOrder.getId()
        );
        savedOrder.setStockOperationLogId(stockLog.getId());
        OutboundOrder result = outboundOrderRepository.saveAndFlush(savedOrder);

        operationAuditService.record("订单列表", "CREATE", "OUTBOUND_ORDER", result.getId(),
                result.getOrderNo(), result.getCustomerName(),
                "新增配件出库订单 " + result.getResourceCode() + " x" + quantity,
                result.getOperator(), result.getOrderRemark(), SOURCE_TYPE, result.getId());
        return OutboundOrderVO.fromEntity(result);
    }

    @Override
    @Transactional
    public OutboundOrderVO update(Long id, OutboundOrderUpdateDTO request) {
        OutboundOrder order = outboundOrderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "出库订单不存在"));
        collaborationService.validateWrite(order, request.getVersion());
        if (request.getSettlementPrice() != null) {
            order.setSettlementPrice(request.getSettlementPrice());
        }
        order.setSalesDate(request.getSalesDate());
        if (request.getSalePrice() != null) {
            order.setSalePrice(request.getSalePrice());
        }
        if (request.getPaymentSettled() != null) {
            order.setPaymentSettled(request.getPaymentSettled());
        }
        order.setPaymentRemark(blankToNull(request.getPaymentRemark()));
        if (request.getSalesReported() != null) {
            order.setSalesReported(request.getSalesReported());
        }
        if (request.getInvoiceApplied() != null) {
            order.setInvoiceApplied(request.getInvoiceApplied());
        }
        order.setSalesReportDate(request.getSalesReportDate());
        order.setInvoiceApplicationDate(request.getInvoiceApplicationDate());
        order.setInvoiceStatus(blankToNull(request.getInvoiceStatus()));
        order.setInvoiceIssuedDate(request.getInvoiceIssuedDate());
        order.setRegistrationStatus(blankToNull(request.getRegistrationStatus()));
        order.setContractType(blankToNull(request.getContractType()));
        order.setOrderRemark(blankToNull(request.getOrderRemark()));
        if (request.getOperator() != null && !request.getOperator().isBlank()) {
            order.setOperator(request.getOperator().trim());
        }
        collaborationService.stampWrite(order);
        OutboundOrder saved = outboundOrderRepository.saveAndFlush(order);
        syncLegacyOutboundFlags(saved);
        operationAuditService.record("订单列表", "UPDATE", "OUTBOUND_ORDER", saved.getId(),
                saved.getOrderNo(), saved.getCustomerName(), "更新出库订单状态",
                saved.getOperator(), saved.getOrderRemark(), SOURCE_TYPE, saved.getId());
        return OutboundOrderVO.fromEntity(saved);
    }

    private Customer findCustomer(Long customerId) {
        return customerRepository.findByIdForUpdate(customerId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "客户不存在"));
    }

    private void copyCustomer(OutboundOrder order, Customer customer) {
        order.setCustomerId(customer.getId());
        order.setCustomerName(customer.getCompanyName());
        order.setCustomerAddress(customer.getAddress());
        order.setContactName(customer.getContactName());
        order.setContactPhone(customer.getContactPhone());
        order.setTaxOrIdNumber(customer.getTaxOrIdNumber());
    }

    private void syncLegacyOutboundFlags(OutboundOrder order) {
        if (OutboundOrder.RESOURCE_MACHINE.equals(order.getResourceType())) {
            machineRepository.findByIdForUpdate(order.getResourceId()).ifPresent(machine -> {
                machine.setSettlementPrice(firstPrice(order.getSettlementPrice(), machine.getSettlementPrice()));
                machine.setSalePrice(firstPrice(order.getSalePrice(), machine.getSalePrice()));
                machine.setSalesDate(toMachineSalesDate(order.getSalesDate()));
                machine.setIsSalesReported(yesNo(order.getSalesReported()));
                machine.setSalesReportDate(order.getSalesReportDate());
                machine.setIsInvoiceApplied(yesNo(order.getInvoiceApplied()));
                collaborationService.stampWrite(machine);
                machineRepository.saveAndFlush(machine);
            });
            return;
        }
        if (OutboundOrder.RESOURCE_PART.equals(order.getResourceType())) {
            partRepository.findByIdForUpdate(order.getResourceId()).ifPresent(part -> {
                part.setIsSalesReported(yesNo(order.getSalesReported()));
                part.setSalesReportDate(order.getSalesReportDate());
                collaborationService.stampWrite(part);
                partRepository.saveAndFlush(part);
            });
        }
    }

    private StockOperationLog saveStockLog(
            String resourceType,
            Long resourceId,
            String resourceCode,
            String resourceName,
            Long warehouseId,
            Integer quantity,
            Integer beforeQuantity,
            Integer afterQuantity,
            String operator,
            String remark,
            Long orderId
    ) {
        StockOperationLog stockLog = new StockOperationLog();
        stockLog.setResourceType(resourceType);
        stockLog.setOperationType("OUTBOUND");
        stockLog.setResourceId(resourceId);
        stockLog.setResourceCode(resourceCode);
        stockLog.setResourceName(resourceName);
        stockLog.setQuantity(quantity);
        stockLog.setBeforeQuantity(beforeQuantity);
        stockLog.setAfterQuantity(afterQuantity);
        stockLog.setOperator(operator);
        stockLog.setRemark(remark);
        StockOperationLog savedLog = stockOperationLogRepository.save(stockLog);
        stockLedgerService.recordMovement(
                "OUTBOUND",
                resourceType,
                resourceId,
                resourceCode,
                resourceName,
                warehouseId,
                beforeQuantity,
                afterQuantity,
                operator,
                remark,
                SOURCE_TYPE,
                orderId
        );
        operationAuditService.record(resourceType.equals(OutboundOrder.RESOURCE_MACHINE) ? "整车出入库" : "配件出入库",
                "OUTBOUND", resourceType, resourceId, resourceCode, resourceName,
                "出库 " + quantity, operator, remark, "STOCK", savedLog.getId());
        return savedLog;
    }

    private String nextOrderNo() {
        String suffix = UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 6)
                .toUpperCase(Locale.ROOT);
        return "OO-" + ORDER_NO_TIME.format(LocalDateTime.now()) + "-" + suffix;
    }

    private BigDecimal firstPrice(BigDecimal... prices) {
        for (BigDecimal price : prices) {
            if (price != null) {
                return price;
            }
        }
        return null;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String joinRemark(String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append("; ");
            }
            builder.append(value.trim());
        }
        return builder.isEmpty() ? null : builder.toString();
    }

    private String toMachineSalesDate(LocalDate salesDate) {
        return salesDate == null ? null : salesDate.toString();
    }

    private String yesNo(Boolean value) {
        return Boolean.TRUE.equals(value) ? "是" : "否";
    }
}
