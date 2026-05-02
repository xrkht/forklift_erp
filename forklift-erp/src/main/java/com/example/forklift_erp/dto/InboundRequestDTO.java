// src/main/java/com/example/forklift_erp/dto/InboundRequestDTO.java
package com.example.forklift_erp.dto;

import com.example.forklift_erp.entity.MachineInventory;
import lombok.Data;
import java.util.List;

/**
 * 入库请求 DTO
 * 包含车辆基本信息和配置明细
 */
@Data
public class InboundRequestDTO {
    // 车辆基本信息
    private MachineInventory machineInventory;
    // 配置明细
    private List<ConfigSelection> configs;
    // 配置项选择
    @Data
    public static class ConfigSelection {
        // 配置项ID（来自 config_item 表）
        private Long configItemId;
        // 配置值ID（来自 config_value 表）
        private Long configValueId;
        // 配置项名称
        private String itemName;
        // 用户选择的值
        private String selectedValue;
        // 是否是原厂配置
        private Boolean isStandard;
    }
}