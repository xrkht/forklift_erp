// src/main/java/com/example/forklift_erp/dto/ConfigReplaceRequestDTO.java
package com.example.forklift_erp.dto;

import lombok.Data;

/**
 * 配置替换请求 DTO
 * 描述一次替换需要的信息
 */
@Data
public class ConfigReplaceRequestDTO {
    // 车辆ID
    private Long machineId;
    // 配置项ID
    private Long configItemId;
    // 旧配置记录ID（如果已知，可直接提供；否则系统根据 machineId + configItemId 查找）
    private Long oldConfigId;
    // 新配置值ID（来自于 config_value 表）
    private Long newConfigValueId;
    // 新配置值显示名称（冗余）
    private String newValueLabel;
    // 替换类型：SWAP / UPGRADE / REPAIR
    private String replaceType;
    // 新配件ID（如果是从配件库存中更换）
    private Long newPartId;
    // 旧件处理方式：STOCK_IN 表示旧件入库，DISCARD 表示丢弃
    private String oldPartAction;
    // 操作人
    private String operator;
    // 备注
    private String remark;
}