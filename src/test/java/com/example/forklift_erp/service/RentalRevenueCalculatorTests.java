package com.example.forklift_erp.service;

import com.example.forklift_erp.constant.RentalStatus;
import com.example.forklift_erp.entity.RentalRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

class RentalRevenueCalculatorTests {

    private final RentalRevenueCalculator calculator = new RentalRevenueCalculator(
            Clock.fixed(Instant.parse("2026-06-22T00:00:00Z"), ZoneId.of("Asia/Shanghai"))
    );

    @Test
    void monthlyAmountsProrateReturnedRentalAcrossMonths() {
        RentalRecord rental = rental("3000.00", LocalDate.of(2026, 1, 16), LocalDate.of(2026, 2, 15), RentalStatus.RETURNED.code());

        var amounts = calculator.monthlyAmounts(rental, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertThat(amounts).containsOnlyKeys(YearMonth.of(2026, 1), YearMonth.of(2026, 2));
        assertThat(amounts.get(YearMonth.of(2026, 1))).isEqualByComparingTo("1548.39");
        assertThat(amounts.get(YearMonth.of(2026, 2))).isEqualByComparingTo("1607.14");
    }

    @Test
    void totalAmountUsesActiveRentalThroughCurrentDate() {
        RentalRecord rental = rental("3100.00", LocalDate.of(2026, 6, 1), null, RentalStatus.ACTIVE.code());

        BigDecimal amount = calculator.totalAmount(rental);

        assertThat(amount).isEqualByComparingTo("2273.33");
    }

    @Test
    void amountForRangeReturnsZeroWhenRentalIsOutsideRange() {
        RentalRecord rental = rental("3000.00", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), RentalStatus.RETURNED.code());

        BigDecimal amount = calculator.amountForRange(rental, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));

        assertThat(amount).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private RentalRecord rental(String monthlyPrice, LocalDate startDate, LocalDate endDate, String status) {
        RentalRecord rental = new RentalRecord();
        rental.setMonthlyRentalPrice(new BigDecimal(monthlyPrice));
        rental.setRentalPrice(new BigDecimal("1.00"));
        rental.setStartDate(startDate);
        rental.setEndDate(endDate);
        rental.setStatus(status);
        return rental;
    }
}
