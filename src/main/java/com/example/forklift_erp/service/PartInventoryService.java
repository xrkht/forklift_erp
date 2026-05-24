package com.example.forklift_erp.service;

import com.example.forklift_erp.entity.PartInventory;
import java.util.List;
import java.util.Optional;

/**
 * 配件库存业务接口
 */
public interface PartInventoryService {

    // ========== 基础 CRUD ==========
    List<PartInventory> findAll();
    Optional<PartInventory> findById(Long id);
    Optional<PartInventory> findByIdForUpdate(Long id);
    Optional<PartInventory> findByPartCode(String partCode);
    Optional<PartInventory> findByPartCodeForUpdate(String partCode);
    PartInventory save(PartInventory part);
    void deleteById(Long id);

    // ========== 扩展查询 ==========
    List<PartInventory> findByCategory(String category);
    List<PartInventory> findAvailableParts();     // 库存大于0的可用配件
    List<PartInventory> findBySource(String source);
    List<PartInventory> findBySourceMachineId(Long machineId);

    // ========== 业务操作 ==========
    /**
     * 配件入库（增加库存）
     * @param partCode 配件编码
     * @param quantity 增加的数量
     * @return 更新后的配件
     */
    PartInventory inbound(String partCode, int quantity, Long expectedVersion);

    /**
     * 配件出库（扣减库存）
     * @param partCode 配件编码
     * @param quantity 扣减的数量
     * @return 更新后的配件
     */
    PartInventory outbound(String partCode, int quantity, Long expectedVersion);
}
