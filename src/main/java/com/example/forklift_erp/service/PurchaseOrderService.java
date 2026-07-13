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
import com.example.forklift_erp.util.BusinessNumberGenerator;
import com.example.forklift_erp.util.ListPageSupport;
import com.example.forklift_erp.util.MoneyValues;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
public class PurchaseOrderService {
    private static final String STATUS_ORDERED = "ORDERED";
    private static final String STATUS_PARTIAL = "PARTIAL";
    private static final String STATUS_ARRIVED = "ARRIVED";
    private static final String STATUS_RECEIVED = "RECEIVED";
    private static final String STATUS_CANCELED = "CANCELED";

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
        return findPage(keyword, null, page, size);
    }

    @Transactional(readOnly = true)
    public PageResult<PurchaseOrderVO> findPage(String keyword, String resourceType, Integer page, Integer size) {
        int normalizedPage = ListPageSupport.page(page);
        int normalizedSize = ListPageSupport.size(size);
        Page<PurchaseOrder> result = purchaseOrderRepository.searchPage(
                normalizeKeyword(keyword),
                normalizeResourceTypeFilter(resourceType),
                ListPageSupport.pageRequest(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
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
        PurchaseOrder order = new PurchaseOrder();
        order.setPurchaseNo(nextPurchaseNo());
        copy(request, order);
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
        copy(request, order);
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
        if (received) {
            markReceived(order);
        } else {
            restoreStatusBeforeReceived(order);
        }
        if (order.getFreightAmount() == null) {
            order.setFreightAmount(BigDecimal.ZERO);
        }
        collaborationService.stampWrite(order);
        PurchaseOrder saved = purchaseOrderRepository.saveAndFlush(order);
        operationAuditService.record("Purchase order", received ? STATUS_RECEIVED : saved.getStatus(), "PURCHASE_ORDER", saved.getId(),
                saved.getPurchaseNo(), saved.getSupplierName(), received ? "Mark purchase received" : "Mark purchase not received",
                saved.getOperator(), saved.getRemark());
        return PurchaseOrderVO.fromEntity(saved);
    }

    @Transactional
    public PurchaseOrderVO updateFreight(Long id, BigDecimal freightAmount, Long version) {
        PurchaseOrder order = purchaseOrderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Purchase order not found"));
        collaborationService.validateWrite(order, version);
        BigDecimal normalizedFreight = MoneyValues.zeroIfNegative(freightAmount);
        order.setFreightAmount(normalizedFreight == null ? BigDecimal.ZERO : normalizedFreight);
        collaborationService.stampWrite(order);
        PurchaseOrder saved = purchaseOrderRepository.saveAndFlush(order);
        operationAuditService.record("Purchase order", "FREIGHT_UPDATE", "PURCHASE_ORDER", saved.getId(),
                saved.getPurchaseNo(), saved.getSupplierName(), "Update purchase freight", saved.getOperator(), saved.getRemark());
        return PurchaseOrderVO.fromEntity(saved);
    }

    private void copy(PurchaseOrderDTO request, PurchaseOrder order) {
        String resourceType = normalizeResourceType(request.getResourceType());
        Supplier supplier = resolveSupplier(request, resourceType);
        ConfigItem configItem = null;
        ConfigValue configValue = null;
        if (PurchaseOrder.RESOURCE_PART.equals(resourceType)) {
            configItem = request.getConfigItemId() == null ? null : configItemRepository.findById(request.getConfigItemId())
                    .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Config item not found"));
            configValue = request.getConfigValueId() == null ? null : configValueRepository.findById(request.getConfigValueId())
                    .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Config value not found"));
            if (configValue != null && configItem != null && !configValue.getConfigItemId().equals(configItem.getId())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "Config value does not belong to selected config item");
            }
            if (configValue != null && configItem == null) {
                configItem = configItemRepository.findById(configValue.getConfigItemId())
                        .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Config item not found"));
            }
        }
        if (PurchaseOrder.RESOURCE_MACHINE.equals(resourceType) && blankToNull(request.getSpecificationModel()) == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Specification model is required for machine inbound orders");
        }

        order.setSupplierId(supplier == null ? null : supplier.getId());
        order.setSupplierName(supplier == null ? supplierName(request) : supplier.getSupplierName());
        order.setConfigItemId(PurchaseOrder.RESOURCE_PART.equals(resourceType) && configItem != null ? configItem.getId() : null);
        order.setConfigValueId(PurchaseOrder.RESOURCE_PART.equals(resourceType) && configValue != null ? configValue.getId() : null);
        order.setResourceType(resourceType);
        order.setResourceCode(PurchaseOrder.RESOURCE_PART.equals(resourceType) && configValue != null ? blankToNull(configValue.getValueCode()) : blankToNull(request.getResourceCode()));
        order.setResourceName(PurchaseOrder.RESOURCE_PART.equals(resourceType) && configValue != null ? configValue.getValueLabel() : blankToNull(request.getResourceName()));
        order.setSpecificationModel(PurchaseOrder.RESOURCE_PART.equals(resourceType) && configItem != null ? configItemLabel(configItem) : blankToNull(request.getSpecificationModel()));
        order.setQuantity(request.getQuantity() == null ? 1 : request.getQuantity());
        String requestUnit = blankToNull(request.getUnit());
        if (requestUnit == null && configItem != null) {
            requestUnit = blankToNull(configItem.getUnit());
        }
        if (requestUnit == null && PurchaseOrder.RESOURCE_MACHINE.equals(resourceType)) {
            requestUnit = "台";
        }
        order.setUnit(requestUnit);
        order.setUnitPrice(MoneyValues.zeroIfNegative(request.getUnitPrice()));
        order.setTotalAmount(totalAmount(order.getQuantity(), order.getUnitPrice(), request.getTotalAmount()));
        if (request.getFreightAmount() != null) {
            order.setFreightAmount(MoneyValues.zeroIfNegative(request.getFreightAmount()));
        } else if (order.getFreightAmount() == null) {
            order.setFreightAmount(BigDecimal.ZERO);
        }
        order.setOrderDate(request.getOrderDate() == null ? LocalDate.now() : request.getOrderDate());
        order.setExpectedArrivalDate(request.getExpectedArrivalDate());
        applyRequestedStatus(order, request.getStatus());
        order.setOperator(blankToNull(request.getOperator()));
        order.setRemark(blankToNull(request.getRemark()));
    }

    private void applyRequestedStatus(PurchaseOrder order, String requestedStatus) {
        String nextStatus = blankToNull(requestedStatus);
        if (nextStatus == null) {
            nextStatus = STATUS_ORDERED;
        }
        String currentStatus = blankToNull(order.getStatus());
        if (currentStatus == null) {
            currentStatus = STATUS_ORDERED;
        }

        if (STATUS_RECEIVED.equals(nextStatus)) {
            if (!STATUS_RECEIVED.equals(currentStatus)) {
                markReceived(order);
            }
            return;
        }

        order.setStatus(nextStatus);
        order.setStatusBeforeReceived(null);
    }

    private void markReceived(PurchaseOrder order) {
        String currentStatus = blankToNull(order.getStatus());
        if (STATUS_RECEIVED.equals(currentStatus)) {
            return;
        }
        if (STATUS_CANCELED.equals(currentStatus)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Canceled purchase order cannot be received");
        }
        if (!isReceivableStatus(currentStatus)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Purchase order status cannot be marked received: " + currentStatus);
        }
        order.setStatusBeforeReceived(currentStatus);
        order.setStatus(STATUS_RECEIVED);
    }

    private void restoreStatusBeforeReceived(PurchaseOrder order) {
        if (!STATUS_RECEIVED.equals(order.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Only received purchase orders can undo receipt");
        }
        String previousStatus = blankToNull(order.getStatusBeforeReceived());
        order.setStatus(isReceivableStatus(previousStatus) ? previousStatus : STATUS_ORDERED);
        order.setStatusBeforeReceived(null);
    }

    private boolean isReceivableStatus(String status) {
        return STATUS_ORDERED.equals(status) || STATUS_PARTIAL.equals(status) || STATUS_ARRIVED.equals(status);
    }

    private BigDecimal totalAmount(Integer quantity, BigDecimal unitPrice, BigDecimal requestTotal) {
        if (requestTotal != null) {
            return MoneyValues.zeroIfNegative(requestTotal);
        }
        if (unitPrice == null) {
            return BigDecimal.ZERO;
        }
        return unitPrice.multiply(BigDecimal.valueOf(quantity == null ? 0 : quantity));
    }

    private Supplier resolveSupplier(PurchaseOrderDTO request, String resourceType) {
        if (request.getSupplierId() != null) {
            return supplierRepository.findById(request.getSupplierId())
                    .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Supplier not found"));
        }
        if (PurchaseOrder.RESOURCE_PART.equals(resourceType)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Supplier is required for part inbound orders");
        }
        return null;
    }

    private String supplierName(PurchaseOrderDTO request) {
        String name = blankToNull(request.getSupplierName());
        return name == null ? blankToNull(request.getSupplier()) : name;
    }

    private String normalizeResourceType(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return PurchaseOrder.RESOURCE_PART;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!PurchaseOrder.RESOURCE_PART.equals(normalized) && !PurchaseOrder.RESOURCE_MACHINE.equals(normalized)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Unsupported inbound resource type: " + normalized);
        }
        return normalized;
    }

    private String normalizeResourceTypeFilter(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!PurchaseOrder.RESOURCE_PART.equals(normalized) && !PurchaseOrder.RESOURCE_MACHINE.equals(normalized)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Unsupported inbound resource type: " + normalized);
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
        return BusinessNumberGenerator.next("PO", 6);
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
