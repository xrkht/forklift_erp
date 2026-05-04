// src/main/java/com/example/forklift_erp/service/ConfigReplaceService.java
package com.example.forklift_erp.service;

import com.example.forklift_erp.dto.ConfigReplaceRequestDTO;
import com.example.forklift_erp.entity.ConfigReplaceLog;

public interface ConfigReplaceService {
    /**
     * 执行一次配置替换（包含更新车辆配置、库存变动、日志记录）
     * @param request 替换请求
     * @return 替换日志记录
     */
    ConfigReplaceLog performReplace(ConfigReplaceRequestDTO request);
}