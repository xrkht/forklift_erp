package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.common.PageResult;
import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.constant.MachineStockStatus;
import com.example.forklift_erp.dto.InboundRequestDTO;
import com.example.forklift_erp.dto.MachineConfigVO;
import com.example.forklift_erp.dto.MachineInventoryCreateDTO;
import com.example.forklift_erp.dto.MachineInventoryVO;
import com.example.forklift_erp.dto.StockAdjustRequestDTO;
import com.example.forklift_erp.dto.VehicleModelSummaryVO;
import com.example.forklift_erp.entity.ConfigItem;
import com.example.forklift_erp.entity.ConfigValue;
import com.example.forklift_erp.entity.MachineConfig;
import com.example.forklift_erp.entity.MachineInventory;
import com.example.forklift_erp.entity.StockOperationLog;
import com.example.forklift_erp.exception.BusinessException;
import com.example.forklift_erp.repository.ConfigItemRepository;
import com.example.forklift_erp.repository.ConfigValueRepository;
import com.example.forklift_erp.repository.MachineInventoryRepository;
import com.example.forklift_erp.service.CollaborationService;
import com.example.forklift_erp.service.MachineConfigService;
import com.example.forklift_erp.service.MachineInventoryService;
import com.example.forklift_erp.service.OperationAuditService;
import com.example.forklift_erp.service.ResourceVisibilityPolicy;
import com.example.forklift_erp.service.StockLedgerService;
import com.example.forklift_erp.util.InventoryQuantities;
import com.example.forklift_erp.util.ListPageSupport;
import com.example.forklift_erp.util.MoneyValues;
import com.example.forklift_erp.util.SearchKeywordSupport;
import com.example.forklift_erp.util.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MachineInventoryServiceImpl implements MachineInventoryService {

    @Autowired
    private MachineInventoryRepository repository;

    @Autowired
    private CollaborationService collaborationService;

    @Autowired
    private StockLedgerService stockLedgerService;

    @Autowired
    private MachineConfigService machineConfigService;

    @Autowired
    private ConfigItemRepository configItemRepository;

    @Autowired
    private ConfigValueRepository configValueRepository;

    @Autowired
    private OperationAuditService operationAuditService;

    @Autowired
    private StockOperationRecorder stockOperationRecorder;

    @Autowired
    private ResourceVisibilityPolicy visibilityPolicy;

    @Override
    public List<MachineInventory> findAll() {
        if (SecurityUtils.isAdminOrSuperAdmin()) {
            return repository.findAll();
        } else {
            return repository.findAllByIsLockedFalse();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<MachineInventoryVO> findPage(String keyword, Integer page, Integer size) {
        int normalizedPage = ListPageSupport.page(page);
        int normalizedSize = ListPageSupport.size(size);
        Page<MachineInventory> result = repository.searchPage(
                SearchKeywordSupport.likePrefix(keyword),
                SearchKeywordSupport.fullTextBoolean(keyword),
                SecurityUtils.isAdminOrSuperAdmin(),
                ListPageSupport.pageRequest(page, size)
        );
        return PageResult.of(
                result.getContent().stream().map(MachineInventoryVO::fromEntity).toList(),
                normalizedPage,
                normalizedSize,
                result.getTotalElements()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<VehicleModelSummaryVO> findModelPage(String keyword, Integer page, Integer size) {
        int normalizedPage = ListPageSupport.page(page);
        int normalizedSize = ListPageSupport.size(size);
        Page<MachineInventoryRepository.VehicleModelSummaryProjection> result = repository.searchModelSummaries(
                SearchKeywordSupport.likePrefix(keyword),
                SearchKeywordSupport.fullTextBoolean(keyword),
                SecurityUtils.isAdminOrSuperAdmin(),
                ListPageSupport.pageRequest(page, size)
        );
        return PageResult.of(
                result.getContent().stream().map(VehicleModelSummaryVO::fromProjection).toList(),
                normalizedPage,
                normalizedSize,
                result.getTotalElements()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<MachineInventoryVO> findVehiclesByModel(String name, String specificationModel, String machineType) {
        return repository.findVehiclesByModel(
                        normalizeModelField(name),
                        normalizeModelField(specificationModel),
                        normalizeModelField(machineType),
                        SecurityUtils.isAdminOrSuperAdmin()
                ).stream()
                .map(MachineInventoryVO::fromEntity)
                .toList();
    }

    @Override
    public Optional<MachineInventory> findById(Long id) {
        if (SecurityUtils.isAdminOrSuperAdmin()) {
            return repository.findById(id);
        } else {
            return repository.findByIdAndIsLockedFalse(id);
        }
    }

    @Override
    public Optional<MachineInventory> findByIdForUpdate(Long id) {
        if (SecurityUtils.isAdminOrSuperAdmin()) {
            return repository.findByIdForUpdate(id);
        } else {
            return repository.findByIdAndIsLockedFalseForUpdate(id);
        }
    }

    @Override
    public Optional<MachineInventory> findByVehicleProductNumber(String vehicleProductNumber) {
        if (SecurityUtils.isAdminOrSuperAdmin()) {
            return repository.findByVehicleProductNumber(vehicleProductNumber);
        } else {
            return repository.findByVehicleProductNumberAndIsLockedFalse(vehicleProductNumber);
        }
    }

    @Override
    @Transactional
    public MachineInventory save(MachineInventory machineInventory) {
        boolean creating = machineInventory.getId() == null;
        // 更新操作时，检查原记录是否被锁定且当前用户非管理员
        if (machineInventory.getId() != null) {
            Optional<MachineInventory> existingOpt = findById(machineInventory.getId()); // 此处已应用角色过滤
            if (existingOpt.isPresent()) {
                MachineInventory existing = existingOpt.get();
                // 如果原记录是锁定的，且当前用户不是管理员，则拒绝更新
                visibilityPolicy.ensureWritable(existing.getIsLocked(), "该记录已被锁定，您无权修改");
            } else {
                throw new BusinessException(ResultCode.NOT_FOUND, "要更新的记录不存在");
            }
        }
        normalizeVehicleIdentity(machineInventory);
        normalizeInventoryCount(machineInventory, creating);
        if (Boolean.TRUE.equals(machineInventory.getModelOnly())) {
            machineInventory.setInventoryCount(0);
            machineInventory.setStockStatus(MachineStockStatus.PENDING_INBOUND.code());
            machineInventory.setEngineNumber(null);
            machineInventory.setFrameNumber(null);
            machineInventory.setWarrantyCardNumber(null);
            machineInventory.setInboundDate(null);
        }
        if (creating && machineInventory.getInboundDate() == null && machineInventory.getInventoryCount() > 0) {
            machineInventory.setInboundDate(java.time.LocalDateTime.now());
        }
        if (machineInventory.getWarehouseId() == null) {
            machineInventory.setWarehouseId(stockLedgerService.resolveWarehouseId(null));
        }
        if (machineInventory.getStockStatus() == null || machineInventory.getStockStatus().isBlank()) {
            machineInventory.setStockStatus(machineInventory.getInventoryCount() > 0
                    ? MachineStockStatus.IN_STOCK.code()
                    : MachineStockStatus.PENDING_INBOUND.code());
        }
        machineInventory.setPurchasePrice(MoneyValues.zeroIfNegative(machineInventory.getPurchasePrice()));
        machineInventory.setSalePrice(MoneyValues.zeroIfNegative(machineInventory.getSalePrice()));
        machineInventory.setSettlementPrice(MoneyValues.zeroIfNegative(machineInventory.getSettlementPrice()));

        collaborationService.stampWrite(machineInventory);
        MachineInventory saved = repository.save(machineInventory);
        if (!Boolean.TRUE.equals(saved.getModelOnly())) {
            stockLedgerService.syncBalance(
                    StockLedgerService.RESOURCE_MACHINE,
                    saved.getId(),
                    saved.getWarehouseId(),
                    saved.getInventoryCount()
            );
        }
        return saved;
    }

    @Override
    @Transactional
    public MachineInventoryVO create(MachineInventoryCreateDTO dto) {
        MachineInventory saved = save(dto.toEntity());
        int quantity = saved.getInventoryCount() == null ? 0 : saved.getInventoryCount();
        if (quantity > 0) {
            saveStockLog(saved, "INITIAL", quantity, 0, quantity, null, "Initial machine stock");
        }
        String summary = Boolean.TRUE.equals(saved.getModelOnly()) ? "Create machine model" : "Create machine";
        operationAuditService.record("Machine", "CREATE", "MACHINE", saved.getId(),
                saved.getVehicleProductNumber(), saved.getName(), summary, null, saved.getRemarks());
        return MachineInventoryVO.fromEntity(saved);
    }

    @Override
    @Transactional
    public MachineInventoryVO update(Long id, MachineInventoryCreateDTO dto) {
        MachineInventory machine = findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.VEHICLE_NOT_FOUND));
        collaborationService.validateWrite(machine, dto.getVersion());
        int beforeQuantity = machine.getInventoryCount() == null ? 0 : machine.getInventoryCount();
        dto.updateEntity(machine);
        MachineInventory saved = save(machine);
        int afterQuantity = saved.getInventoryCount() == null ? 0 : saved.getInventoryCount();
        if (beforeQuantity != afterQuantity) {
            saveStockLog(saved, "ADJUST", Math.abs(afterQuantity - beforeQuantity),
                    beforeQuantity, afterQuantity, null, "Machine stock adjusted from profile edit");
        }
        operationAuditService.record("Machine", "UPDATE", "MACHINE", saved.getId(),
                saved.getVehicleProductNumber(), saved.getName(), "Update machine", null, saved.getRemarks());
        return MachineInventoryVO.fromEntity(saved);
    }

    @Override
    @Transactional
    public void delete(Long id, Long version) {
        MachineInventory machine = findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.VEHICLE_NOT_FOUND));
        collaborationService.validateWrite(machine, version);
        machineConfigService.deleteByMachineId(id);
        deleteById(id);
        operationAuditService.record("Machine", "DELETE", "MACHINE", machine.getId(),
                machine.getVehicleProductNumber(), machine.getName(), "Delete machine", null, machine.getRemarks());
    }

    @Override
    @Transactional
    public void setLocked(Long id, boolean locked, Long version) {
        MachineInventory machine = findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.VEHICLE_NOT_FOUND, "Vehicle not found"));
        collaborationService.validateWrite(machine, version);
        machine.setIsLocked(locked);
        MachineInventory saved = save(machine);
        operationAuditService.record("Machine", locked ? "LOCK" : "UNLOCK", "MACHINE", saved.getId(),
                saved.getVehicleProductNumber(), saved.getName(), locked ? "Lock machine" : "Unlock machine", null, null);
    }

    @Override
    @Transactional
    public MachineInventoryVO inbound(InboundRequestDTO request) {
        MachineInventory savedMachine = save(request.getMachineInventory().toEntity());
        Long machineId = savedMachine.getId();

        if (request.getConfigs() != null && !request.getConfigs().isEmpty()) {
            List<MachineConfig> configList = new ArrayList<>();
            for (InboundRequestDTO.ConfigSelection config : request.getConfigs()) {
                ConfigItem item = configItemRepository.findById(config.getConfigItemId())
                        .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Config item not found"));
                ConfigValue value = configValueRepository.findById(config.getConfigValueId())
                        .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "Config value not found"));
                if (!item.getId().equals(value.getConfigItemId())) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "Config value does not belong to selected item");
                }
                MachineConfig mc = new MachineConfig();
                mc.setMachineId(machineId);
                mc.setConfigItemId(item.getId());
                mc.setConfigValueId(value.getId());
                mc.setItemName(item.getItemName());
                mc.setSelectedValue(value.getValueLabel());
                String configSource = config.getConfigSource() == null || config.getConfigSource().isBlank()
                        ? "FACTORY_STANDARD"
                        : config.getConfigSource();
                mc.setConfigSource(configSource);
                mc.setIsStandard(config.getIsStandard() != null
                        ? config.getIsStandard()
                        : !"FACTORY_OPTIONAL".equals(configSource));
                mc.setInstalledDate(LocalDateTime.now());
                configList.add(mc);
            }
            machineConfigService.saveAll(configList);
        }
        int quantity = savedMachine.getInventoryCount() == null ? 0 : savedMachine.getInventoryCount();
        if (quantity > 0) {
            saveStockLog(savedMachine, "INBOUND", quantity, 0, quantity, null, "Machine inbound profile created");
        }
        return MachineInventoryVO.fromEntity(savedMachine);
    }

    @Override
    @Transactional
    public MachineInventoryVO inboundStock(Long id, StockAdjustRequestDTO request) {
        return MachineInventoryVO.fromEntity(adjustStock(id, request, true));
    }

    @Override
    @Transactional
    public MachineInventoryVO outboundStock(Long id, StockAdjustRequestDTO request) {
        return MachineInventoryVO.fromEntity(adjustStock(id, request, false));
    }

    @Override
    @Transactional
    public List<MachineConfigVO> updateConfigs(Long id, Long version, List<MachineConfigVO> configVOs) {
        MachineInventory machine = findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.VEHICLE_NOT_FOUND));
        collaborationService.validateWrite(machine, version);
        List<MachineConfig> configs = configVOs.stream().map(vo -> {
            MachineConfig mc = new MachineConfig();
            mc.setMachineId(id);
            mc.setConfigItemId(vo.getConfigItemId());
            mc.setConfigValueId(vo.getConfigValueId());
            mc.setItemName(vo.getItemName());
            mc.setSelectedValue(vo.getSelectedValue());
            mc.setIsStandard(vo.getIsStandard());
            mc.setConfigSource(vo.getConfigSource() != null ? vo.getConfigSource() : "FACTORY");
            mc.setInstalledDate(vo.getInstalledDate() != null ? vo.getInstalledDate() : LocalDateTime.now());
            mc.setRemark(vo.getRemark());
            return mc;
        }).collect(Collectors.toList());

        machineConfigService.deleteByMachineId(id);
        List<MachineConfig> saved = machineConfigService.saveAll(configs);
        save(machine);
        List<MachineConfigVO> result = saved.stream().map(MachineConfigVO::fromEntity).collect(Collectors.toList());
        operationAuditService.record("Machine config", "CONFIG_UPDATE", "MACHINE", id,
                null, "Machine ID " + id, "Update machine configs: " + result.size(), null, null);
        return result;
    }

    private void normalizeVehicleIdentity(MachineInventory machineInventory) {
        if (machineInventory.getModelOnly() == null) {
            machineInventory.setModelOnly(false);
        }
        String vehicleNumber = trimToNull(machineInventory.getVehicleProductNumber());
        if (vehicleNumber != null) {
            machineInventory.setVehicleProductNumber(vehicleNumber);
            return;
        }
        if (Boolean.TRUE.equals(machineInventory.getModelOnly())) {
            machineInventory.setVehicleProductNumber(generateVehicleNumber("MODEL"));
            return;
        }
        if (isManualForklift(machineInventory.getMachineType())) {
            machineInventory.setVehicleProductNumber(generateVehicleNumber("MAN"));
            return;
        }
        throw new BusinessException(ResultCode.PARAM_ERROR, "车号不能为空");
    }

    private void normalizeInventoryCount(MachineInventory machineInventory, boolean creating) {
        Integer inventoryCount = machineInventory.getInventoryCount();
        if (inventoryCount == null) {
            machineInventory.setInventoryCount(creating ? 1 : 0);
            return;
        }
        InventoryQuantities.requireNonNegative(inventoryCount, "Inventory count cannot be negative");
    }

    private boolean isManualForklift(String machineType) {
        String normalized = trimToNull(machineType);
        return normalized != null && normalized.contains("手动");
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String generateVehicleNumber(String prefix) {
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = "%s-%s".formatted(prefix, UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
            if (!repository.existsByVehicleProductNumber(candidate)) {
                return candidate;
            }
        }
        throw new BusinessException(ResultCode.PARAM_ERROR, "系统生成车号失败，请重试");
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        Optional<MachineInventory> existingOpt = findByIdForUpdate(id); // 角色过滤
        if (existingOpt.isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "车辆不存在");
        }
        MachineInventory existing = existingOpt.get();
        visibilityPolicy.ensureWritable(existing.getIsLocked(), "该记录已被锁定，您无权删除");
        repository.deleteById(id);
    }

    private MachineInventory adjustStock(Long id, StockAdjustRequestDTO request, boolean inbound) {
        MachineInventory machine = findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.VEHICLE_NOT_FOUND, "Vehicle not found"));
        Integer quantity = request.getQuantity();
        int current = machine.getInventoryCount() == null ? 0 : machine.getInventoryCount();
        collaborationService.validateWrite(machine, request.getVersion());
        InventoryQuantities.QuantityChange change = inbound
                ? InventoryQuantities.inbound(current, quantity, "Quantity must be greater than 0")
                : InventoryQuantities.outbound(current, quantity, "Quantity must be greater than 0", "Insufficient vehicle stock: ");
        machine.setInventoryCount(change.afterQuantity());
        machine.setStockStatus(change.afterQuantity() > 0 ? MachineStockStatus.IN_STOCK.code()
                : (inbound ? MachineStockStatus.PENDING_INBOUND.code() : MachineStockStatus.OUTBOUND.code()));
        if (inbound) {
            machine.setInboundDate(LocalDateTime.now());
        }
        MachineInventory saved = save(machine);
        saveStockLog(saved, inbound ? "INBOUND" : "OUTBOUND", change.quantity(),
                change.beforeQuantity(), change.afterQuantity(), request.getOperator(), request.getRemark());
        return saved;
    }

    private StockOperationLog saveStockLog(MachineInventory machine, String operationType, Integer quantity,
                                           Integer beforeQuantity, Integer afterQuantity, String operator, String remark) {
        BigDecimal unitCost = stockUnitCost(machine);
        return stockOperationRecorder.recordMachine(machine, operationType, quantity,
                beforeQuantity, afterQuantity, unitCost, operator, remark);
    }

    private BigDecimal stockUnitCost(MachineInventory machine) {
        return MoneyValues.firstNonNegativeOrZero(machine.getSettlementPrice(), machine.getPurchasePrice());
    }

    private String normalizeModelField(String value) {
        return value == null ? "" : value.trim();
    }
}
