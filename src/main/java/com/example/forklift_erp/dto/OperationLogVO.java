package com.example.forklift_erp.dto;

import com.example.forklift_erp.entity.ConfigReplaceLog;
import com.example.forklift_erp.entity.OperationAuditLog;
import com.example.forklift_erp.entity.RepairRecord;
import com.example.forklift_erp.entity.StockOperationLog;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OperationLogVO {
    private String id;
    private String category;
    private String action;
    private String target;
    private String summary;
    private String operator;
    private Integer quantity;
    private Integer beforeQuantity;
    private Integer afterQuantity;
    private String remark;
    private LocalDateTime createdAt;

    public static OperationLogVO fromAuditLog(OperationAuditLog log) {
        OperationLogVO vo = new OperationLogVO();
        vo.setId("audit-" + log.getId());
        vo.setCategory(log.getModule());
        vo.setAction(log.getAction());
        vo.setTarget(joinTarget(log.getTargetCode(), log.getTargetName()));
        vo.setSummary(log.getSummary());
        vo.setOperator(log.getOperator());
        vo.setRemark(log.getRemark());
        vo.setCreatedAt(log.getCreatedAt());
        return vo;
    }

    public static OperationLogVO fromReplaceLog(ConfigReplaceLog log) {
        OperationLogVO vo = new OperationLogVO();
        vo.setId("replace-" + log.getId());
        vo.setCategory("配件替换");
        vo.setAction(log.getReplaceType());
        vo.setTarget(log.getItemName());
        vo.setSummary(display(log.getOldValue()) + " -> " + display(log.getNewValue()));
        vo.setOperator(log.getOperator());
        vo.setRemark(log.getRemark());
        vo.setCreatedAt(log.getCreatedAt());
        return vo;
    }

    public static OperationLogVO fromRepairRecord(RepairRecord record) {
        OperationLogVO vo = new OperationLogVO();
        vo.setId("repair-" + record.getId());
        vo.setCategory("维修");
        vo.setAction(record.getStatus());
        vo.setTarget(display(record.getVehicleNumber()) + " / " + display(record.getCustomerName()));
        vo.setSummary(record.getFaultDescription());
        vo.setOperator(record.getRepairPerson());
        vo.setRemark(record.getRemarks());
        vo.setCreatedAt(record.getRepairDate());
        return vo;
    }

    public static OperationLogVO fromStockLog(StockOperationLog log) {
        OperationLogVO vo = new OperationLogVO();
        vo.setId("stock-" + log.getId());
        vo.setCategory("MACHINE".equals(log.getResourceType()) ? "整车出入库" : "配件出入库");
        vo.setAction(log.getOperationType());
        vo.setTarget(display(log.getResourceCode()) + " / " + display(log.getResourceName()));
        vo.setSummary(("INBOUND".equals(log.getOperationType()) ? "入库 " : "出库 ") + log.getQuantity());
        vo.setOperator(log.getOperator());
        vo.setQuantity(log.getQuantity());
        vo.setBeforeQuantity(log.getBeforeQuantity());
        vo.setAfterQuantity(log.getAfterQuantity());
        vo.setRemark(log.getRemark());
        vo.setCreatedAt(log.getCreatedAt());
        return vo;
    }

    private static String display(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String joinTarget(String code, String name) {
        if ((code == null || code.isBlank()) && (name == null || name.isBlank())) {
            return "-";
        }
        if (code == null || code.isBlank()) {
            return name;
        }
        if (name == null || name.isBlank()) {
            return code;
        }
        return code + " / " + name;
    }
}
