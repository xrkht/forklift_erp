package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.common.PageResult;
import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.constant.MachineStockStatus;
import com.example.forklift_erp.constant.RentalStatus;
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
import com.example.forklift_erp.service.CollaborationService;
import com.example.forklift_erp.service.OperationAuditService;
import com.example.forklift_erp.service.OutboundOrderService;
import com.example.forklift_erp.service.ResourceVisibilityPolicy;
import com.example.forklift_erp.service.ResourceAttachmentService;
import com.example.forklift_erp.util.BusinessNumberGenerator;
import com.example.forklift_erp.util.InventoryQuantities;
import com.example.forklift_erp.util.ListPageSupport;
import com.example.forklift_erp.util.MoneyValues;
import com.example.forklift_erp.util.SearchKeywordSupport;
import com.example.forklift_erp.util.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class OutboundOrderServiceImpl implements OutboundOrderService {

    private static final String SOURCE_TYPE = "OUTBOUND_ORDER";

    private final OutboundOrderRepository outboundOrderRepository;
    private final CustomerRepository customerRepository;
    private final MachineInventoryRepository machineRepository;
    private final PartInventoryRepository partRepository;
    private final RentalRecordRepository rentalRecordRepository;
    private final OperationAuditService operationAuditService;
    private final CollaborationService collaborationService;
    private final OutboundOrderFileStorage fileStorage;
    private final OutboundUploadReadinessPolicy uploadReadinessPolicy;
    private final OutboundResourceLockService resourceLockService;
    private final ResourceAttachmentService resourceAttachmentService;
    private final ResourceVisibilityPolicy visibilityPolicy;
    private final OutboundReceivablePolicy receivablePolicy;
    private final OutboundStockAccountingService stockAccountingService;

    public OutboundOrderServiceImpl(
            OutboundOrderRepository outboundOrderRepository,
            CustomerRepository customerRepository,
            MachineInventoryRepository machineRepository,
            PartInventoryRepository partRepository,
            RentalRecordRepository rentalRecordRepository,
            OperationAuditService operationAuditService,
            CollaborationService collaborationService,
            OutboundOrderFileStorage fileStorage,
            OutboundUploadReadinessPolicy uploadReadinessPolicy,
            OutboundResourceLockService resourceLockService,
            ResourceAttachmentService resourceAttachmentService,
            ResourceVisibilityPolicy visibilityPolicy,
            OutboundReceivablePolicy receivablePolicy,
            OutboundStockAccountingService stockAccountingService
    ) {
        this.outboundOrderRepository = outboundOrderRepository;
        this.customerRepository = customerRepository;
        this.machineRepository = machineRepository;
        this.partRepository = partRepository;
        this.rentalRecordRepository = rentalRecordRepository;
        this.operationAuditService = operationAuditService;
        this.collaborationService = collaborationService;
        this.fileStorage = fileStorage;
        this.uploadReadinessPolicy = uploadReadinessPolicy;
        this.resourceLockService = resourceLockService;
        this.resourceAttachmentService = resourceAttachmentService;
        this.visibilityPolicy = visibilityPolicy;
        this.receivablePolicy = receivablePolicy;
        this.stockAccountingService = stockAccountingService;
    }

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
                SearchKeywordSupport.likePrefix(keyword),
                SearchKeywordSupport.fullTextBoolean(keyword),
                SecurityUtils.isAdminOrSuperAdmin(),
                ListPageSupport.pageRequest(page, size)
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
        InventoryQuantities.QuantityChange stockChange = InventoryQuantities.outbound(
                machine.getInventoryCount(),
                1,
                "Outbound quantity must be greater than 0",
                before -> "Vehicle stock is insufficient"
        );
        if (rentalRecordRepository.existsByMachineIdAndStatus(machine.getId(), RentalStatus.ACTIVE.code())) {
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
        order.setSettlementPrice(MoneyValues.firstNonNegativeOrNull(request.getSettlementPrice(), BigDecimal.ZERO));
        order.setSalesDate(request.getSalesDate());
        order.setSalePrice(MoneyValues.firstNonNegativeOrNull(request.getSalePrice(), machine.getSalePrice()));
        order.setReceivableAmount(MoneyValues.firstNonNegativeOrNull(request.getReceivableAmount(), request.getSettlementPrice()));
        order.setReceivedAmount(MoneyValues.zeroIfNullOrNegative(request.getReceivedAmount()));
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
        receivablePolicy.apply(order);
        collaborationService.stampWrite(order);
        OutboundOrder savedOrder = outboundOrderRepository.save(order);

        BigDecimal unitCost = stockAccountingService.machineUnitCost(machine);
        machine.setInventoryCount(stockChange.afterQuantity());
        machine.setStockStatus(stockChange.afterQuantity() > 0 ? MachineStockStatus.IN_STOCK.code() : MachineStockStatus.OUTBOUND.code());
        machine.setSettlementPrice(MoneyValues.firstNonNegativeOrNull(request.getSettlementPrice(), BigDecimal.ZERO));
        machine.setSalePrice(MoneyValues.firstNonNegativeOrNull(request.getSalePrice(), machine.getSalePrice()));
        machine.setSalesDate(toMachineSalesDate(request.getSalesDate()));
        machine.setDestination1(customer.getCompanyName());
        machine.setIsSalesReported(yesNo(order.getSalesReported()));
        machine.setSalesReportDate(order.getSalesReportDate());
        machine.setIsInvoiceApplied(yesNo(order.getInvoiceApplied()));
        collaborationService.stampWrite(machine);
        MachineInventory savedMachine = machineRepository.save(machine);

        StockOperationLog stockLog = stockAccountingService.recordMachineOutbound(
                savedMachine,
                savedOrder,
                stockChange,
                unitCost,
                request.getOperator(),
                joinRemark("Vehicle outbound order " + savedOrder.getOrderNo(), request.getOrderRemark())
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
        InventoryQuantities.QuantityChange stockChange = InventoryQuantities.outbound(
                part.getQuantity(),
                request.getQuantity(),
                "Outbound quantity must be greater than 0",
                "Part stock is insufficient: "
        );
        int quantity = stockChange.quantity();

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
        order.setSettlementPrice(MoneyValues.firstNonNegativeOrNull(request.getSettlementPrice(), part.getSettlementPrice(), part.getSalePrice()));
        order.setReceivableAmount(MoneyValues.firstNonNegativeOrNull(request.getReceivableAmount(), order.getSettlementPrice()));
        order.setReceivedAmount(MoneyValues.zeroIfNullOrNegative(request.getReceivedAmount()));
        order.setPaymentDueDate(request.getPaymentDueDate());
        order.setLastPaymentDate(request.getLastPaymentDate());
        if (request.getPaymentSettled() != null) {
            order.setPaymentSettled(request.getPaymentSettled());
        }
        order.setPaymentRemark(blankToNull(request.getPaymentRemark()));
        order.setOperator(blankToNull(request.getOperator()));
        order.setOrderRemark(blankToNull(request.getOrderRemark()));
        receivablePolicy.apply(order);
        collaborationService.stampWrite(order);
        OutboundOrder savedOrder = outboundOrderRepository.save(order);

        part.setQuantity(stockChange.afterQuantity());
        part.setIsSalesReported("\u5426");
        collaborationService.stampWrite(part);
        PartInventory savedPart = partRepository.save(part);

        StockOperationLog stockLog = stockAccountingService.recordPartOutbound(
                savedPart,
                savedOrder,
                stockChange,
                request.getOperator(),
                joinRemark("Part outbound order " + savedOrder.getOrderNo(), request.getOrderRemark())
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
            order.setSettlementPrice(MoneyValues.firstNonNegativeOrNull(request.getSettlementPrice(), BigDecimal.ZERO));
        }
        order.setSalesDate(request.getSalesDate());
        if (request.getSalePrice() != null) {
            order.setSalePrice(MoneyValues.firstNonNegativeOrNull(request.getSalePrice(), BigDecimal.ZERO));
        }
        if (request.getReceivableAmount() != null) {
            order.setReceivableAmount(MoneyValues.zeroIfNullOrNegative(request.getReceivableAmount()));
        } else if (order.getReceivableAmount() == null) {
            order.setReceivableAmount(order.getSettlementPrice());
        }
        if (request.getReceivedAmount() != null) {
            order.setReceivedAmount(MoneyValues.zeroIfNullOrNegative(request.getReceivedAmount()));
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
        receivablePolicy.apply(order);
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
        resourceAttachmentService.recordLegacyOrderAttachment(saved, "INVOICE", storedFile);
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
        resourceAttachmentService.recordLegacyOrderAttachment(saved, "CONTRACT", storedFile);
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
        visibilityPolicy.ensureVisible(order.getIsLocked(), ResultCode.NOT_FOUND, "Outbound order not found");
    }

    private void ensureResourceVisible(boolean locked, ResultCode resultCode, String message) {
        visibilityPolicy.ensureVisible(locked, resultCode, message);
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
                machine.setSettlementPrice(MoneyValues.firstNonNegativeOrNull(order.getSettlementPrice(), machine.getSettlementPrice()));
                machine.setSalePrice(MoneyValues.firstNonNegativeOrNull(order.getSalePrice(), machine.getSalePrice()));
                machine.setSalesDate(toMachineSalesDate(order.getSalesDate()));
                machine.setIsSalesReported(yesNo(order.getSalesReported()));
                machine.setSalesReportDate(order.getSalesReportDate());
                machine.setIsInvoiceApplied(yesNo(order.getInvoiceApplied()));
                collaborationService.stampWrite(machine);
                machineRepository.save(machine);
            });
            return;
        }
        if (OutboundOrder.RESOURCE_PART.equals(order.getResourceType())) {
            partRepository.findByIdForUpdate(order.getResourceId()).ifPresent(part -> {
                part.setIsSalesReported(yesNo(order.getSalesReported()));
                part.setSalesReportDate(order.getSalesReportDate());
                collaborationService.stampWrite(part);
                partRepository.save(part);
            });
        }
    }

    private String nextOrderNo() {
        return BusinessNumberGenerator.next("OO", 6);
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
        return Boolean.TRUE.equals(value) ? "\u662f" : "\u5426";
    }
}
