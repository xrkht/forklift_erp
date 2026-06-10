package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.dto.VehicleConfigItemDTO;
import com.example.forklift_erp.dto.VehicleConfigItemVO;
import com.example.forklift_erp.dto.VehicleConfigTemplateVO;
import com.example.forklift_erp.dto.VehicleConfigValueDTO;
import com.example.forklift_erp.dto.VehicleConfigValueVO;
import com.example.forklift_erp.entity.ConfigItem;
import com.example.forklift_erp.entity.ConfigValue;
import com.example.forklift_erp.entity.VehicleConfigItem;
import com.example.forklift_erp.entity.VehicleConfigValue;
import com.example.forklift_erp.exception.BusinessException;
import com.example.forklift_erp.repository.ConfigItemRepository;
import com.example.forklift_erp.repository.ConfigValueRepository;
import com.example.forklift_erp.repository.VehicleConfigItemRepository;
import com.example.forklift_erp.repository.VehicleConfigValueRepository;
import com.example.forklift_erp.service.CollaborationService;
import com.example.forklift_erp.service.VehicleConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class VehicleConfigServiceImpl implements VehicleConfigService {
    @Autowired
    private VehicleConfigItemRepository itemRepository;

    @Autowired
    private VehicleConfigValueRepository valueRepository;

    @Autowired
    private ConfigItemRepository configItemRepository;

    @Autowired
    private ConfigValueRepository configValueRepository;

    @Autowired
    private CollaborationService collaborationService;

    @Override
    @Transactional(readOnly = true)
    public List<VehicleConfigItemVO> findAllItems() {
        return itemRepository.findAllByOrderBySortOrderAscSpecificationModelAsc().stream()
                .map(VehicleConfigItemVO::fromEntity)
                .toList();
    }

    @Override
    @Transactional
    public VehicleConfigItemVO createItem(VehicleConfigItemDTO request) {
        VehicleConfigItem item = new VehicleConfigItem();
        copyItem(request, item);
        ensureUniqueSpecification(item.getSpecificationModel(), null);
        collaborationService.stampWrite(item);
        return VehicleConfigItemVO.fromEntity(itemRepository.saveAndFlush(item));
    }

    @Override
    @Transactional
    public VehicleConfigItemVO updateItem(Long id, VehicleConfigItemDTO request) {
        VehicleConfigItem item = itemRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "整车配置项不存在"));
        collaborationService.validateWrite(item, request.getVersion());
        copyItem(request, item);
        ensureUniqueSpecification(item.getSpecificationModel(), id);
        collaborationService.stampWrite(item);
        return VehicleConfigItemVO.fromEntity(itemRepository.saveAndFlush(item));
    }

    @Override
    @Transactional
    public void deleteItem(Long id, Long version) {
        VehicleConfigItem item = itemRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "整车配置项不存在"));
        collaborationService.validateWrite(item, version);
        valueRepository.deleteByVehicleConfigItemId(id);
        itemRepository.delete(item);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VehicleConfigValueVO> findValues(Long vehicleConfigItemId) {
        if (!itemRepository.existsById(vehicleConfigItemId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "整车配置项不存在");
        }
        return valueRepository.findByVehicleConfigItemIdOrderBySortOrderAscIdAsc(vehicleConfigItemId).stream()
                .map(this::toValueVO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public VehicleConfigTemplateVO findTemplateBySpecificationModel(String specificationModel) {
        String normalized = trimToNull(specificationModel);
        if (normalized == null) {
            return VehicleConfigTemplateVO.empty(null);
        }
        return itemRepository.findBySpecificationModel(normalized)
                .map(item -> {
                    VehicleConfigTemplateVO vo = new VehicleConfigTemplateVO();
                    vo.setId(item.getId());
                    vo.setSpecificationModel(item.getSpecificationModel());
                    vo.setValues(findValues(item.getId()));
                    return vo;
                })
                .orElseGet(() -> VehicleConfigTemplateVO.empty(normalized));
    }

    @Override
    @Transactional
    public VehicleConfigValueVO createValue(VehicleConfigValueDTO request) {
        VehicleConfigValue value = new VehicleConfigValue();
        copyValue(request, value, null);
        collaborationService.stampWrite(value);
        return toValueVO(valueRepository.saveAndFlush(value));
    }

    @Override
    @Transactional
    public VehicleConfigValueVO updateValue(Long id, VehicleConfigValueDTO request) {
        VehicleConfigValue value = valueRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "整车配置值不存在"));
        collaborationService.validateWrite(value, request.getVersion());
        copyValue(request, value, id);
        collaborationService.stampWrite(value);
        return toValueVO(valueRepository.saveAndFlush(value));
    }

    @Override
    @Transactional
    public void deleteValue(Long id, Long version) {
        VehicleConfigValue value = valueRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "整车配置值不存在"));
        collaborationService.validateWrite(value, version);
        valueRepository.delete(value);
    }

    private void copyItem(VehicleConfigItemDTO request, VehicleConfigItem item) {
        item.setSpecificationModel(requiredTrim(request.getSpecificationModel(), "规格型号不能为空"));
        item.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        item.setRemark(trimToNull(request.getRemark()));
    }

    private void copyValue(VehicleConfigValueDTO request, VehicleConfigValue value, Long currentId) {
        VehicleConfigItem vehicleItem = itemRepository.findById(request.getVehicleConfigItemId())
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "整车配置项不存在"));
        ConfigItem configItem = configItemRepository.findById(request.getConfigItemId())
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "配置项不存在"));
        ConfigValue configValue = configValueRepository.findById(request.getConfigValueId())
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "配置值不存在"));
        if (!configItem.getId().equals(configValue.getConfigItemId())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "配置值不属于所选配置项");
        }
        valueRepository.findByVehicleConfigItemIdAndConfigItemId(vehicleItem.getId(), configItem.getId())
                .filter(existing -> currentId == null || !existing.getId().equals(currentId))
                .ifPresent(existing -> {
                    throw new BusinessException(ResultCode.DATA_DUPLICATE, "该规格型号已配置此配置项");
                });

        value.setVehicleConfigItemId(vehicleItem.getId());
        value.setConfigItemId(configItem.getId());
        value.setConfigValueId(configValue.getId());
        value.setConfigItemName(configItem.getItemName());
        value.setConfigValueLabel(configValue.getValueLabel());
        value.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        value.setRemark(trimToNull(request.getRemark()));
    }

    private VehicleConfigValueVO toValueVO(VehicleConfigValue value) {
        ConfigItem configItem = configItemRepository.findById(value.getConfigItemId()).orElse(null);
        ConfigValue configValue = configValueRepository.findById(value.getConfigValueId()).orElse(null);
        return VehicleConfigValueVO.fromEntity(value, configItem, configValue);
    }

    private void ensureUniqueSpecification(String specificationModel, Long currentId) {
        itemRepository.findBySpecificationModel(specificationModel)
                .filter(existing -> currentId == null || !existing.getId().equals(currentId))
                .ifPresent(existing -> {
                    throw new BusinessException(ResultCode.DATA_DUPLICATE, "规格型号已存在: " + specificationModel);
                });
    }

    private String requiredTrim(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, message);
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
