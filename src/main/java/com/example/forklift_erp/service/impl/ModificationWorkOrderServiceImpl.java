package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.dto.ModificationWorkOrderActionDTO;
import com.example.forklift_erp.dto.ModificationWorkOrderCreateDTO;
import com.example.forklift_erp.dto.ModificationWorkOrderVO;
import com.example.forklift_erp.dto.PartReplaceRequestDTO;
import com.example.forklift_erp.entity.ConfigReplaceLog;
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
import com.example.forklift_erp.service.CollaborationService;
import com.example.forklift_erp.service.ConfigReplaceService;
import com.example.forklift_erp.service.ModificationWorkOrderService;
import com.example.forklift_erp.service.OperationAuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ModificationWorkOrderServiceImpl implements ModificationWorkOrderService {
    public static final String STATUS_WAITING_PARTS = "WAITING_PARTS";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_CANCELED = "CANCELED";

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
    public List<ModificationWorkOrderVO> findByMachineId(Long machineId) {
        return workOrderRepository.findByMachineIdOrderByCreatedAtDesc(machineId).stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ModificationWorkOrderVO findById(Long id) {
        return toVO(workOrderRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "改装工单不存在")));
    }

    @Override
    @Transactional
    public ModificationWorkOrderVO create(ModificationWorkOrderCreateDTO request) {
        MachineInventory machine = machineRepository.findByIdForUpdate(request.getMachineId())
                .orElseThrow(() -> new BusinessException(ResultCode.VEHICLE_NOT_FOUND, "车辆不存在"));
        collaborationService.validateWrite(machine, request.getMachineVersion());
        int machineQuantity = machine.getInventoryCount() == null ? 0 : machine.getInventoryCount();
        if (machineQuantity < 1) {
            throw new BusinessException(ResultCode.INSUFFICIENT_STOCK, "整车不在库，不能创建改装工单");
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
                        "配件库存不足: " + part.getPartName() + "，需要 " + quantity + "，当前 " + available);
            }
        });

        ModificationWorkOrder workOrder = new ModificationWorkOrder();
        workOrder.setWorkOrderNo(nextWorkOrderNo());
        workOrder.setMachineId(machine.getId());
        workOrder.setCustomerName(blankToNull(request.getCustomerName()));
        workOrder.setSalesOrderNo(blankToNull(request.getSalesOrderNo()));
        workOrder.setOperator(blankToNull(request.getOperator()));
        workOrder.setRemark(blankToNull(request.getRemark()));
        workOrder.setStatus(STATUS_WAITING_PARTS);
        ModificationWorkOrder savedOrder = workOrderRepository.saveAndFlush(workOrder);

        preparedLines.forEach(line -> line.setWorkOrderId(savedOrder.getId()));
        lineRepository.saveAllAndFlush(preparedLines);

        machine.setStockStatus("PENDING_MODIFICATION");
        collaborationService.stampWrite(machine);
        machineRepository.saveAndFlush(machine);

        operationAuditService.record("改装工单", "CREATE", "MODIFICATION_WORK_ORDER", savedOrder.getId(),
                savedOrder.getWorkOrderNo(), "车辆ID " + savedOrder.getMachineId(),
                "创建改装工单 " + preparedLines.size() + " 项", request.getOperator(), request.getRemark(),
                MOVEMENT_SOURCE_TYPE, savedOrder.getId());
        return toVO(savedOrder);
    }

    @Override
    @Transactional
    public ModificationWorkOrderVO complete(Long id, ModificationWorkOrderActionDTO request) {
        ModificationWorkOrder workOrder = workOrderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "改装工单不存在"));
        validateVersion(workOrder.getVersion(), request == null ? null : request.getVersion(), "改装工单已被其他人修改");
        if (STATUS_COMPLETED.equals(workOrder.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "改装工单已完成");
        }
        if (STATUS_CANCELED.equals(workOrder.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "改装工单已取消，不能完成");
        }

        List<ModificationWorkOrderLine> lines = lineRepository.findByWorkOrderIdOrderByIdAsc(workOrder.getId());
        if (lines.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "改装工单没有替换明细");
        }

        MachineInventory machine = machineRepository.findByIdForUpdate(workOrder.getMachineId())
                .orElseThrow(() -> new BusinessException(ResultCode.VEHICLE_NOT_FOUND, "车辆不存在"));
        machine.setStockStatus("MODIFYING");
        collaborationService.stampWrite(machine);
        machineRepository.saveAndFlush(machine);

        workOrder.setStatus(STATUS_IN_PROGRESS);
        workOrderRepository.saveAndFlush(workOrder);

        String operator = firstNonBlank(request == null ? null : request.getOperator(), workOrder.getOperator());
        String actionRemark = request == null ? null : request.getRemark();
        for (ModificationWorkOrderLine line : lines) {
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
            replaceRequest.setRemark(joinRemark("改装工单 " + workOrder.getWorkOrderNo(), line.getRemark(), actionRemark));
            replaceRequest.setStockMovementSourceType(MOVEMENT_SOURCE_TYPE);
            replaceRequest.setStockMovementSourceId(workOrder.getId());
            ConfigReplaceLog replaceLog = configReplaceService.performPartReplace(replaceRequest);
            line.setReplaceLogId(replaceLog.getId());
            lineRepository.save(line);
        }

        workOrder.setStatus(STATUS_COMPLETED);
        workOrder.setCompletedAt(LocalDateTime.now());
        if (operator != null) {
            workOrder.setOperator(operator);
        }
        if (actionRemark != null && !actionRemark.isBlank()) {
            workOrder.setRemark(joinRemark(workOrder.getRemark(), actionRemark));
        }
        ModificationWorkOrder savedOrder = workOrderRepository.saveAndFlush(workOrder);

        MachineInventory completedMachine = machineRepository.findByIdForUpdate(workOrder.getMachineId())
                .orElseThrow(() -> new BusinessException(ResultCode.VEHICLE_NOT_FOUND, "车辆不存在"));
        completedMachine.setStockStatus("PENDING_OUTBOUND");
        collaborationService.stampWrite(completedMachine);
        machineRepository.saveAndFlush(completedMachine);

        operationAuditService.record("改装工单", "COMPLETE", "MODIFICATION_WORK_ORDER", savedOrder.getId(),
                savedOrder.getWorkOrderNo(), "车辆ID " + savedOrder.getMachineId(),
                "完成改装工单 " + lines.size() + " 项", operator, actionRemark,
                MOVEMENT_SOURCE_TYPE, savedOrder.getId());
        return toVO(savedOrder);
    }

    @Override
    @Transactional
    public ModificationWorkOrderVO cancel(Long id, ModificationWorkOrderActionDTO request) {
        ModificationWorkOrder workOrder = workOrderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "改装工单不存在"));
        validateVersion(workOrder.getVersion(), request == null ? null : request.getVersion(), "改装工单已被其他人修改");
        if (STATUS_COMPLETED.equals(workOrder.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "改装工单已完成，不能取消");
        }
        if (STATUS_CANCELED.equals(workOrder.getStatus())) {
            return toVO(workOrder);
        }
        workOrder.setStatus(STATUS_CANCELED);
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

        operationAuditService.record("改装工单", "CANCEL", "MODIFICATION_WORK_ORDER", savedOrder.getId(),
                savedOrder.getWorkOrderNo(), "车辆ID " + savedOrder.getMachineId(),
                "取消改装工单", operator, remark, MOVEMENT_SOURCE_TYPE, savedOrder.getId());
        return toVO(savedOrder);
    }

    private ModificationWorkOrderLine prepareLine(
            MachineInventory machine,
            ModificationWorkOrderCreateDTO.Line lineRequest,
            Map<Long, Integer> requestedPartQuantities,
            Map<Long, PartInventory> parts
    ) {
        MachineConfig config = machineConfigRepository.findByIdForUpdate(lineRequest.getMachineConfigId())
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "车辆配置不存在"));
        validateVersion(config.getVersion(), lineRequest.getMachineConfigVersion(), "车辆配置已被其他人修改");
        if (!config.getMachineId().equals(machine.getId())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "车辆配置不属于该车辆");
        }
        PartInventory part = partRepository.findByIdForUpdate(lineRequest.getNewPartId())
                .orElseThrow(() -> new BusinessException(ResultCode.PART_NOT_FOUND, "新配件不存在"));
        validateVersion(part.getVersion(), lineRequest.getNewPartVersion(), "配件库存已被其他人修改");
        ensureCompatible(config, part);

        int quantity = lineRequest.getQuantity() == null ? 1 : lineRequest.getQuantity();
        if (quantity < 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "替换数量必须大于0");
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
        line.setOldPartAction(blankToDefault(lineRequest.getOldPartAction(), "STOCK_IN"));
        line.setRemark(blankToNull(lineRequest.getRemark()));
        return line;
    }

    private ResolvedLine resolveLineForExecution(Long machineId, ModificationWorkOrderLine line) {
        MachineInventory machine = machineRepository.findByIdForUpdate(machineId)
                .orElseThrow(() -> new BusinessException(ResultCode.VEHICLE_NOT_FOUND, "车辆不存在"));
        MachineConfig config = machineConfigRepository.findByIdForUpdate(line.getMachineConfigId())
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "车辆配置不存在"));
        if (!config.getMachineId().equals(machineId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "车辆配置不属于该车辆");
        }
        if (line.getOldValue() != null && !line.getOldValue().equals(config.getSelectedValue())) {
            throw new BusinessException(ResultCode.CONFLICT,
                    "配置已变化，不能按旧工单完成: " + line.getItemName());
        }
        PartInventory part = partRepository.findByIdForUpdate(line.getNewPartId())
                .orElseThrow(() -> new BusinessException(ResultCode.PART_NOT_FOUND, "新配件不存在"));
        ensureCompatible(config, part);
        return new ResolvedLine(machine, config, part);
    }

    private record ResolvedLine(MachineInventory machine, MachineConfig config, PartInventory part) {
    }

    private void restoreMachineStatusIfNoActiveOrder(Long machineId, Long canceledOrderId) {
        boolean hasActive = workOrderRepository.findByMachineIdOrderByCreatedAtDesc(machineId).stream()
                .anyMatch(order -> !order.getId().equals(canceledOrderId)
                        && !STATUS_COMPLETED.equals(order.getStatus())
                        && !STATUS_CANCELED.equals(order.getStatus()));
        if (hasActive) {
            return;
        }
        machineRepository.findByIdForUpdate(machineId).ifPresent(machine -> {
            machine.setStockStatus("IN_STOCK");
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
                    "只能替换同类型配件：车辆配置类型=" + config.getItemName()
                            + "，库存配件类型=" + part.getPartCategory());
        }
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
