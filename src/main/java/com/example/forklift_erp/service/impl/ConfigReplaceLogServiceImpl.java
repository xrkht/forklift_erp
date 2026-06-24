package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.entity.ConfigReplaceLog;
import com.example.forklift_erp.repository.ConfigReplaceLogRepository;
import com.example.forklift_erp.service.ConfigReplaceLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ConfigReplaceLogServiceImpl implements ConfigReplaceLogService {

    @Autowired
    private ConfigReplaceLogRepository configReplaceLogRepository;

    @Override
    public List<ConfigReplaceLog> findByMachineId(Long machineId) {
        return configReplaceLogRepository.findByMachineIdOrderByCreatedAtDesc(machineId);
    }

    @Override
    public ConfigReplaceLog save(ConfigReplaceLog log) {
        return configReplaceLogRepository.save(log);
    }
}