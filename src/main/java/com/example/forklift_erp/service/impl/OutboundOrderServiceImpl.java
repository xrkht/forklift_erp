package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.common.PageResult;
import com.example.forklift_erp.common.ResultCode;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class OutboundOrderServiceImpl implements OutboundOrderService {

    private static final String SOURCE_TYPE = "OUTBOUND_ORDER";
    private static final DateTimeFormatter ORDER_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final long MAX_INVOICE_FILE_SIZE = 20L * 1024 * 1024;
    private static final long MAX_CONTRACT_FILE_SIZE = 20L * 1024 * 1024;
    private static final Set<String> ALLOWED_INVOICE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "pdf", "ofd", "jpg", "jpeg", "png", "webp"
    ));
    private static final Set<String> ALLOWED_CONTRACT_EXTENSIONS = new HashSet<>(Arrays.asList(
            "pdf", "ofd", "doc", "docx", "jpg", "jpeg", "png", "webp"
    ));

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

    @Value("${forklift-erp.invoice-storage-dir:${FORKLIFT_ERP_INVOICE_STORAGE_DIR:uploads/invoices}}")
    private String invoiceStorageDir;

    @Value("${forklift-erp.contract-storage-dir:${FORKLIFT_ERP_CONTRACT_STORAGE_DIR:uploads/contracts}}")
    private String contractStorageDir;

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
        if (rentalRecordRepository.existsByMachineIdAndStatus(machine.getId(), "ACTIVE")) {
            throw new BusinessException(ResultCode.CONFLICT, "Vehicle is currently rented and cannot be sold");
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
        order.setOperator(blankToNull(request.getOperator()));
        order.setOrderRemark(blankToNull(request.getOrderRemark()));
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
            lockRelatedResource(order);
        } else {
            releaseRelatedResource(order);
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
    public OutboundOrderVO uploadInvoice(Long id, MultipartFile file) {
        OutboundOrder order = outboundOrderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Outbound order not found"));
        ensureOrderVisible(order);
        validateInvoiceUpload(order, file);

        String originalName = cleanOriginalInvoiceName(file.getOriginalFilename());
        String extension = Objects.requireNonNull(StringUtils.getFilenameExtension(originalName)).toLowerCase(Locale.ROOT);
        String storedFileName = "order-" + order.getId() + "-" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                + "." + extension;
        Path target = storeUploadedFile(file, invoiceStorageRoot(), storedFileName, "Invoice file save failed");
        String previousFileName = order.getInvoiceStoredFileName();
        registerStoredFileLifecycle(target, () -> deleteStoredInvoice(previousFileName));

        order.setInvoiceStoredFileName(storedFileName);
        order.setInvoiceOriginalName(originalName);
        order.setInvoiceContentType(firstNonBlank(file.getContentType(), probeContentType(target), "application/octet-stream"));
        order.setInvoiceFileSize(file.getSize());
        order.setInvoiceUploadedAt(LocalDateTime.now());
        collaborationService.stampWrite(order);

        OutboundOrder saved = outboundOrderRepository.saveAndFlush(order);
        operationAuditService.record("Outbound order", "UPLOAD_INVOICE", "OUTBOUND_ORDER", saved.getId(),
                saved.getOrderNo(), saved.getCustomerName(), "Upload invoice: " + originalName,
                saved.getOperator(), saved.getOrderRemark(), SOURCE_TYPE, saved.getId());
        return OutboundOrderVO.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public OutboundInvoiceDownload downloadInvoice(Long id) {
        OutboundOrder order = visibleOrderById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Outbound order not found"));
        if (order.getInvoiceStoredFileName() == null || order.getInvoiceStoredFileName().isBlank()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Invoice has not been uploaded");
        }

        Path filePath = resolveInvoiceFile(order.getInvoiceStoredFileName());
        if (!Files.isRegularFile(filePath)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Invoice file not found");
        }
        try {
            Resource resource = new UrlResource(filePath.toUri());
            return new OutboundInvoiceDownload(
                    resource,
                    firstNonBlank(order.getInvoiceOriginalName(), "invoice-" + order.getOrderNo()),
                    firstNonBlank(order.getInvoiceContentType(), "application/octet-stream"),
                    Files.size(filePath)
            );
        } catch (IOException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "Invoice file download failed");
        }
    }

    @Override
    @Transactional
    public OutboundOrderVO uploadContract(Long id, MultipartFile file) {
        OutboundOrder order = outboundOrderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Outbound order not found"));
        ensureOrderVisible(order);
        validateContractUpload(order, file);

        String originalName = cleanOriginalContractName(file.getOriginalFilename());
        String extension = Objects.requireNonNull(StringUtils.getFilenameExtension(originalName)).toLowerCase(Locale.ROOT);
        String storedFileName = "order-" + order.getId() + "-" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                + "." + extension;
        Path target = storeUploadedFile(file, contractStorageRoot(), storedFileName, "Contract file save failed");
        String previousFileName = order.getContractStoredFileName();
        registerStoredFileLifecycle(target, () -> deleteStoredContract(previousFileName));

        order.setContractStoredFileName(storedFileName);
        order.setContractOriginalName(originalName);
        order.setContractContentType(firstNonBlank(file.getContentType(), probeContentType(target), "application/octet-stream"));
        order.setContractFileSize(file.getSize());
        order.setContractUploadedAt(LocalDateTime.now());
        collaborationService.stampWrite(order);

        OutboundOrder saved = outboundOrderRepository.saveAndFlush(order);
        operationAuditService.record("Outbound order", "UPLOAD_CONTRACT", "OUTBOUND_ORDER", saved.getId(),
                saved.getOrderNo(), saved.getCustomerName(), "Upload contract: " + originalName,
                saved.getOperator(), saved.getOrderRemark(), SOURCE_TYPE, saved.getId());
        return OutboundOrderVO.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public OutboundInvoiceDownload downloadContract(Long id) {
        OutboundOrder order = visibleOrderById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Outbound order not found"));
        if (order.getContractStoredFileName() == null || order.getContractStoredFileName().isBlank()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Contract has not been uploaded");
        }

        Path filePath = resolveContractFile(order.getContractStoredFileName());
        if (!Files.isRegularFile(filePath)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Contract file not found");
        }
        try {
            Resource resource = new UrlResource(filePath.toUri());
            return new OutboundInvoiceDownload(
                    resource,
                    firstNonBlank(order.getContractOriginalName(), "contract-" + order.getOrderNo()),
                    firstNonBlank(order.getContractContentType(), "application/octet-stream"),
                    Files.size(filePath)
            );
        } catch (IOException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "Contract file download failed");
        }
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

    private void lockRelatedResource(OutboundOrder order) {
        boolean previouslyOrderLocked = Boolean.TRUE.equals(order.getResourceLockedByOrder());
        if (order.getResourceId() == null) {
            order.setResourceLockedByOrder(false);
            return;
        }
        if (OutboundOrder.RESOURCE_MACHINE.equals(order.getResourceType())) {
            machineRepository.findByIdForUpdate(order.getResourceId()).ifPresent(machine -> {
                boolean alreadyLocked = Boolean.TRUE.equals(machine.getIsLocked());
                machine.setIsLocked(true);
                order.setResourceLockedByOrder(previouslyOrderLocked || !alreadyLocked);
                collaborationService.stampWrite(machine);
                machineRepository.saveAndFlush(machine);
            });
            return;
        }
        if (OutboundOrder.RESOURCE_PART.equals(order.getResourceType())) {
            partRepository.findByIdForUpdate(order.getResourceId()).ifPresent(part -> {
                boolean alreadyLocked = Boolean.TRUE.equals(part.getIsLocked());
                part.setIsLocked(true);
                order.setResourceLockedByOrder(previouslyOrderLocked || !alreadyLocked);
                collaborationService.stampWrite(part);
                partRepository.saveAndFlush(part);
            });
        }
    }

    private void releaseRelatedResource(OutboundOrder order) {
        if (!Boolean.TRUE.equals(order.getResourceLockedByOrder()) || order.getResourceId() == null) {
            order.setResourceLockedByOrder(false);
            return;
        }
        if (hasOtherLockedOrderForResource(order)) {
            order.setResourceLockedByOrder(false);
            return;
        }
        if (OutboundOrder.RESOURCE_MACHINE.equals(order.getResourceType())) {
            machineRepository.findByIdForUpdate(order.getResourceId()).ifPresent(machine -> {
                machine.setIsLocked(false);
                collaborationService.stampWrite(machine);
                machineRepository.saveAndFlush(machine);
            });
        } else if (OutboundOrder.RESOURCE_PART.equals(order.getResourceType())) {
            partRepository.findByIdForUpdate(order.getResourceId()).ifPresent(part -> {
                part.setIsLocked(false);
                collaborationService.stampWrite(part);
                partRepository.saveAndFlush(part);
            });
        }
        order.setResourceLockedByOrder(false);
    }

    private boolean hasOtherLockedOrderForResource(OutboundOrder order) {
        return outboundOrderRepository.existsByResourceTypeAndResourceIdAndIsLockedTrueAndIdNot(
                order.getResourceType(),
                order.getResourceId(),
                order.getId()
        );
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

    private void validateInvoiceUpload(OutboundOrder order, MultipartFile file) {
        if (!isInvoiceUploadReady(order)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Order is not ready for invoice upload");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Invoice file is required");
        }
        if (file.getSize() > MAX_INVOICE_FILE_SIZE) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "闂佸憡鐟﹂崹褰掑Χ閵娾晛妫橀柛銉檮椤愯棄鈽夐幘宕囆ラ柛蹇旑焽閹奸箖宕ㄩ幍顔剧暫20MB");
        }
        String originalName = cleanOriginalInvoiceName(file.getOriginalFilename());
        String extension = StringUtils.getFilenameExtension(originalName);
        if (extension == null || !ALLOWED_INVOICE_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Unsupported invoice file type");
        }
    }

    private void validateContractUpload(OutboundOrder order, MultipartFile file) {
        if (!isContractUploadReady(order)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Order is not ready for contract upload");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Contract file is required");
        }
        if (file.getSize() > MAX_CONTRACT_FILE_SIZE) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "闂佸憡鑹鹃悧鍡涘箖閹捐妫橀柛銉檮椤愯棄鈽夐幘宕囆ラ柛蹇旑焽閹奸箖宕ㄩ幍顔剧暫20MB");
        }
        String originalName = cleanOriginalContractName(file.getOriginalFilename());
        String extension = StringUtils.getFilenameExtension(originalName);
        if (extension == null || !ALLOWED_CONTRACT_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Unsupported contract file type");
        }
    }

    private boolean isInvoiceUploadReady(OutboundOrder order) {
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

    private boolean isContractUploadReady(OutboundOrder order) {
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

    private String cleanOriginalInvoiceName(String originalFilename) {
        String originalName = StringUtils.cleanPath(Objects.requireNonNullElse(originalFilename, "invoice.pdf")).trim();
        if (originalName.isBlank() || originalName.contains("..") || originalName.contains("/") || originalName.contains("\\")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Invoice file name is invalid");
        }
        return originalName;
    }

    private String cleanOriginalContractName(String originalFilename) {
        String originalName = StringUtils.cleanPath(Objects.requireNonNullElse(originalFilename, "contract.pdf")).trim();
        if (originalName.isBlank() || originalName.contains("..") || originalName.contains("/") || originalName.contains("\\")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Contract file name is invalid");
        }
        return originalName;
    }

    private Path invoiceStorageRoot() {
        return Paths.get(invoiceStorageDir).toAbsolutePath().normalize();
    }

    private Path contractStorageRoot() {
        return Paths.get(contractStorageDir).toAbsolutePath().normalize();
    }

    private Path storeUploadedFile(MultipartFile file, Path root, String storedFileName, String failureMessage) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path target = normalizedRoot.resolve(storedFileName).normalize();
        if (!target.startsWith(normalizedRoot)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "File path is invalid");
        }

        Path tempFile = null;
        try {
            Files.createDirectories(normalizedRoot);
            tempFile = Files.createTempFile(normalizedRoot, storedFileName + "-", ".tmp");
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            moveIntoPlace(tempFile, target);
            return target;
        } catch (IOException e) {
            deleteQuietly(tempFile, "Failed to delete temp upload");
            throw new BusinessException(ResultCode.SYSTEM_ERROR, failureMessage);
        }
    }

    private void moveIntoPlace(Path tempFile, Path target) throws IOException {
        try {
            Files.move(tempFile, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void registerStoredFileLifecycle(Path newFile, Runnable deletePreviousFile) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deletePreviousFile.run();
            }

            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    deleteQuietly(newFile, "Failed to delete rolled back upload");
                }
            }
        });
    }

    private Path resolveInvoiceFile(String storedFileName) {
        Path root = invoiceStorageRoot();
        Path filePath = root.resolve(storedFileName).normalize();
        if (!filePath.startsWith(root)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Invoice file path is invalid");
        }
        return filePath;
    }

    private Path resolveContractFile(String storedFileName) {
        Path root = contractStorageRoot();
        Path filePath = root.resolve(storedFileName).normalize();
        if (!filePath.startsWith(root)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Contract file path is invalid");
        }
        return filePath;
    }

    private void deleteQuietly(Path path, String message) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("{}: {}", message, path, e);
        }
    }

    private String probeContentType(Path target) {
        try {
            return Files.probeContentType(target);
        } catch (IOException e) {
            return null;
        }
    }

    private void deleteStoredInvoice(String storedFileName) {
        if (storedFileName == null || storedFileName.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolveInvoiceFile(storedFileName));
        } catch (IOException e) {
            log.warn("闂佸憡甯炴繛鈧繛鍛叄瀵喛顦茬憸鏉垮€荤划濠囧Ω閿旂晫鈧喖霉閻樼儤纭鹃柕鍥ㄥ灩閹? {}", storedFileName, e);
        }
    }

    private void deleteStoredContract(String storedFileName) {
        if (storedFileName == null || storedFileName.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolveContractFile(storedFileName));
        } catch (IOException e) {
            log.warn("闂佸憡甯炴繛鈧繛鍛叄瀵喛顦查柟顔奸叄瀹曘儳浠﹂悙顒傗偓顔济归悩鐑樼【闁靛洦鍨归幏? {}", storedFileName, e);
        }
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

    private String normalizeKeyword(String keyword) {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
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
