package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.common.PageResult;
import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.constant.MachineStockStatuses;
import com.example.forklift_erp.constant.ModificationWorkOrderStatuses;
import com.example.forklift_erp.constant.PartChangeActions;
import com.example.forklift_erp.dto.ModificationWorkOrderActionDTO;
import com.example.forklift_erp.dto.ModificationWorkOrderCreateDTO;
import com.example.forklift_erp.dto.ModificationWorkOrderVO;
import com.example.forklift_erp.dto.PartReplaceRequestDTO;
import com.example.forklift_erp.entity.ConfigReplaceLog;
import com.example.forklift_erp.entity.ConfigValue;
import com.example.forklift_erp.entity.MachineConfig;
import com.example.forklift_erp.entity.MachineInventory;
import com.example.forklift_erp.entity.ModificationWorkOrder;
import com.example.forklift_erp.entity.ModificationWorkOrderLine;
import com.example.forklift_erp.entity.PartInventory;
import com.example.forklift_erp.exception.BusinessException;
import com.example.forklift_erp.repository.MachineConfigRepository;
import com.example.forklift_erp.repository.MachineInventoryRepository;
import com.example.forklift_erp.repository.ModificationWorkOrderLineRepository;
import com.example.forklift_erp.repository.ModificationWorkOrderRepository;
import com.example.forklift_erp.repository.PartInventoryRepository;
import com.example.forklift_erp.repository.ConfigItemRepository;
import com.example.forklift_erp.repository.ConfigValueRepository;
import com.example.forklift_erp.service.CollaborationService;
import com.example.forklift_erp.service.ConfigReplaceService;
import com.example.forklift_erp.service.ModificationWorkOrderService;
import com.example.forklift_erp.service.OperationAuditService;
import com.example.forklift_erp.util.ListPageSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ModificationWorkOrderServiceImpl implements ModificationWorkOrderService {
    private static final String MOVEMENT_SOURCE_TYPE = "MODIFICATION_WORK_ORDER";
    private static final DateTimeFormatter ORDER_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    @Autowired
    private ModificationWorkOrderRepository workOrderRepository;

    @Autowired
    private ModificationWorkOrderLineRepository lineRepository;

    @Autowired
    private MachineInventoryRepository machineRepository;

    @Autowired
    private MachineConfigRepository machineConfigRepository;

    @Autowired
    private PartInventoryRepository partRepository;

    @Autowired
    private ConfigItemRepository configItemRepository;

    @Autowired
    private ConfigValueRepository configValueRepository;

    @Autowired
    private ConfigReplaceService configReplaceService;

    @Autowired
    private CollaborationService collaborationService;

    @Autowired
    private OperationAuditService operationAuditService;

    @Override
    @Transactional(readOnly = true)
    public List<ModificationWorkOrderVO> findAll() {
        return workOrderRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<ModificationWorkOrderVO> findPage(String keyword, Integer page, Integer size) {
        int normalizedPage = ListPageSupport.page(page);
        int normalizedSize = ListPageSupport.size(size);
        Page<ModificationWorkOrder> result = workOrderRepository.searchPage(
                normalizeKeyword(keyword),
                PageRequest.of(normalizedPage, normalizedSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return PageResult.of(
                result.getContent().stream().map(this::toVO).toList(),
                normalizedPage,
                normalizedSize,
                result.getTotalElements()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<ModificationWorkOrderVO> findByMachineId(Long machineId) {
        return workOrderRepository.findByMachineIdOrderByCreatedAtDesc(machineId).stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ModificationWorkOrderVO findById(Long id) {
        return toVO(workOrderRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Modification work order not found")));
    }

    @Override
    @Transactional
    public ModificationWorkOrderVO create(ModificationWorkOrderCreateDTO request) {
        MachineInventory machine = machineRepository.findByIdForUpdate(request.getMachineId())
                .orElseThrow(() -> new BusinessException(ResultCode.VEHICLE_NOT_FOUND, "Vehicle not found"));
        collaborationService.validateWrite(machine, request.getMachineVersion());
        int machineQuantity = machine.getInventoryCount() == null ? 0 : machine.getInventoryCount();
        if (machineQuantity < 1) {
            throw new BusinessException(ResultCode.INSUFFICIENT_STOCK, "Vehicle is not in stock and cannot create modification work order");
        }

        Map<Long, Integer> requestedPartQuantities = new HashMap<>();
        Map<Long, PartInventory> parts = new HashMap<>();
        List<ModificationWorkOrderLine> preparedLines = request.getLines().stream()
                .map(lineRequest -> prepareLine(machine, lineRequest, requestedPartQuantities, parts))
                .toList();
        requestedPartQuantities.forEach((partId, quantity) -> {
            PartInventory part = parts.get(partId);
            int available = part.getQuantity() == null ? 0 : part.getQuantity();
            if (available < quantity) {
                throw new BusinessException(ResultCode.INSUFFICIENT_STOCK,
                        "闁板秳娆㈡惔鎾崇摠娑撳秷鍐? " + part.getPartName() + "閿涘矂娓剁憰?" + quantity + "閿涘苯缍嬮崜?" + available);
            }
        });

        ModificationWorkOrder workOrder = new ModificationWorkOrder();
        workOrder.setWorkOrderNo(nextWorkOrderNo());
        workOrder.setMachineId(machine.getId());
        workOrder.setCustomerName(blankToNull(request.getCustomerName()));
        workOrder.setSalesOrderNo(blankToNull(request.getSalesOrderNo()));
        workOrder.setOperator(blankToNull(request.getOperator()));
        workOrder.setRemark(blankToNull(request.getRemark()));
        workOrder.setStatus(ModificationWorkOrderStatuses.WAITING_PARTS);
        ModificationWorkOrder savedOrder = workOrderRepository.saveAndFlush(workOrder);

        preparedLines.forEach(line -> line.setWorkOrderId(savedOrder.getId()));
        lineRepository.saveAllAndFlush(preparedLines);

        machine.setStockStatus(MachineStockStatuses.PENDING_MODIFICATION);
        collaborationService.stampWrite(machine);
        machineRepository.saveAndFlush(machine);

        operationAuditService.record("Modification work order", "CREATE", "MODIFICATION_WORK_ORDER", savedOrder.getId(),
                savedOrder.getWorkOrderNo(), "鏉烇箒绶營D " + savedOrder.getMachineId(),
                "Create modification work order lines: " + preparedLines.size(), request.getOperator(), request.getRemark(),
                MOVEMENT_SOURCE_TYPE, savedOrder.getId());
        return toVO(savedOrder);
    }

    @Override
    @Transactional
    public ModificationWorkOrderVO complete(Long id, ModificationWorkOrderActionDTO request) {
        ModificationWorkOrder workOrder = workOrderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Modification work order not found"));
        validateVersion(workOrder.getVersion(), request == null ? null : request.getVersion(), "Modification work order was updated by another user");
        if (ModificationWorkOrderStatuses.COMPLETED.equals(workOrder.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "Modification work order is already completed");
        }
        if (ModificationWorkOrderStatuses.CANCELED.equals(workOrder.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "Modification work order is already completed");
        }

        List<ModificationWorkOrderLine> lines = lineRepository.findByWorkOrderIdOrderByIdAsc(workOrder.getId());
        if (lines.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "閺€纭咁棅瀹搞儱宕熷▽鈩冩箒閺囨寧宕查弰搴ｇ矎");
        }

        MachineInventory machine = machineRepository.findByIdForUpdate(workOrder.getMachineId())
                .orElseThrow(() -> new BusinessException(ResultCode.VEHICLE_NOT_FOUND, "Vehicle not found"));
        machine.setStockStatus(MachineStockStatuses.MODIFYING);
        collaborationService.stampWrite(machine);
        machineRepository.saveAndFlush(machine);

        workOrder.setStatus(ModificationWorkOrderStatuses.IN_PROGRESS);
        workOrderRepository.saveAndFlush(workOrder);

        String operator = firstNonBlank(request == null ? null : request.getOperator(), workOrder.getOperator());
        String actionRemark = request == null ? null : request.getRemark();
        for (ModificationWorkOrderLine line : lines) {
            if (isDiscountLine(line)) {
                applyDiscountLine(workOrder, line, operator, actionRemark);
            } else {
                ResolvedLine resolvedLine = resolveLineForExecution(workOrder.getMachineId(), line);
            PartReplaceRequestDTO replaceRequest = new PartReplaceRequestDTO();
            replaceRequest.setMachineId(workOrder.getMachineId());
            replaceRequest.setMachineVersion(resolvedLine.machine().getVersion());
            replaceRequest.setMachineConfigId(line.getMachineConfigId());
            replaceRequest.setMachineConfigVersion(resolvedLine.config().getVersion());
            replaceRequest.setNewPartId(line.getNewPartId());
            replaceRequest.setNewPartVersion(resolvedLine.part().getVersion());
            replaceRequest.setQuantity(line.getQuantity());
            replaceRequest.setOldPartAction(line.getOldPartAction());
            replaceRequest.setOperator(operator);
            replaceRequest.setRemark(joinRemark("閺€纭咁棅瀹搞儱宕?" + workOrder.getWorkOrderNo(), line.getRemark(), actionRemark));
            replaceRequest.setStockMovementSourceType(MOVEMENT_SOURCE_TYPE);
            replaceRequest.setStockMovementSourceId(workOrder.getId());
            ConfigReplaceLog replaceLog = configReplaceService.performPartReplace(replaceRequest);
            line.setReplaceLogId(replaceLog.getId());
            }
            lineRepository.save(line);
        }

        workOrder.setStatus(ModificationWorkOrderStatuses.COMPLETED);
        workOrder.setCompletedAt(LocalDateTime.now());
        if (operator != null) {
            workOrder.setOperator(operator);
        }
        if (actionRemark != null && !actionRemark.isBlank()) {
            workOrder.setRemark(joinRemark(workOrder.getRemark(), actionRemark));
        }
        ModificationWorkOrder savedOrder = workOrderRepository.saveAndFlush(workOrder);

        MachineInventory completedMachine = machineRepository.findByIdForUpdate(workOrder.getMachineId())
                .orElseThrow(() -> new BusinessException(ResultCode.VEHICLE_NOT_FOUND, "Vehicle not found"));
        completedMachine.setStockStatus(MachineStockStatuses.PENDING_OUTBOUND);
        collaborationService.stampWrite(completedMachine);
        machineRepository.saveAndFlush(completedMachine);

        operationAuditService.record("Modification work order", "COMPLETE", "MODIFICATION_WORK_ORDER", savedOrder.getId(),
                savedOrder.getWorkOrderNo(), "鏉烇箒绶營D " + savedOrder.getMachineId(),
                "Complete modification work order lines: " + lines.size(), operator, actionRemark,
                MOVEMENT_SOURCE_TYPE, savedOrder.getId());
        return toVO(savedOrder);
    }

    @Override
    @Transactional
    public ModificationWorkOrderVO cancel(Long id, ModificationWorkOrderActionDTO request) {
        ModificationWorkOrder workOrder = workOrderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Modification work order not found"));
        validateVersion(workOrder.getVersion(), request == null ? null : request.getVersion(), "Modification work order was updated by another user");
        if (ModificationWorkOrderStatuses.COMPLETED.equals(workOrder.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "Completed modification work order cannot be canceled");
        }
        if (ModificationWorkOrderStatuses.CANCELED.equals(workOrder.getStatus())) {
            return toVO(workOrder);
        }
        workOrder.setStatus(ModificationWorkOrderStatuses.CANCELED);
        workOrder.setCanceledAt(LocalDateTime.now());
        String operator = request == null ? null : request.getOperator();
        String remark = request == null ? null : request.getRemark();
        if (operator != null && !operator.isBlank()) {
            workOrder.setOperator(operator);
        }
        if (remark != null && !remark.isBlank()) {
            workOrder.setRemark(joinRemark(workOrder.getRemark(), remark));
        }
        ModificationWorkOrder savedOrder = workOrderRepository.saveAndFlush(workOrder);
        restoreMachineStatusIfNoActiveOrder(savedOrder.getMachineId(), savedOrder.getId());

        operationAuditService.record("Modification work order", "CANCEL", "MODIFICATION_WORK_ORDER", savedOrder.getId(),
                savedOrder.getWorkOrderNo(), "鏉烇箒绶營D " + savedOrder.getMachineId(),
                "Cancel modification work order", operator, remark, MOVEMENT_SOURCE_TYPE, savedOrder.getId());
        return toVO(savedOrder);
    }

    private ModificationWorkOrderLine prepareLine(
            MachineInventory machine,
            ModificationWorkOrderCreateDTO.Line lineRequest,
            Map<Long, Integer> requestedPartQuantities,
            Map<Long, PartInventory> parts
    ) {
        MachineConfig config = machineConfigRepository.findByIdForUpdate(lineRequest.getMachineConfigId())
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Machine config not found"));
        validateVersion(config.getVersion(), lineRequest.getMachineConfigVersion(), "Machine config was updated by another user");
        if (!config.getMachineId().equals(machine.getId())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Machine config does not belong to this vehicle");
        }
        String oldPartAction = blankToDefault(lineRequest.getOldPartAction(), PartChangeActions.STOCK_IN);
        if (PartChangeActions.DISCOUNT.equals(oldPartAction)) {
            int discountQuantity = lineRequest.getQuantity() == null ? 1 : lineRequest.getQuantity();
            if (discountQuantity < 1) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "Quantity must be greater than 0");
            }
            if (lineRequest.getNewConfigValueId() == null) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "Target config value is required");
            }
            ConfigValue value = configValueRepository.findByIdForUpdate(lineRequest.getNewConfigValueId())
                    .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Target config value not found"));
            validateVersion(value.getVersion(), lineRequest.getNewConfigValueVersion(), "Config value was updated by another user");
            ensureSameConfigItem(config, value);

            ModificationWorkOrderLine line = new ModificationWorkOrderLine();
            line.setMachineConfigId(config.getId());
            line.setConfigItemId(config.getConfigItemId());
            line.setItemName(config.getItemName());
            line.setOldValue(config.getSelectedValue());
            line.setNewConfigValueId(value.getId());
            line.setNewPartName(value.getValueLabel());
            line.setNewValue(value.getValueLabel());
            line.setQuantity(discountQuantity);
            line.setOldPartAction(oldPartAction);
            line.setPriceDifference(amountOrZero(lineRequest.getPriceDifference()));
            line.setRemark(blankToNull(lineRequest.getRemark()));
            return line;
        }
        if (lineRequest.getNewPartId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "New part is required");
        }
        PartInventory part = partRepository.findByIdForUpdate(lineRequest.getNewPartId())
                .orElseThrow(() -> new BusinessException(ResultCode.PART_NOT_FOUND, "New part not found"));
        validateVersion(part.getVersion(), lineRequest.getNewPartVersion(), "Part inventory was updated by another user");
        ensureCompatible(config, part);

        int quantity = lineRequest.getQuantity() == null ? 1 : lineRequest.getQuantity();
        if (quantity < 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "閺囨寧宕查弫浼村櫤韫囧懘銆忔径褌绨?");
        }
        requestedPartQuantities.merge(part.getId(), quantity, Integer::sum);
        parts.put(part.getId(), part);

        ModificationWorkOrderLine line = new ModificationWorkOrderLine();
        line.setMachineConfigId(config.getId());
        line.setConfigItemId(config.getConfigItemId());
        line.setItemName(config.getItemName());
        line.setOldValue(config.getSelectedValue());
        line.setNewPartId(part.getId());
        line.setNewPartCode(part.getPartCode());
        line.setNewPartName(part.getPartName());
        line.setNewValue(partDisplayName(part));
        line.setQuantity(quantity);
        line.setOldPartAction(oldPartAction);
        line.setPriceDifference(amountOrZero(lineRequest.getPriceDifference()));
        line.setRemark(blankToNull(lineRequest.getRemark()));
        return line;
    }

    private ResolvedLine resolveLineForExecution(Long machineId, ModificationWorkOrderLine line) {
        MachineInventory machine = machineRepository.findByIdForUpdate(machineId)
                .orElseThrow(() -> new BusinessException(ResultCode.VEHICLE_NOT_FOUND, "Vehicle not found"));
        MachineConfig config = machineConfigRepository.findByIdForUpdate(line.getMachineConfigId())
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Machine config not found"));
        if (!config.getMachineId().equals(machineId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Machine config does not belong to this vehicle");
        }
        if (line.getOldValue() != null && !line.getOldValue().equals(config.getSelectedValue())) {
            throw new BusinessException(ResultCode.CONFLICT,
                    "闁板秶鐤嗗鎻掑綁閸栨牭绱濇稉宥堝厴閹稿妫銉ュ礋鐎瑰本鍨? " + line.getItemName());
        }
        PartInventory part = partRepository.findByIdForUpdate(line.getNewPartId())
                .orElseThrow(() -> new BusinessException(ResultCode.PART_NOT_FOUND, "New part not found"));
        ensureCompatible(config, part);
        return new ResolvedLine(machine, config, part);
    }

    private record ResolvedLine(MachineInventory machine, MachineConfig config, PartInventory part) {
    }

    private void applyDiscountLine(
            ModificationWorkOrder workOrder,
            ModificationWorkOrderLine line,
            String operator,
            String actionRemark
    ) {
        MachineConfig config = machineConfigRepository.findByIdForUpdate(line.getMachineConfigId())
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Machine config not found"));
        if (!config.getMachineId().equals(workOrder.getMachineId())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Machine config does not belong to this vehicle");
        }
        if (line.getOldValue() != null && !line.getOldValue().equals(config.getSelectedValue())) {
            throw new BusinessException(ResultCode.CONFLICT,
                    "Vehicle config has changed before discount replacement: " + line.getItemName());
        }
        ConfigValue value = configValueRepository.findByIdForUpdate(line.getNewConfigValueId())
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Target config value not found"));
        ensureSameConfigItem(config, value);

        config.setConfigValueId(value.getId());
        config.setSelectedValue(value.getValueLabel());
        config.setConfigSource("DISCOUNT");
        config.setInstalledDate(LocalDateTime.now());
        config.setRemark(joinRemark(config.getRemark(),
                "旧件折价改装；工单=" + workOrder.getWorkOrderNo(),
                "新件差价=" + amountOrZero(line.getPriceDifference()),
                line.getRemark(),
                actionRemark));
        collaborationService.stampWrite(config);
        machineConfigRepository.saveAndFlush(config);

        line.setNewConfigValueId(value.getId());
        line.setNewPartName(value.getValueLabel());
        line.setNewValue(value.getValueLabel());
        line.setReplaceLogId(null);

        operationAuditService.record("Modification work order", "DISCOUNT_REPLACE", "MODIFICATION_WORK_ORDER", workOrder.getId(),
                workOrder.getWorkOrderNo(), "Discount replacement " + workOrder.getMachineId(),
                "Discount line amount: " + amountOrZero(line.getPriceDifference()), operator, actionRemark,
                MOVEMENT_SOURCE_TYPE, workOrder.getId());
    }

    private void restoreMachineStatusIfNoActiveOrder(Long machineId, Long canceledOrderId) {
        boolean hasActive = workOrderRepository.findByMachineIdOrderByCreatedAtDesc(machineId).stream()
                .anyMatch(order -> !order.getId().equals(canceledOrderId)
                        && !ModificationWorkOrderStatuses.COMPLETED.equals(order.getStatus())
                        && !ModificationWorkOrderStatuses.CANCELED.equals(order.getStatus()));
        if (hasActive) {
            return;
        }
        machineRepository.findByIdForUpdate(machineId).ifPresent(machine -> {
            machine.setStockStatus(MachineStockStatuses.IN_STOCK);
            collaborationService.stampWrite(machine);
            machineRepository.saveAndFlush(machine);
        });
    }

    private void ensureCompatible(MachineConfig config, PartInventory part) {
        String actualType = normalizeType(part.getPartCategory());
        List<String> expectedTypes = configItemRepository.findById(config.getConfigItemId())
                .map(item -> List.of(normalizeType(item.getSubCategory()), normalizeType(item.getItemName()), normalizeType(config.getItemName())))
                .orElseGet(() -> List.of(normalizeType(config.getItemName())));
        boolean matched = !actualType.isEmpty() && expectedTypes.stream()
                .filter(value -> value != null && !value.isBlank())
                .anyMatch(value -> value.equals(actualType));
        if (!matched) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "閸欘亣鍏橀弴鎸庡床閸氬瞼琚崹瀣帳娴犺绱版潪锕佺窢闁板秶鐤嗙猾璇茬€?" + config.getItemName()
                            + "閿涘苯绨辩€涙﹢鍘ゆ禒鍓佽閸?" + part.getPartCategory());
        }
    }

    private void ensureSameConfigItem(MachineConfig config, ConfigValue value) {
        if (value == null || value.getConfigItemId() == null || !value.getConfigItemId().equals(config.getConfigItemId())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Target config value must belong to the same config item");
        }
    }

    private boolean isDiscountLine(ModificationWorkOrderLine line) {
        return PartChangeActions.DISCOUNT.equals(line.getOldPartAction());
    }

    private BigDecimal amountOrZero(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value;
    }

    private ModificationWorkOrderVO toVO(ModificationWorkOrder workOrder) {
        MachineInventory machine = workOrder.getMachineId() == null
                ? null
                : machineRepository.findById(workOrder.getMachineId()).orElse(null);
        return ModificationWorkOrderVO.fromEntity(
                workOrder,
                lineRepository.findByWorkOrderIdOrderByIdAsc(workOrder.getId()),
                machine
        );
    }

    private void validateVersion(Long currentVersion, Long requestVersion, String message) {
        if (requestVersion != null && currentVersion != null && !currentVersion.equals(requestVersion)) {
            throw new BusinessException(ResultCode.CONFLICT, message);
        }
    }

    private String nextWorkOrderNo() {
        String suffix = UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 6)
                .toUpperCase(Locale.ROOT);
        return "MO-" + ORDER_NO_TIME.format(LocalDateTime.now()) + "-" + suffix;
    }

    private String partDisplayName(PartInventory part) {
        if (part.getSpecification() == null || part.getSpecification().isBlank()) {
            return part.getPartName();
        }
        return part.getPartName() + " / " + part.getSpecification();
    }

    private String normalizeType(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return blankToNull(second);
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
}
