// src/main/java/com/example/forklift_erp/service/ConfigReplaceService.java
package com.example.forklift_erp.service;

import com.example.forklift_erp.dto.ConfigReplaceRequestDTO;
import com.example.forklift_erp.dto.PartReplaceRequestDTO;
import com.example.forklift_erp.dto.VehiclePartInstallRequestDTO;
import com.example.forklift_erp.entity.ConfigReplaceLog;

public interface ConfigReplaceService {
    /**
     * 执行一次配置替换（包含更新车辆配置、库存变动、日志记录）
     * @param request 替换请求
     * @return 替换日志记录
     */
    ConfigReplaceLog performReplace(ConfigReplaceRequestDTO request);

    /**
     * 执行一次整车配件替换，并将拆下来的旧件自动入配件仓库。
     */
    ConfigReplaceLog performPartReplace(PartReplaceRequestDTO request);

    /**
     * 从配件仓库领取配件并新增安装到整车配置。
     */
    ConfigReplaceLog performPartInstall(VehiclePartInstallRequestDTO request);
}
