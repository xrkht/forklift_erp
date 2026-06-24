package com.example.forklift_erp.service.impl;

import com.example.forklift_erp.dto.DataImportErrorVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DataImportWorkbookValidatorTests {

    private final DataImportWorkbookValidator validator = new DataImportWorkbookValidator();

    @Test
    void validateVehicleRowsReportsMissingAndDuplicateVehicles() {
        WorkbookSnapshot snapshot = new WorkbookSnapshot(Map.of(
                "Sales",
                List.of(
                        new WorkbookRow(2, row("", "", "", "", "", "V-001", "", "", "", "", "", "", "", "", "Customer A")),
                        new WorkbookRow(3, row("", "", "", "", "", "V-001", "", "", "", "", "", "", "", "", "Customer B")),
                        new WorkbookRow(4, row("", "", "", "", "", "", "", "", "", "", "", "", "", "", "Customer C")),
                        new WorkbookRow(5, row("", "", "", "", "", "V-002"))
                )
        ));

        List<DataImportErrorVO> errors = validator.validateVehicleRows(snapshot);

        assertThat(errors).extracting(DataImportErrorVO::getFieldName)
                .contains("vehicleNumber", "customerName");
        assertThat(errors).anySatisfy(error -> {
            assertThat(error.getRowNumber()).isEqualTo(3);
            assertThat(error.getMessage()).isEqualTo("Duplicate vehicle number in sheet");
            assertThat(error.getValue()).isEqualTo("V-001");
        });
        assertThat(errors).anySatisfy(error -> {
            assertThat(error.getRowNumber()).isEqualTo(4);
            assertThat(error.getMessage()).isEqualTo("Vehicle number is required");
        });
        assertThat(errors).anySatisfy(error -> {
            assertThat(error.getRowNumber()).isEqualTo(5);
            assertThat(error.getMessage()).isEqualTo("Customer name is required");
        });
    }

    @Test
    void validatePartRowsReportsRequiredPartFields() {
        WorkbookSnapshot snapshot = new WorkbookSnapshot(Map.of(
                "Parts",
                List.of(
                        new WorkbookRow(2, row("", "", "", "", "Filter", "", "", "3")),
                        new WorkbookRow(3, row("", "P-001", "", "", "", "", "", "3")),
                        new WorkbookRow(4, row("", "P-002", "", "", "Tire", "", "", "not-a-number"))
                )
        ));

        List<DataImportErrorVO> errors = validator.validatePartRows(snapshot);

        assertThat(errors).extracting(DataImportErrorVO::getFieldName)
                .containsExactly("partCode", "partName", "quantity");
    }

    private List<String> row(String... values) {
        return List.of(values);
    }
}
