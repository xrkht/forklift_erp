package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.dto.DataImportErrorVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class DataImportWorkbookValidator {

    List<DataImportErrorVO> validateVehicleRows(WorkbookSnapshot snapshot) {
        List<DataImportErrorVO> errors = new ArrayList<>();
        validateVehicleSheet(snapshot.sheetRows("Inbound"), "Inbound", 8, 14, 1, errors);
        validateVehicleSheet(snapshot.sheetRows("Sales"), "Sales", 5, 14, 1, errors);
        validateVehicleSheet(snapshot.sheetRows("OtherBrandSales"), "OtherBrandSales", 6, 13, 1, errors);
        validateVehicleSheet(snapshot.sheetRows("OldInbound"), "OldInbound", 6, -1, 1, errors);
        validateVehicleSheet(snapshot.sheetRows("OldSales"), "OldSales", 5, 13, 1, errors);
        return errors;
    }

    List<DataImportErrorVO> validatePartRows(WorkbookSnapshot snapshot) {
        List<DataImportErrorVO> errors = new ArrayList<>();
        for (WorkbookRow row : snapshot.sheetRows("Parts")) {
            if (text(row, 1) == null) {
                errors.add(new DataImportErrorVO("Parts", row.rowNumber(), "partCode", "Part code is required", null));
            }
            if (text(row, 4) == null) {
                errors.add(new DataImportErrorVO("Parts", row.rowNumber(), "partName", "Part name is required", null));
            }
            if (decimal(row, 7) == null) {
                errors.add(new DataImportErrorVO("Parts", row.rowNumber(), "quantity", "Quantity is required", null));
            }
        }
        return errors;
    }

    private void validateVehicleSheet(
            List<WorkbookRow> rows,
            String sheetName,
            int vehicleColumn,
            int customerColumn,
            int minimumColumns,
            List<DataImportErrorVO> errors
    ) {
        Set<String> seenVehicles = new HashSet<>();
        for (WorkbookRow row : rows) {
            String vehicleNumber = text(row, vehicleColumn);
            if (vehicleNumber == null) {
                errors.add(new DataImportErrorVO(sheetName, row.rowNumber(), "vehicleNumber", "Vehicle number is required", null));
                continue;
            }
            if (!seenVehicles.add(vehicleNumber)) {
                errors.add(new DataImportErrorVO(sheetName, row.rowNumber(), "vehicleNumber", "Duplicate vehicle number in sheet", vehicleNumber));
            }
            if (customerColumn >= 0 && text(row, customerColumn) == null) {
                errors.add(new DataImportErrorVO(sheetName, row.rowNumber(), "customerName", "Customer name is required", null));
            }
            if (minimumColumns > 0 && row.values().size() < minimumColumns) {
                errors.add(new DataImportErrorVO(sheetName, row.rowNumber(), "columns", "Sheet row is incomplete", null));
            }
        }
    }

    private String text(WorkbookRow row, int index) {
        if (row == null || index < 0 || index >= row.values().size()) {
            return null;
        }
        String value = row.values().get(index);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private BigDecimal decimal(WorkbookRow row, int index) {
        String value = text(row, index);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(",", ""));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
