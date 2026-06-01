package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.entity.OutboundOrder;
import com.example.forklift_erp.repository.MachineInventoryRepository;
import com.example.forklift_erp.repository.OutboundOrderRepository;
import com.example.forklift_erp.repository.PartInventoryRepository;
import com.example.forklift_erp.service.CollaborationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OutboundResourceLockService {

    @Autowired
    private OutboundOrderRepository outboundOrderRepository;

    @Autowired
    private MachineInventoryRepository machineRepository;

    @Autowired
    private PartInventoryRepository partRepository;

    @Autowired
    private CollaborationService collaborationService;

    void lockRelatedResource(OutboundOrder order) {
        boolean previouslyOrderLocked = Boolean.TRUE.equals(order.getResourceLockedByOrder());
        if (order.getResourceId() == null) {
            order.setResourceLockedByOrder(false);
            return;
        }
        if (OutboundOrder.RESOURCE_MACHINE.equals(order.getResourceType())) {
            machineRepository.findByIdForUpdate(order.getResourceId()).ifPresent(machine -> {
                boolean alreadyLocked = Boolean.TRUE.equals(machine.getIsLocked());
                machine.setIsLocked(true);
                order.setResourceLockedByOrder(previouslyOrderLocked || !alreadyLocked);
                collaborationService.stampWrite(machine);
                machineRepository.saveAndFlush(machine);
            });
            return;
        }
        if (OutboundOrder.RESOURCE_PART.equals(order.getResourceType())) {
            partRepository.findByIdForUpdate(order.getResourceId()).ifPresent(part -> {
                boolean alreadyLocked = Boolean.TRUE.equals(part.getIsLocked());
                part.setIsLocked(true);
                order.setResourceLockedByOrder(previouslyOrderLocked || !alreadyLocked);
                collaborationService.stampWrite(part);
                partRepository.saveAndFlush(part);
            });
        }
    }

    void releaseRelatedResource(OutboundOrder order) {
        if (!Boolean.TRUE.equals(order.getResourceLockedByOrder()) || order.getResourceId() == null) {
            order.setResourceLockedByOrder(false);
            return;
        }
        if (hasOtherLockedOrderForResource(order)) {
            order.setResourceLockedByOrder(false);
            return;
        }
        if (OutboundOrder.RESOURCE_MACHINE.equals(order.getResourceType())) {
            machineRepository.findByIdForUpdate(order.getResourceId()).ifPresent(machine -> {
                machine.setIsLocked(false);
                collaborationService.stampWrite(machine);
                machineRepository.saveAndFlush(machine);
            });
        } else if (OutboundOrder.RESOURCE_PART.equals(order.getResourceType())) {
            partRepository.findByIdForUpdate(order.getResourceId()).ifPresent(part -> {
                part.setIsLocked(false);
                collaborationService.stampWrite(part);
                partRepository.saveAndFlush(part);
            });
        }
        order.setResourceLockedByOrder(false);
    }

    private boolean hasOtherLockedOrderForResource(OutboundOrder order) {
        return outboundOrderRepository.existsByResourceTypeAndResourceIdAndIsLockedTrueAndIdNot(
                order.getResourceType(),
                order.getResourceId(),
                order.getId()
        );
    }
}
