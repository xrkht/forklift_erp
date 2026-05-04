package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.entity.PartInventory;
import com.example.forklift_erp.exception.BusinessException;
import com.example.forklift_erp.repository.PartInventoryRepository;
import com.example.forklift_erp.service.PartInventoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class PartInventoryServiceImpl implements PartInventoryService {

    @Autowired
    private PartInventoryRepository partRepository;

    @Override
    public List<PartInventory> findAll() {
        return partRepository.findAll();
    }

    @Override
    public Optional<PartInventory> findById(Long id) {
        return partRepository.findById(id);
    }

    @Override
    public Optional<PartInventory> findByPartCode(String partCode) {
        return partRepository.findByPartCode(partCode);
    }

    @Override
    @Transactional
    public PartInventory save(PartInventory part) {
        // 新增时检查编码唯一性
        if (part.getId() == null) {
            Optional<PartInventory> exist = partRepository.findByPartCode(part.getPartCode());
            if (exist.isPresent()) {
                throw new BusinessException(ResultCode.DATA_DUPLICATE, "配件编码已存在: " + part.getPartCode());
            }
        } else {
            // 更新时检查编码是否与其他记录冲突
            Optional<PartInventory> exist = partRepository.findByPartCode(part.getPartCode());
            if (exist.isPresent() && !exist.get().getId().equals(part.getId())) {
                throw new BusinessException(ResultCode.DATA_DUPLICATE, "配件编码已被其他配件使用: " + part.getPartCode());
            }
        }
        if (part.getQuantity() == null) {
            part.setQuantity(0);
        }
        log.info("保存配件: partCode={}, name={}, quantity={}", part.getPartCode(), part.getPartName(), part.getQuantity());
        return partRepository.save(part);
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        if (!partRepository.existsById(id)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "配件不存在，id=" + id);
        }
        partRepository.deleteById(id);
        log.info("删除配件: id={}", id);
    }

    @Override
    public List<PartInventory> findByCategory(String category) {
        return partRepository.findByPartCategory(category);
    }

    @Override
    public List<PartInventory> findAvailableParts() {
        return partRepository.findByQuantityGreaterThan(0);
    }

    @Override
    public List<PartInventory> findBySource(String source) {
        return partRepository.findBySource(source);
    }

    @Override
    public List<PartInventory> findBySourceMachineId(Long machineId) {
        return partRepository.findBySourceMachineId(machineId);
    }

    @Override
    @Transactional
    public PartInventory inbound(String partCode, int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "入库数量必须大于0");
        }
        PartInventory part = partRepository.findByPartCode(partCode)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "配件编码不存在: " + partCode));
        part.setQuantity(part.getQuantity() + quantity);
        part.setInboundDate(LocalDateTime.now());
        log.info("配件入库: partCode={}, 增加数量={}, 当前库存={}", partCode, quantity, part.getQuantity());
        return partRepository.save(part);
    }

    @Override
    @Transactional
    public PartInventory outbound(String partCode, int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "出库数量必须大于0");
        }
        PartInventory part = partRepository.findByPartCode(partCode)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "配件编码不存在: " + partCode));
        if (part.getQuantity() < quantity) {
            throw new BusinessException(ResultCode.INSUFFICIENT_STOCK, "库存不足，当前库存：" + part.getQuantity() + "，需要出库：" + quantity);
        }
        part.setQuantity(part.getQuantity() - quantity);
        log.info("配件出库: partCode={}, 减少数量={}, 剩余库存={}", partCode, quantity, part.getQuantity());
        return partRepository.save(part);
    }
}