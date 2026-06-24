package com.example.forklift_erp.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class VehicleConfigTemplateVO {
    private Long id;
    private String specificationModel;
    private List<VehicleConfigValueVO> values = new ArrayList<>();

    public static VehicleConfigTemplateVO empty(String specificationModel) {
        VehicleConfigTemplateVO vo = new VehicleConfigTemplateVO();
        vo.setSpecificationModel(specificationModel);
        return vo;
    }
}
