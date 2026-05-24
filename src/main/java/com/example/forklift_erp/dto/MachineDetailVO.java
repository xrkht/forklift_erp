// src/main/java/com/example/forklift_erp/dto/MachineDetailVO.java
package com.example.forklift_erp.dto;

import lombok.Data;
import java.util.List;

@Data
public class MachineDetailVO {
    private MachineInventoryVO machine;
    private List<MachineConfigVO> configs;
}