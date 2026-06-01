package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.common.ResultCode;
import com.example.forklift_erp.dto.CustomerVO;
import com.example.forklift_erp.dto.ExcelExportFile;
import com.example.forklift_erp.dto.MachineInventoryVO;
import com.example.forklift_erp.dto.OutboundOrderVO;
import com.example.forklift_erp.dto.PartInventoryVO;
import com.example.forklift_erp.dto.PurchaseOrderVO;
import com.example.forklift_erp.dto.RentalRecordVO;
import com.example.forklift_erp.dto.RepairRecordVO;
import com.example.forklift_erp.dto.StocktakingRecordVO;
import com.example.forklift_erp.dto.SupplierVO;
import com.example.forklift_erp.exception.BusinessException;
import com.example.forklift_erp.security.PermissionService;
import com.example.forklift_erp.service.CustomerService;
import com.example.forklift_erp.service.ExcelExportService;
import com.example.forklift_erp.service.MachineInventoryService;
import com.example.forklift_erp.service.OutboundOrderService;
import com.example.forklift_erp.service.PartInventoryService;
import com.example.forklift_erp.service.PurchaseOrderService;
import com.example.forklift_erp.service.RentalRecordService;
import com.example.forklift_erp.service.RepairRecordService;
import com.example.forklift_erp.service.StocktakingRecordService;
import com.example.forklift_erp.service.SupplierService;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
public class ExcelExportServiceImpl implements ExcelExportService {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private MachineInventoryService machineInventoryService;

    @Autowired
    private PartInventoryService partInventoryService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private OutboundOrderService outboundOrderService;

    @Autowired
    private RentalRecordService rentalRecordService;

    @Autowired
    private RepairRecordService repairRecordService;

    @Autowired
    private SupplierService supplierService;

    @Autowired
    private PurchaseOrderService purchaseOrderService;

    @Autowired
    private StocktakingRecordService stocktakingRecordService;

    @Override
    @Transactional(readOnly = true)
    public ExcelExportFile export(String type, Authentication authentication) {
        String normalizedType = normalizeType(type);
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Styles styles = new Styles(workbook);
            String label = switch (normalizedType) {
                case "vehicles" -> exportVehicles(workbook, styles);
                case "parts" -> exportParts(workbook, styles);
                case "customers" -> exportCustomers(workbook, styles);
                case "outbound-orders" -> {
                    requirePermission(authentication, "stock:adjust");
                    yield exportOutboundOrders(workbook, styles);
                }
                case "rentals" -> {
                    requirePermission(authentication, "stock:adjust");
                    yield exportRentals(workbook, styles);
                }
                case "repairs" -> exportRepairs(workbook, styles);
                case "suppliers" -> {
                    requirePermission(authentication, "stock:adjust");
                    yield exportSuppliers(workbook, styles);
                }
                case "purchase-orders" -> {
                    requirePermission(authentication, "stock:adjust");
                    yield exportPurchaseOrders(workbook, styles);
                }
                case "stocktaking-records" -> {
                    requirePermission(authentication, "stock:adjust");
                    yield exportStocktakingRecords(workbook, styles);
                }
                default -> throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的导出类型");
            };
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return new ExcelExportFile(label + "-" + FILE_TIME.format(LocalDateTime.now()) + ".xlsx", out.toByteArray());
        } catch (IOException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "导出 Excel 失败");
        }
    }

    private String exportVehicles(XSSFWorkbook workbook, Styles styles) {
        List<MachineInventoryVO> rows = machineInventoryService.findAll().stream()
                .map(MachineInventoryVO::fromEntity)
                .toList();
        writeSheet(workbook, styles, "车辆库存", List.of(
                "ID", "车号", "名称", "规格型号", "类型", "配置", "供应商", "仓库", "库存数", "库存状态",
                "采购价", "销售价", "结算价", "入库日期", "销售日期", "申请单号", "物料号", "备注"
        ), rows.stream().map(row -> List.of(
                value(row.getId()), value(row.getVehicleProductNumber()), value(row.getName()), value(row.getSpecificationModel()),
                value(row.getMachineType()), value(row.getConfiguration()), value(row.getSupplier()), value(row.getWarehouseName()),
                value(row.getInventoryCount()), value(row.getStockStatus()), value(row.getPurchasePrice()), value(row.getSalePrice()),
                value(row.getSettlementPrice()), value(row.getInboundDate()), value(row.getSalesDate()), value(row.getApplicationNumber()),
                value(row.getMaterialNumber()), value(row.getRemarks())
        )).toList());
        return "车辆库存";
    }

    private String exportParts(XSSFWorkbook workbook, Styles styles) {
        List<PartInventoryVO> rows = partInventoryService.findAll().stream()
                .map(PartInventoryVO::fromEntity)
                .toList();
        writeSheet(workbook, styles, "配件库存", List.of(
                "ID", "配件编码", "品牌", "配件名称", "规格", "类别", "适用车型", "数量", "单位",
                "采购价", "销售价", "结算价", "仓库ID", "来源", "来源车ID", "入库日期", "备注"
        ), rows.stream().map(row -> List.of(
                value(row.getId()), value(row.getPartCode()), value(row.getPartBrand()), value(row.getPartName()),
                value(row.getSpecification()), value(row.getPartCategory()), value(row.getApplicableModels()),
                value(row.getQuantity()), value(row.getUnit()), value(row.getPurchasePrice()), value(row.getSalePrice()),
                value(row.getSettlementPrice()), value(row.getWarehouseId()), value(row.getSource()), value(row.getSourceMachineId()),
                value(row.getInboundDate()), value(row.getRemarks())
        )).toList());
        return "配件库存";
    }

    private String exportCustomers(XSSFWorkbook workbook, Styles styles) {
        List<CustomerVO> rows = customerService.findAll();
        writeSheet(workbook, styles, "客户档案", List.of(
                "ID", "公司名称", "地址", "联系人", "联系电话", "税号/证件号", "备注"
        ), rows.stream().map(row -> List.of(
                value(row.getId()), value(row.getCompanyName()), value(row.getAddress()), value(row.getContactName()),
                value(row.getContactPhone()), value(row.getTaxOrIdNumber()), value(row.getRemarks())
        )).toList());
        return "客户档案";
    }

    private String exportOutboundOrders(XSSFWorkbook workbook, Styles styles) {
        List<OutboundOrderVO> rows = outboundOrderService.findAll();
        writeSheet(workbook, styles, "出库订单", List.of(
                "ID", "订单号", "资源类型", "资源编码", "资源名称", "规格型号", "数量", "单位", "客户", "销售日期",
                "结算价", "销售价", "应收金额", "已收金额", "欠款金额", "收款到期日", "最近收款日",
                "车款结清", "报销售", "申请发票", "发票状态", "开票日期", "发票文件",
                "合同", "合同文件", "经办人", "备注"
        ), rows.stream().map(row -> List.of(
                value(row.getId()), value(row.getOrderNo()), value(row.getResourceType()), value(row.getResourceCode()),
                value(row.getResourceName()), value(row.getSpecificationModel()), value(row.getQuantity()), value(row.getUnit()),
                value(row.getCustomerName()), value(row.getSalesDate()), value(row.getSettlementPrice()), value(row.getSalePrice()),
                value(row.getReceivableAmount()), value(row.getReceivedAmount()), value(row.getOutstandingAmount()),
                value(row.getPaymentDueDate()), value(row.getLastPaymentDate()),
                yesNo(row.getPaymentSettled()), yesNo(row.getSalesReported()), yesNo(row.getInvoiceApplied()), value(row.getInvoiceStatus()),
                value(row.getInvoiceIssuedDate()), value(row.getInvoiceOriginalName()), value(row.getContractType()),
                value(row.getContractOriginalName()), value(row.getOperator()), value(row.getOrderRemark())
        )).toList());
        return "出库订单";
    }

    private String exportRentals(XSSFWorkbook workbook, Styles styles) {
        List<RentalRecordVO> rows = rentalRecordService.findAll();
        writeSheet(workbook, styles, "租赁记录", List.of(
                "ID", "租赁单号", "车号", "车辆名称", "规格型号", "客户", "客户地址", "租赁去向", "月租价格",
                "开始日期", "结束日期", "状态", "经办人", "备注"
        ), rows.stream().map(row -> List.of(
                value(row.getId()), value(row.getRentalNo()), value(row.getVehicleNumber()), value(row.getMachineName()),
                value(row.getSpecificationModel()), value(row.getCustomerName()), value(row.getCustomerAddress()),
                value(row.getDestination()), value(row.getMonthlyRentalPrice()), value(row.getStartDate()), value(row.getEndDate()),
                value(row.getStatus()), value(row.getOperator()), value(row.getRemark())
        )).toList());
        return "租赁记录";
    }

    private String exportRepairs(XSSFWorkbook workbook, Styles styles) {
        List<RepairRecordVO> rows = repairRecordService.findAll().stream()
                .map(RepairRecordVO::fromEntity)
                .toList();
        writeSheet(workbook, styles, "维修记录", List.of(
                "ID", "维修日期", "车号", "客户", "故障描述", "维修内容", "维修人员", "使用配件",
                "工时", "维修费用", "配件费用", "总费用", "状态", "备注"
        ), rows.stream().map(row -> List.of(
                value(row.getId()), value(row.getRepairDate()), value(row.getVehicleNumber()), value(row.getCustomerName()),
                value(row.getFaultDescription()), value(row.getRepairContent()), value(row.getRepairPerson()),
                value(row.getUsedParts()), value(row.getWorkHours()), value(row.getRepairFee()), value(row.getPartsFee()),
                value(row.getTotalFee()), value(row.getStatus()), value(row.getRemarks())
        )).toList());
        return "维修记录";
    }

    private String exportSuppliers(XSSFWorkbook workbook, Styles styles) {
        List<SupplierVO> rows = supplierService.findAll();
        writeSheet(workbook, styles, "采购供应商", List.of(
                "ID", "供应商名称", "供应类型", "联系人", "联系电话", "地址", "税号", "开户行/账号", "备注"
        ), rows.stream().map(row -> List.of(
                value(row.getId()), value(row.getSupplierName()), value(row.getSupplierType()), value(row.getContactName()),
                value(row.getContactPhone()), value(row.getAddress()), value(row.getTaxNumber()), value(row.getBankAccount()),
                value(row.getRemarks())
        )).toList());
        return "采购供应商";
    }

    private String exportPurchaseOrders(XSSFWorkbook workbook, Styles styles) {
        List<PurchaseOrderVO> rows = purchaseOrderService.findAll();
        writeSheet(workbook, styles, "采购订单", List.of(
                "ID", "采购单号", "供应商", "配件编码/物料号", "配件名称", "规格型号", "数量", "单位",
                "单价", "总金额", "运费", "采购日期", "预计到货", "状态", "经办人", "备注"
        ), rows.stream().map(row -> List.of(
                value(row.getId()), value(row.getPurchaseNo()), value(row.getSupplierName()), value(row.getResourceCode()),
                value(row.getResourceName()), value(row.getSpecificationModel()), value(row.getQuantity()),
                value(row.getUnit()), value(row.getUnitPrice()), value(row.getTotalAmount()), value(row.getFreightAmount()), value(row.getOrderDate()),
                value(row.getExpectedArrivalDate()), value(row.getStatus()), value(row.getOperator()), value(row.getRemark())
        )).toList());
        return "采购订单";
    }

    private String exportStocktakingRecords(XSSFWorkbook workbook, Styles styles) {
        List<StocktakingRecordVO> rows = stocktakingRecordService.findAll();
        writeSheet(workbook, styles, "库存盘点", List.of(
                "ID", "盘点单号", "盘点类型", "资源编码", "资源名称", "规格型号", "账面数量", "实盘数量",
                "差异数量", "盘点日期", "状态", "盘点人", "备注"
        ), rows.stream().map(row -> List.of(
                value(row.getId()), value(row.getStocktakingNo()), value(row.getResourceType()), value(row.getResourceCode()),
                value(row.getResourceName()), value(row.getSpecificationModel()), value(row.getBookQuantity()), value(row.getActualQuantity()),
                value(row.getDifferenceQuantity()), value(row.getStocktakingDate()), value(row.getStatus()), value(row.getOperator()),
                value(row.getRemark())
        )).toList());
        return "库存盘点";
    }

    private void writeSheet(XSSFWorkbook workbook, Styles styles, String sheetName, List<String> headers, List<List<Object>> rows) {
        Sheet sheet = workbook.createSheet(sheetName);
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers.get(i));
            cell.setCellStyle(styles.header);
        }
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            Row sheetRow = sheet.createRow(rowIndex + 1);
            List<Object> values = rows.get(rowIndex);
            for (int col = 0; col < headers.size(); col++) {
                Cell cell = sheetRow.createCell(col);
                Object value = col < values.size() ? values.get(col) : "";
                writeCell(cell, value);
                cell.setCellStyle(styles.body);
            }
        }
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, Math.max(0, rows.size()), 0, headers.size() - 1));
        for (int i = 0; i < headers.size(); i++) {
            sheet.autoSizeColumn(i);
            int width = Math.min(Math.max(sheet.getColumnWidth(i), 10 * 256), 40 * 256);
            sheet.setColumnWidth(i, width);
        }
    }

    private void writeCell(Cell cell, Object value) {
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
            return;
        }
        cell.setCellValue(value == null ? "" : String.valueOf(value));
    }

    private void requirePermission(Authentication authentication, String permission) {
        if (!permissionService.hasPermission(authentication, permission)) {
            throw new AccessDeniedException("Access Denied");
        }
    }

    private String normalizeType(String type) {
        return type == null ? "" : type.trim().toLowerCase(Locale.ROOT).replace("_", "-");
    }

    private Object value(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof LocalDate date) {
            return DATE.format(date);
        }
        if (value instanceof LocalDateTime dateTime) {
            return DATE_TIME.format(dateTime);
        }
        return value;
    }

    private String yesNo(Boolean value) {
        return Boolean.TRUE.equals(value) ? "是" : "否";
    }

    private static final class Styles {
        private final CellStyle header;
        private final CellStyle body;

        private Styles(XSSFWorkbook workbook) {
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            header = workbook.createCellStyle();
            header.setFont(headerFont);
            header.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setVerticalAlignment(VerticalAlignment.CENTER);
            header.setBorderBottom(BorderStyle.THIN);

            body = workbook.createCellStyle();
            body.setVerticalAlignment(VerticalAlignment.CENTER);
            body.setWrapText(false);
            body.setBorderBottom(BorderStyle.HAIR);
            body.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        }
    }
}
