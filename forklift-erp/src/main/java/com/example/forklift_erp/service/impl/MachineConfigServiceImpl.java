// src/main/java/com/example/forklift_erp/service/impl/MachineConfigServiceImpl.java
package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.entity.MachineConfig;
import com.example.forklift_erp.repository.MachineConfigRepository;
import com.example.forklift_erp.service.MachineConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class MachineConfigServiceImpl implements MachineConfigService {

    @Autowired
    private MachineConfigRepository machineConfigRepository;

    @Override
    public List<MachineConfig> findByMachineId(Long machineId) {
        return machineConfigRepository.findByMachineId(machineId);
    }

    @Override
    @Transactional
    public List<MachineConfig> saveAll(List<MachineConfig> configs) {
        return machineConfigRepository.saveAll(configs);
    }

    @Override
    @Transactional
    public void deleteByMachineId(Long machineId) {
        machineConfigRepository.deleteByMachineId(machineId);
    }
}