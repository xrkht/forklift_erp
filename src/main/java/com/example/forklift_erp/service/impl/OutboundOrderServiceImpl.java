package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.common.PageResult;
import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.constant.MachineStockStatuses;
import com.example.forklift_erp.constant.RentalStatuses;
import com.example.forklift_erp.dto.OutboundInvoiceDownload;
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
import com.example.forklift_erp.repository.RentalRecordRepository;
import com.example.forklift_erp.repository.StockOperationLogRepository;
import com.example.forklift_erp.service.CollaborationService;
import com.example.forklift_erp.service.OperationAuditService;
import com.example.forklift_erp.service.OutboundOrderService;
import com.example.forklift_erp.service.StockLedgerService;
import com.example.forklift_erp.util.ListPageSupport;
import com.example.forklift_erp.util.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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
    private RentalRecordRepository rentalRecordRepository;

    @Autowired
    private StockOperationLogRepository stockOperationLogRepository;

    @Autowired
    private StockLedgerService stockLedgerService;

    @Autowired
    private OperationAuditService operationAuditService;

    @Autowired
    private CollaborationService collaborationService;

    @Autowired
    private OutboundOrderFileStorage fileStorage;

    @Autowired
    private OutboundUploadReadinessPolicy uploadReadinessPolicy;

    @Autowired
    private OutboundResourceLockService resourceLockService;

    @Override
    @Transactional(readOnly = true)
    public List<OutboundOrderVO> findAll() {
        List<OutboundOrder> orders = SecurityUtils.isAdminOrSuperAdmin()
                ? outboundOrderRepository.findAllByOrderByCreatedAtDesc()
                : outboundOrderRepository.findAllByIsLockedFalseOrderByCreatedAtDesc();
        return orders.stream()
                .map(OutboundOrderVO::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<OutboundOrderVO> findPage(String keyword, Integer page, Integer size) {
        int normalizedPage = ListPageSupport.page(page);
        int normalizedSize = ListPageSupport.size(size);
        Page<OutboundOrder> result = outboundOrderRepository.searchPage(
                normalizeKeyword(keyword),
                SecurityUtils.isAdminOrSuperAdmin(),
                PageRequest.of(normalizedPage, normalizedSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return PageResult.of(
                result.getContent().stream().map(OutboundOrderVO::fromEntity).toList(),
                normalizedPage,
                normalizedSize,
                result.getTotalElements()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public OutboundOrderVO findById(Long id) {
        return visibleOrderById(id)
                .map(OutboundOrderVO::fromEntity)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Outbound order not found"));
    }

    @Override
    @Transactional
    public OutboundOrderVO createVehicleOutbound(VehicleOutboundOrderCreateDTO request) {
        MachineInventory machine = machineRepository.findByIdForUpdate(request.getMachineId())
                .orElseThrow(() -> new BusinessException(ResultCode.VEHICLE_NOT_FOUND, "Vehicle not found"));
        ensureResourceVisible(Boolean.TRUE.equals(machine.getIsLocked()), ResultCode.VEHICLE_NOT_FOUND, "Vehicle not found");
        if (Boolean.TRUE.equals(machine.getModelOnly())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Model-only vehicle cannot be outbounded");
        }
        collaborationService.validateWrite(machine, request.getMachineVersion());
        int before = machine.getInventoryCount() == null ? 0 : machine.getInventoryCount();
        if (before < 1) {
            throw new BusinessException(ResultCode.INSUFFICIENT_STOCK, "Vehicle stock is insufficient");
        }
        if (rentalRecordRepository.existsByMachineIdAndStatus(machine.getId(), RentalStatuses.ACTIVE)) {
            throw new BusinessException(ResultCode.CONFLICT, "车辆正在租赁中，不能创建销售出库订单");
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
        order.setUnit("\u53f0");
        copyCustomer(order, customer);
        order.setSettlementPrice(request.getSettlementPrice());
        order.setSalesDate(request.getSalesDate());
        order.setSalePrice(firstPrice(request.getSalePrice(), machine.getSalePrice()));
        order.setReceivableAmount(firstPrice(request.getReceivableAmount(), request.getSettlementPrice()));
        order.setReceivedAmount(nonNegative(request.getReceivedAmount()));
        order.setPaymentDueDate(request.getPaymentDueDate());
        order.setLastPaymentDate(request.getLastPaymentDate());
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
        syncReceivableStatus(order);
        collaborationService.stampWrite(order);
        OutboundOrder savedOrder = outboundOrderRepository.saveAndFlush(order);

        int after = before - 1;
        machine.setInventoryCount(after);
        machine.setStockStatus(after > 0 ? MachineStockStatuses.IN_STOCK : MachineStockStatuses.OUTBOUND);
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
                joinRemark("闂佽桨鑳堕ˉ鎰攦閸涙潙绀勯柛婵嗗濮樸劑鎮规担闈涚仼鐎?" + savedOrder.getOrderNo(), request.getOrderRemark()),
                savedOrder.getId()
        );
        savedOrder.setStockOperationLogId(stockLog.getId());
        OutboundOrder result = outboundOrderRepository.saveAndFlush(savedOrder);

        operationAuditService.record("Outbound order", "CREATE", "OUTBOUND_ORDER", result.getId(),
                result.getOrderNo(), result.getCustomerName(),
                "Create vehicle outbound order " + result.getResourceCode(), result.getOperator(), result.getOrderRemark(),
                SOURCE_TYPE, result.getId());
        return OutboundOrderVO.fromEntity(result);
    }

    @Override
    @Transactional
    public OutboundOrderVO createPartOutbound(PartOutboundOrderCreateDTO request) {
        PartInventory part = partRepository.findByPartCodeForUpdate(request.getPartCode())
                .orElseThrow(() -> new BusinessException(ResultCode.PART_NOT_FOUND, "Part not found"));
        ensureResourceVisible(Boolean.TRUE.equals(part.getIsLocked()), ResultCode.PART_NOT_FOUND, "Part not found");
        collaborationService.validateWrite(part, request.getPartVersion());
        int quantity = request.getQuantity() == null ? 0 : request.getQuantity();
        if (quantity < 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "闂佸憡鍨甸幖顐よ姳闁秴鏋佸ù鍏兼綑濞呫倝鐓崶褎鍤囬柕鍡楃箲瀵板嫯顦辩紒?");
        }
        int before = part.getQuantity() == null ? 0 : part.getQuantity();
        if (before < quantity) {
            throw new BusinessException(ResultCode.INSUFFICIENT_STOCK, "Part stock is insufficient: " + before);
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
        order.setReceivableAmount(firstPrice(request.getReceivableAmount(), order.getSettlementPrice()));
        order.setReceivedAmount(nonNegative(request.getReceivedAmount()));
        order.setPaymentDueDate(request.getPaymentDueDate());
        order.setLastPaymentDate(request.getLastPaymentDate());
        if (request.getPaymentSettled() != null) {
            order.setPaymentSettled(request.getPaymentSettled());
        }
        order.setPaymentRemark(blankToNull(request.getPaymentRemark()));
        order.setOperator(blankToNull(request.getOperator()));
        order.setOrderRemark(blankToNull(request.getOrderRemark()));
        syncReceivableStatus(order);
        collaborationService.stampWrite(order);
        OutboundOrder savedOrder = outboundOrderRepository.saveAndFlush(order);

        int after = before - quantity;
        part.setQuantity(after);
        part.setIsSalesReported("\u5426");
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
                joinRemark("闂備焦婢樼粔鍐测枎閵忋倕绀勯柛婵嗗濮樸劑鎮规担闈涚仼鐎?" + savedOrder.getOrderNo(), request.getOrderRemark()),
                savedOrder.getId()
        );
        savedOrder.setStockOperationLogId(stockLog.getId());
        OutboundOrder result = outboundOrderRepository.saveAndFlush(savedOrder);

        operationAuditService.record("Outbound order", "CREATE", "OUTBOUND_ORDER", result.getId(),
                result.getOrderNo(), result.getCustomerName(),
                "Create part outbound order " + result.getResourceCode() + " x" + quantity,
                result.getOperator(), result.getOrderRemark(), SOURCE_TYPE, result.getId());
        return OutboundOrderVO.fromEntity(result);
    }

    @Override
    @Transactional
    public OutboundOrderVO update(Long id, OutboundOrderUpdateDTO request) {
        OutboundOrder order = outboundOrderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Outbound order not found"));
        ensureOrderVisible(order);
        collaborationService.validateWrite(order, request.getVersion());
        if (request.getSettlementPrice() != null) {
            order.setSettlementPrice(request.getSettlementPrice());
        }
        order.setSalesDate(request.getSalesDate());
        if (request.getSalePrice() != null) {
            order.setSalePrice(request.getSalePrice());
        }
        if (request.getReceivableAmount() != null) {
            order.setReceivableAmount(nonNegative(request.getReceivableAmount()));
        } else if (order.getReceivableAmount() == null) {
            order.setReceivableAmount(order.getSettlementPrice());
        }
        if (request.getReceivedAmount() != null) {
            order.setReceivedAmount(nonNegative(request.getReceivedAmount()));
        }
        order.setPaymentDueDate(request.getPaymentDueDate());
        order.setLastPaymentDate(request.getLastPaymentDate());
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
        syncReceivableStatus(order);
        collaborationService.stampWrite(order);
        OutboundOrder saved = outboundOrderRepository.saveAndFlush(order);
        syncLegacyOutboundFlags(saved);
        operationAuditService.record("Outbound order", "UPDATE", "OUTBOUND_ORDER", saved.getId(),
                saved.getOrderNo(), saved.getCustomerName(), "Update outbound order status",
                saved.getOperator(), saved.getOrderRemark(), SOURCE_TYPE, saved.getId());
        return OutboundOrderVO.fromEntity(saved);
    }

    @Override
    @Transactional
    public OutboundOrderVO setLocked(Long id, boolean locked, Long version) {
        if (!SecurityUtils.isAdminOrSuperAdmin()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "Only admins can lock outbound orders");
        }
        OutboundOrder order = outboundOrderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Outbound order not found"));
        collaborationService.validateWrite(order, version);
        if (locked) {
            order.setIsLocked(true);
            resourceLockService.lockRelatedResource(order);
        } else {
            resourceLockService.releaseRelatedResource(order);
            order.setIsLocked(false);
        }
        collaborationService.stampWrite(order);
        OutboundOrder saved = outboundOrderRepository.saveAndFlush(order);
        operationAuditService.record("Outbound order", locked ? "LOCK" : "UNLOCK", "OUTBOUND_ORDER", saved.getId(),
                saved.getOrderNo(), saved.getCustomerName(),
                locked ? "Lock outbound order and related resource" : "Unlock outbound order and related resource",
                saved.getOperator(), saved.getOrderRemark(), SOURCE_TYPE, saved.getId());
        return OutboundOrderVO.fromEntity(saved);
    }

    @Override
    @Transactional
    public OutboundOrderVO uploadInvoice(Long id, MultipartFile file, Long version) {
        OutboundOrder order = outboundOrderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Outbound order not found"));
        ensureOrderVisible(order);
        collaborationService.validateWrite(order, version);
        if (!uploadReadinessPolicy.isInvoiceUploadReady(order)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Order is not ready for invoice upload");
        }
        StoredOutboundFile storedFile = fileStorage.storeInvoice(order.getId(), file, order.getInvoiceStoredFileName());

        order.setInvoiceStoredFileName(storedFile.storedFileName());
        order.setInvoiceOriginalName(storedFile.originalName());
        order.setInvoiceContentType(storedFile.contentType());
        order.setInvoiceFileSize(storedFile.fileSize());
        order.setInvoiceUploadedAt(storedFile.uploadedAt());
        collaborationService.stampWrite(order);

        OutboundOrder saved = outboundOrderRepository.saveAndFlush(order);
        operationAuditService.record("Outbound order", "UPLOAD_INVOICE", "OUTBOUND_ORDER", saved.getId(),
                saved.getOrderNo(), saved.getCustomerName(), "Upload invoice: " + storedFile.originalName(),
                saved.getOperator(), saved.getOrderRemark(), SOURCE_TYPE, saved.getId());
        return OutboundOrderVO.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public OutboundInvoiceDownload downloadInvoice(Long id) {
        OutboundOrder order = visibleOrderById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Outbound order not found"));
        return fileStorage.downloadInvoice(order);
    }

    @Override
    @Transactional
    public OutboundOrderVO uploadContract(Long id, MultipartFile file, Long version) {
        OutboundOrder order = outboundOrderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Outbound order not found"));
        ensureOrderVisible(order);
        collaborationService.validateWrite(order, version);
        if (!uploadReadinessPolicy.isContractUploadReady(order)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Order is not ready for contract upload");
        }

        StoredOutboundFile storedFile = fileStorage.storeContract(order.getId(), file, order.getContractStoredFileName());

        order.setContractStoredFileName(storedFile.storedFileName());
        order.setContractOriginalName(storedFile.originalName());
        order.setContractContentType(storedFile.contentType());
        order.setContractFileSize(storedFile.fileSize());
        order.setContractUploadedAt(storedFile.uploadedAt());
        collaborationService.stampWrite(order);

        OutboundOrder saved = outboundOrderRepository.saveAndFlush(order);
        operationAuditService.record("Outbound order", "UPLOAD_CONTRACT", "OUTBOUND_ORDER", saved.getId(),
                saved.getOrderNo(), saved.getCustomerName(), "Upload contract: " + storedFile.originalName(),
                saved.getOperator(), saved.getOrderRemark(), SOURCE_TYPE, saved.getId());
        return OutboundOrderVO.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public OutboundInvoiceDownload downloadContract(Long id) {
        OutboundOrder order = visibleOrderById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Outbound order not found"));
        return fileStorage.downloadContract(order);
    }

    private Optional<OutboundOrder> visibleOrderById(Long id) {
        if (SecurityUtils.isAdminOrSuperAdmin()) {
            return outboundOrderRepository.findById(id);
        }
        return outboundOrderRepository.findByIdAndIsLockedFalse(id);
    }

    private void ensureOrderVisible(OutboundOrder order) {
        if (Boolean.TRUE.equals(order.getIsLocked()) && !SecurityUtils.isAdminOrSuperAdmin()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Outbound order not found");
        }
    }

    private void ensureResourceVisible(boolean locked, ResultCode resultCode, String message) {
        if (locked && !SecurityUtils.isAdminOrSuperAdmin()) {
            throw new BusinessException(resultCode, message);
        }
    }

    private Customer findCustomer(Long customerId) {
        return customerRepository.findByIdForUpdate(customerId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Customer not found"));
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
        operationAuditService.record(resourceType.equals(OutboundOrder.RESOURCE_MACHINE) ? "Machine stock" : "Part stock",
                "OUTBOUND", resourceType, resourceId, resourceCode, resourceName,
                "闂佸憡鍨甸幖顐よ姳?" + quantity, operator, remark, "STOCK", savedLog.getId());
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

    private BigDecimal nonNegative(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value.signum() < 0 ? BigDecimal.ZERO : value;
    }

    private void syncReceivableStatus(OutboundOrder order) {
        BigDecimal receivable = firstPrice(order.getReceivableAmount(), order.getSettlementPrice(), BigDecimal.ZERO);
        BigDecimal received = nonNegative(order.getReceivedAmount());
        order.setReceivableAmount(nonNegative(receivable));
        order.setReceivedAmount(received);

        if (Boolean.TRUE.equals(order.getPaymentSettled()) && received.compareTo(order.getReceivableAmount()) < 0) {
            order.setReceivedAmount(order.getReceivableAmount());
        }
        if (order.getReceivableAmount().signum() > 0 && order.getReceivedAmount().compareTo(order.getReceivableAmount()) >= 0) {
            order.setPaymentSettled(true);
        } else if (order.getReceivableAmount().subtract(order.getReceivedAmount()).signum() > 0) {
            order.setPaymentSettled(false);
        }
        if (Boolean.TRUE.equals(order.getPaymentSettled()) && order.getLastPaymentDate() == null) {
            order.setLastPaymentDate(LocalDate.now());
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
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
        return Boolean.TRUE.equals(value) ? "\u662f" : "\u5426";
    }
}
