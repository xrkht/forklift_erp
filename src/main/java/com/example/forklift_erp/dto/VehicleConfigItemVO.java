package com.example.forklift_erp.dto;

import com.example.forklift_erp.entity.VehicleConfigItem;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class VehicleConfigItemVO {
    private Long id;
    private Long version;
    private String specificationModel;
    private Integer sortOrder;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static VehicleConfigItemVO fromEntity(VehicleConfigItem entity) {
        VehicleConfigItemVO vo = new VehicleConfigItemVO();
        vo.setId(entity.getId());
        vo.setVersion(entity.getVersion());
        vo.setSpecificationModel(entity.getSpecificationModel());
        vo.setSortOrder(entity.getSortOrder());
        vo.setRemark(entity.getRemark());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }
}
