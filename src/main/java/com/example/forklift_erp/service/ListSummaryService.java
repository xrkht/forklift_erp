package com.example.forklift_erp.service;

import com.example.forklift_erp.constant.RentalStatus;
import com.example.forklift_erp.dto.ListSummaryVO;
import com.example.forklift_erp.entity.PurchaseOrder;
import com.example.forklift_erp.entity.RentalRecord;
import com.example.forklift_erp.repository.OutboundOrderRepository;
import com.example.forklift_erp.repository.PurchaseOrderRepository;
import com.example.forklift_erp.repository.RentalRecordRepository;
import com.example.forklift_erp.repository.StocktakingRecordRepository;
import com.example.forklift_erp.repository.SupplierRepository;
import com.example.forklift_erp.util.MoneyValues;
import com.example.forklift_erp.util.SearchKeywordSupport;
import com.example.forklift_erp.util.SecurityUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class ListSummaryService {

    private final OutboundOrderRepository outboundOrderRepository;
    private final RentalRecordRepository rentalRecordRepository;
    private final SupplierRepository supplierRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final StocktakingRecordRepository stocktakingRecordRepository;
    private final RentalRevenueCalculator rentalRevenueCalculator;

    public ListSummaryService(
            OutboundOrderRepository outboundOrderRepository,
            RentalRecordRepository rentalRecordRepository,
            SupplierRepository supplierRepository,
            PurchaseOrderRepository purchaseOrderRepository,
            StocktakingRecordRepository stocktakingRecordRepository,
            RentalRevenueCalculator rentalRevenueCalculator
    ) {
        this.outboundOrderRepository = outboundOrderRepository;
        this.rentalRecordRepository = rentalRecordRepository;
        this.supplierRepository = supplierRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.stocktakingRecordRepository = stocktakingRecordRepository;
        this.rentalRevenueCalculator = rentalRevenueCalculator;
    }

    public ListSummaryVO summarize(String type, String keyword) {
        return summarize(type, keyword, null);
    }

    public ListSummaryVO summarize(String type, String keyword, String resourceType) {
        return summarize(type, keyword, resourceType, null);
    }

    public ListSummaryVO summarize(String type, String keyword, String resourceType, String status) {
        String normalizedType = type == null ? "" : type.trim();
        return switch (normalizedType) {
            case "outboundOrders" -> outboundOrderSummary(keyword);
            case "rentals" -> rentalSummary(keyword, status);
            case "suppliers" -> supplierSummary(keyword);
            case "purchases" -> purchaseSummary(keyword, resourceType);
            case "stocktakes" -> stocktakingSummary(keyword);
            default -> {
                ListSummaryVO empty = new ListSummaryVO();
                empty.setType(normalizedType);
                empty.setKeyword(keyword);
                yield empty;
            }
        };
    }

    private ListSummaryVO outboundOrderSummary(String keyword) {
        OutboundOrderRepository.OutboundOrderSummaryProjection data = outboundOrderRepository.summarize(
                SearchKeywordSupport.likePrefix(keyword),
                SearchKeywordSupport.fullTextBoolean(keyword),
                SecurityUtils.isAdminOrSuperAdmin()
        );
        long totalCount = number(data.getTotalCount());
        SummaryCount rows = new SummaryCount(totalCount);
        long unsettledOrders = number(data.getUnsettledOrders());
        long pendingReports = number(data.getPendingReports());
        long pendingInvoices = number(data.getPendingInvoices());
        long settledOrders = number(data.getSettledOrders());
        long uploadedInvoices = number(data.getUploadedInvoices());
        long uploadedContracts = number(data.getUploadedContracts());
        long overdueOrders = number(data.getOverdueOrders());
        long machineOrders = number(data.getMachineOrders());
        BigDecimal outstandingAmount = amount(data.getOutstandingAmount());
        return summary("outboundOrders", keyword)
                .addCard("出库订单", rows.size(), unsettledOrders + " 单待收款")
                .addMoneyCard("应收欠款", outstandingAmount, overdueOrders + " 单逾期")
                .addCard("待报销售", pendingReports, machineOrders + " 单整机订单")
                .addCard("待申请发票", pendingInvoices, uploadedInvoices + " 单已上传发票")
                .addCard("已结清车款", settledOrders, uploadedContracts + " 单已上传合同");
    }

    private ListSummaryVO rentalSummary(String keyword, String status) {
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedStatus = normalizeKeyword(status);
        normalizedStatus = normalizedStatus == null ? "" : normalizedStatus.toLowerCase(Locale.ROOT);
        LocalDate today = LocalDate.now();
        List<RentalRecord> records = rentalRecordRepository.searchForSummary(
                normalizedKeyword, normalizedStatus, today, today.plusDays(7));
        long totalCount = records.size();
        SummaryCount rows = new SummaryCount(totalCount);
        long activeRows = records.stream().filter(record -> RentalStatus.ACTIVE.code().equals(record.getStatus())).count();
        long returnedRows = records.stream().filter(record -> RentalStatus.RETURNED.code().equals(record.getStatus())).count();
        long vehicleCount = records.stream().map(RentalRecord::getMachineId).filter(Objects::nonNull).distinct().count();
        BigDecimal rentalIncome = records.stream()
                .map(rentalRevenueCalculator::totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return summary("rentals", keyword)
                .addCard("租赁记录", rows.size(), activeRows + " 台租赁中")
                .addMoneyCard("已计租赁收入", rentalIncome, returnedRows + " 单已归还")
                .addCard("租赁车辆", vehicleCount, "按具体车号记录")
                .addCard("待归还", activeRows, activeRows > 0 ? "请持续跟进租赁去向" : "暂无进行中租赁");
    }

    private ListSummaryVO supplierSummary(String keyword) {
        SupplierRepository.SupplierSummaryProjection data = supplierRepository.summarize(normalizeKeyword(keyword));
        long totalCount = number(data.getTotalCount());
        SummaryCount rows = new SummaryCount(totalCount);
        long typed = number(data.getTyped());
        long contacts = number(data.getContacts());
        long purchaseSupplierCount = purchaseOrderRepository.countDistinctSupplierNames();
        return summary("suppliers", keyword)
                .addCard("供应商总数", rows.size(), typed + " 家已标记类型")
                .addCard("入库订单供应商", purchaseSupplierCount, "已被入库订单引用")
                .addCard("联系人", contacts, "可直接联系");
    }

    private ListSummaryVO purchaseSummary(String keyword, String resourceType) {
        PurchaseOrderRepository.PurchaseSummaryProjection data = purchaseOrderRepository.summarize(
                normalizeKeyword(keyword),
                normalizePurchaseResourceType(resourceType)
        );
        long totalCount = number(data.getTotalCount());
        SummaryCount rows = new SummaryCount(totalCount);
        BigDecimal totalAmount = amount(data.getTotalAmount());
        BigDecimal freightTotal = amount(data.getFreightTotal());
        long received = number(data.getReceived());
        long pending = number(data.getPending());
        return summary("purchases", keyword)
                .addCard("入库订单", rows.size(), pending + " 单待跟进")
                .addMoneyCard("采购金额", totalAmount, "当前筛选合计")
                .addCard("已收货", received, "运费 " + freightTotal);
    }

    private String normalizePurchaseResourceType(String value) {
        String normalized = normalizeKeyword(value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        return PurchaseOrder.RESOURCE_PART.equals(normalized) || PurchaseOrder.RESOURCE_MACHINE.equals(normalized)
                ? normalized
                : null;
    }

    private ListSummaryVO stocktakingSummary(String keyword) {
        StocktakingRecordRepository.StocktakingSummaryProjection data = stocktakingRecordRepository.summarize(normalizeKeyword(keyword));
        long totalCount = number(data.getTotalCount());
        SummaryCount rows = new SummaryCount(totalCount);
        long drafts = number(data.getDrafts());
        long completed = number(data.getCompleted());
        long differences = number(data.getDifferences());
        return summary("stocktakes", keyword)
                .addCard("盘点记录", rows.size(), drafts + " 条待入账")
                .addCard("已入账", completed, "库存已同步")
                .addCard("有差异", differences, "账实不一致");
    }

    private ListSummaryVO summary(String type, String keyword) {
        ListSummaryVO summary = new ListSummaryVO();
        summary.setType(type);
        summary.setKeyword(keyword);
        return summary;
    }

    private BigDecimal amount(BigDecimal value) {
        return MoneyValues.zeroIfNullOrNegative(value);
    }

    private long number(Long value) {
        return value == null ? 0L : value;
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null || keyword.isBlank() ? null : keyword.trim();
    }

    private record SummaryCount(long totalCount) {
        int size() {
            return toSafeInt(totalCount);
        }

        private static int toSafeInt(long value) {
            return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.toIntExact(value);
        }
    }
}
