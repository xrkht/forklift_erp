package com.example.forklift_erp.service;

import com.example.forklift_erp.common.PageResult;
import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.dto.PurchaseOrderDTO;
import com.example.forklift_erp.dto.PurchaseOrderVO;
import com.example.forklift_erp.entity.ConfigItem;
import com.example.forklift_erp.entity.ConfigValue;
import com.example.forklift_erp.entity.PurchaseOrder;
import com.example.forklift_erp.entity.Supplier;
import com.example.forklift_erp.exception.BusinessException;
import com.example.forklift_erp.repository.ConfigItemRepository;
import com.example.forklift_erp.repository.ConfigValueRepository;
import com.example.forklift_erp.repository.PurchaseOrderRepository;
import com.example.forklift_erp.repository.SupplierRepository;
import com.example.forklift_erp.util.ListPageSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
public class PurchaseOrderService {
    private static final DateTimeFormatter ORDER_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private ConfigItemRepository configItemRepository;

    @Autowired
    private ConfigValueRepository configValueRepository;

    @Autowired
    private CollaborationService collaborationService;

    @Autowired
    private OperationAuditService operationAuditService;

    @Transactional(readOnly = true)
    public List<PurchaseOrderVO> findAll() {
        return purchaseOrderRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(PurchaseOrderVO::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResult<PurchaseOrderVO> findPage(String keyword, Integer page, Integer size) {
        int normalizedPage = ListPageSupport.page(page);
        int normalizedSize = ListPageSupport.size(size);
        Page<PurchaseOrder> result = purchaseOrderRepository.searchPage(
                normalizeKeyword(keyword),
                PageRequest.of(normalizedPage, normalizedSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return PageResult.of(
                result.getContent().stream().map(PurchaseOrderVO::fromEntity).toList(),
                normalizedPage,
                normalizedSize,
                result.getTotalElements()
        );
    }

    @Transactional
    public PurchaseOrderVO create(PurchaseOrderDTO request) {
        Supplier supplier = supplierRepository.findById(request.getSupplierId())
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Supplier not found"));
        PurchaseOrder order = new PurchaseOrder();
        order.setPurchaseNo(nextPurchaseNo());
        copy(request, supplier, order);
        collaborationService.stampWrite(order);
        PurchaseOrder saved = purchaseOrderRepository.saveAndFlush(order);
        operationAuditService.record("Purchase order", "CREATE", "PURCHASE_ORDER", saved.getId(),
                saved.getPurchaseNo(), saved.getSupplierName(), "Create purchase order", saved.getOperator(), saved.getRemark());
        return PurchaseOrderVO.fromEntity(saved);
    }

    @Transactional
    public PurchaseOrderVO update(Long id, PurchaseOrderDTO request) {
        PurchaseOrder order = purchaseOrderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Purchase order not found"));
        collaborationService.validateWrite(order, request.getVersion());
        Supplier supplier = supplierRepository.findById(request.getSupplierId())
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Supplier not found"));
        copy(request, supplier, order);
        collaborationService.stampWrite(order);
        PurchaseOrder saved = purchaseOrderRepository.saveAndFlush(order);
        operationAuditService.record("Purchase order", "UPDATE", "PURCHASE_ORDER", saved.getId(),
                saved.getPurchaseNo(), saved.getSupplierName(), "Update purchase order", saved.getOperator(), saved.getRemark());
        return PurchaseOrderVO.fromEntity(saved);
    }

    @Transactional
    public void delete(Long id, Long version) {
        PurchaseOrder order = purchaseOrderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Purchase order not found"));
        collaborationService.validateWrite(order, version);
        purchaseOrderRepository.delete(order);
        operationAuditService.record("Purchase order", "DELETE", "PURCHASE_ORDER", id,
                order.getPurchaseNo(), order.getSupplierName(), "Delete purchase order", order.getOperator(), order.getRemark());
    }

    @Transactional
    public PurchaseOrderVO setReceived(Long id, boolean received, Long version) {
        PurchaseOrder order = purchaseOrderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Purchase order not found"));
        collaborationService.validateWrite(order, version);
        order.setStatus(received ? "RECEIVED" : "ORDERED");
        if (order.getFreightAmount() == null) {
            order.setFreightAmount(BigDecimal.ZERO);
        }
        collaborationService.stampWrite(order);
        PurchaseOrder saved = purchaseOrderRepository.saveAndFlush(order);
        operationAuditService.record("Purchase order", received ? "RECEIVED" : "ORDERED", "PURCHASE_ORDER", saved.getId(),
                saved.getPurchaseNo(), saved.getSupplierName(), received ? "Mark purchase received" : "Mark purchase not received",
                saved.getOperator(), saved.getRemark());
        return PurchaseOrderVO.fromEntity(saved);
    }

    @Transactional
    public PurchaseOrderVO updateFreight(Long id, BigDecimal freightAmount, Long version) {
        PurchaseOrder order = purchaseOrderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Purchase order not found"));
        collaborationService.validateWrite(order, version);
        BigDecimal normalizedFreight = nonNegative(freightAmount);
        order.setFreightAmount(normalizedFreight == null ? BigDecimal.ZERO : normalizedFreight);
        collaborationService.stampWrite(order);
        PurchaseOrder saved = purchaseOrderRepository.saveAndFlush(order);
        operationAuditService.record("Purchase order", "FREIGHT_UPDATE", "PURCHASE_ORDER", saved.getId(),
                saved.getPurchaseNo(), saved.getSupplierName(), "Update purchase freight", saved.getOperator(), saved.getRemark());
        return PurchaseOrderVO.fromEntity(saved);
    }

    private void copy(PurchaseOrderDTO request, Supplier supplier, PurchaseOrder order) {
        ConfigItem configItem = request.getConfigItemId() == null ? null : configItemRepository.findById(request.getConfigItemId())
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Config item not found"));
        ConfigValue configValue = request.getConfigValueId() == null ? null : configValueRepository.findById(request.getConfigValueId())
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Config value not found"));
        if (configValue != null && configItem != null && !configValue.getConfigItemId().equals(configItem.getId())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Config value does not belong to selected config item");
        }
        if (configValue != null && configItem == null) {
            configItem = configItemRepository.findById(configValue.getConfigItemId())
                    .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Config item not found"));
        }

        order.setSupplierId(supplier.getId());
        order.setSupplierName(supplier.getSupplierName());
        order.setConfigItemId(configItem == null ? null : configItem.getId());
        order.setConfigValueId(configValue == null ? null : configValue.getId());
        order.setResourceType(normalizeResourceType(request.getResourceType()));
        order.setResourceCode(configValue != null ? blankToNull(configValue.getValueCode()) : blankToNull(request.getResourceCode()));
        order.setResourceName(configValue != null ? configValue.getValueLabel() : blankToNull(request.getResourceName()));
        order.setSpecificationModel(configItem != null ? configItemLabel(configItem) : blankToNull(request.getSpecificationModel()));
        order.setQuantity(request.getQuantity() == null ? 1 : request.getQuantity());
        order.setUnit(blankToNull(request.getUnit()) == null && configItem != null ? blankToNull(configItem.getUnit()) : blankToNull(request.getUnit()));
        order.setUnitPrice(nonNegative(request.getUnitPrice()));
        order.setTotalAmount(totalAmount(order.getQuantity(), order.getUnitPrice(), request.getTotalAmount()));
        if (request.getFreightAmount() != null) {
            order.setFreightAmount(nonNegative(request.getFreightAmount()));
        } else if (order.getFreightAmount() == null) {
            order.setFreightAmount(BigDecimal.ZERO);
        }
        order.setOrderDate(request.getOrderDate() == null ? LocalDate.now() : request.getOrderDate());
        order.setExpectedArrivalDate(request.getExpectedArrivalDate());
        order.setStatus(blankToNull(request.getStatus()) == null ? "ORDERED" : request.getStatus().trim());
        order.setOperator(blankToNull(request.getOperator()));
        order.setRemark(blankToNull(request.getRemark()));
    }

    private BigDecimal totalAmount(Integer quantity, BigDecimal unitPrice, BigDecimal requestTotal) {
        if (requestTotal != null) {
            return nonNegative(requestTotal);
        }
        if (unitPrice == null) {
            return BigDecimal.ZERO;
        }
        return unitPrice.multiply(BigDecimal.valueOf(quantity == null ? 0 : quantity));
    }

    private BigDecimal nonNegative(BigDecimal value) {
        if (value == null) return null;
        return value.signum() < 0 ? BigDecimal.ZERO : value;
    }

    private String normalizeResourceType(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return PurchaseOrder.RESOURCE_PART;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!PurchaseOrder.RESOURCE_PART.equals(normalized)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Purchase orders only support part inbound records");
        }
        return normalized;
    }

    private String configItemLabel(ConfigItem item) {
        return List.of(item.getCategory(), item.getSubCategory(), item.getItemName()).stream()
                .map(this::blankToNull)
                .filter(part -> part != null)
                .reduce((left, right) -> left + " / " + right)
                .orElse(null);
    }

    private String nextPurchaseNo() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
        return "PO-" + ORDER_NO_TIME.format(LocalDateTime.now()) + "-" + suffix;
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
