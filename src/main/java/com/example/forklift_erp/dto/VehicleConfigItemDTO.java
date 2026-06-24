package com.example.forklift_erp.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VehicleConfigItemDTO {
    private Long version;

    @NotBlank(message = "规格型号不能为空")
    private String specificationModel;

    private Integer sortOrder;
    private String remark;
}
