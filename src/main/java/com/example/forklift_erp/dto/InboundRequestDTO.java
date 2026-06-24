// src/main/java/com/example/forklift_erp/dto/InboundRequestDTO.java
package com.example.forklift_erp.dto;

import com.example.forklift_erp.entity.MachineInventory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

/**
 * 入库请求 DTO
 * 包含车辆基本信息和配置明细
 */
@Data
public class InboundRequestDTO {
    // 车辆基本信息
    @NotNull(message = "车辆信息不能为空")
    @Valid
    private MachineInventoryCreateDTO machineInventory;
    // 配置明细
    @Valid
    private List<ConfigSelection> configs;
    // 配置项选择
    @Data
    public static class ConfigSelection {
        @NotNull(message = "配置项ID不能为空")
        private Long configItemId;

        @NotNull(message = "配置值ID不能为空")
        private Long configValueId;

        private String itemName;

        private String selectedValue;

        private Boolean isStandard;
        private String configSource;
    }
}
