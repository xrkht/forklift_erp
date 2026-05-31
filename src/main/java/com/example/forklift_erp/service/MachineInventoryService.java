package com.example.forklift_erp.service;

import com.example.forklift_erp.common.PageResult;
import com.example.forklift_erp.dto.InboundRequestDTO;
import com.example.forklift_erp.dto.MachineConfigVO;
import com.example.forklift_erp.dto.MachineInventoryCreateDTO;
import com.example.forklift_erp.dto.MachineInventoryVO;
import com.example.forklift_erp.dto.StockAdjustRequestDTO;
import com.example.forklift_erp.entity.MachineInventory;

import java.util.List;
import java.util.Optional;

public interface MachineInventoryService {
    List<MachineInventory> findAll();

    PageResult<MachineInventoryVO> findPage(String keyword, Integer page, Integer size);

    Optional<MachineInventory> findById(Long id);

    Optional<MachineInventory> findByIdForUpdate(Long id);

    MachineInventory save(MachineInventory machineInventory);

    void deleteById(Long id);

    Optional<MachineInventory> findByVehicleProductNumber(String vehicleProductNumber);

    MachineInventoryVO create(MachineInventoryCreateDTO dto);

    MachineInventoryVO update(Long id, MachineInventoryCreateDTO dto);

    void delete(Long id, Long version);

    void setLocked(Long id, boolean locked, Long version);

    MachineInventoryVO inbound(InboundRequestDTO request);

    MachineInventoryVO inboundStock(Long id, StockAdjustRequestDTO request);

    MachineInventoryVO outboundStock(Long id, StockAdjustRequestDTO request);

    List<MachineConfigVO> updateConfigs(Long id, Long version, List<MachineConfigVO> configVOs);
}
