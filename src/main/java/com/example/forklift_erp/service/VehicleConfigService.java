package com.example.forklift_erp.service;

import com.example.forklift_erp.dto.VehicleConfigItemDTO;
import com.example.forklift_erp.dto.VehicleConfigItemVO;
import com.example.forklift_erp.dto.VehicleConfigTemplateVO;
import com.example.forklift_erp.dto.VehicleConfigValueDTO;
import com.example.forklift_erp.dto.VehicleConfigValueVO;

import java.util.List;

public interface VehicleConfigService {
    List<VehicleConfigItemVO> findAllItems();

    VehicleConfigItemVO createItem(VehicleConfigItemDTO request);

    VehicleConfigItemVO updateItem(Long id, VehicleConfigItemDTO request);

    void deleteItem(Long id, Long version);

    List<VehicleConfigValueVO> findValues(Long vehicleConfigItemId);

    VehicleConfigTemplateVO findTemplateBySpecificationModel(String specificationModel);

    VehicleConfigValueVO createValue(VehicleConfigValueDTO request);

    VehicleConfigValueVO updateValue(Long id, VehicleConfigValueDTO request);

    void deleteValue(Long id, Long version);
}
