package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PricingCalculatorTest {

    private final PricingCalculator calculator = new PricingCalculator();

    @ParameterizedTest
    @CsvSource({
        "10, 9, regular, , 90.00",
        "10, 10, regular, , 100.00",
        "10, 11, regular, , 104.50",
        "100, 1, vip, , 90.00",
        "100, 1, wholesale, , 85.00",
        "100, 1, , , 100.00",
        "100, 1, unknown, UNKNOWN, 100.00",
        "100, 1, VIP, save10, 100.00",
        "100, 1, regular, SAVE10, 90.00",
        "100, 1, regular, SAVE20PERCENT, 80.00",
        "100, 11, vip, SAVE10, 930.50",
        "100, 11, wholesale, SAVE20PERCENT, 710.60",
        "9.99, 1, regular, SAVE10, 0.00",
        "10, 1, regular, SAVE10, 0.00",
        "10.01, 1, regular, SAVE10, 0.01",
        "999.99, 1, regular, , 999.99",
        "1000, 1, regular, , 1000.00",
        "1000.01, 1, regular, , 980.01",
        "1010, 1, regular, SAVE10, 1000.00",
        "1010.01, 1, regular, SAVE10, 980.01",
        "1100, 1, vip, , 990.00",
        "1250, 1, regular, SAVE20PERCENT, 1000.00",
        "100, 20, vip, SAVE10, 1666.00",
        "0, 1, regular, , 0.00",
        "10, 0, regular, , 0.00",
        "-10, 1, regular, , 0.00",
        "10, -1, regular, , 0.00",
        "-10, -2, regular, , 20.00",
        "1.004, 1, regular, , 1.00",
        "1.005, 1, regular, , 1.01",
        "0.005, 2, regular, , 0.01"
    })
    void characterizesDiscountsBoundariesAndNegativeInputs(
            String price, int quantity, String customerType, String coupon, String expected) {
        BigDecimal result = calculator.calculateOrderTotal(
                List.of(new PricingCalculator.LineItem(new BigDecimal(price), quantity)), customerType, coupon);

        assertThat(result).isEqualTo(new BigDecimal(expected));
    }

    @Test
    void bulkDiscountAppliesPerLineInsteadOfToCombinedQuantity() {
        assertThat(calculator.calculateOrderTotal(List.of(
                line("10", 6), line("10", 5), line("20", 11)), "regular", null))
                .isEqualTo(new BigDecimal("319.00"));
    }

    @Test
    void roundsOnlyAfterSummingAndApplyingDiscounts() {
        assertThat(calculator.calculateOrderTotal(
                List.of(line("0.005", 1), line("0.005", 1)), "regular", null))
                .isEqualTo(new BigDecimal("0.01"));
        assertThat(calculator.calculateOrderTotal(List.of(line("1.005", 1)), "vip", null))
                .isEqualTo(new BigDecimal("0.90"));
    }

    @Test
    void negativeLinesAreClampedAfterSummingTheWholeOrder() {
        assertThat(calculator.calculateOrderTotal(
                List.of(line("-10", 1), line("15", 1)), "regular", null))
                .isEqualTo(new BigDecimal("5.00"));
    }

    @Test
    void emptyOrderCostsZeroEvenWithCoupon() {
        for (String coupon : Arrays.asList(null, "SAVE10", "SAVE20PERCENT")) {
            assertThat(calculator.calculateOrderTotal(List.of(), "vip", coupon))
                    .isEqualTo(new BigDecimal("0.00"));
        }
    }

    @Test
    void nullItemsOrPricesCurrentlyThrowNullPointerException() {
        assertThatNullPointerException().isThrownBy(() -> calculator.calculateOrderTotal(null, "regular", null));
        assertThatNullPointerException().isThrownBy(() -> calculator.calculateOrderTotal(
                Arrays.asList((PricingCalculator.LineItem) null), "regular", null));
        assertThatNullPointerException().isThrownBy(() -> calculator.calculateOrderTotal(
                List.of(new PricingCalculator.LineItem(null, 1)), "regular", null));
    }

    private static PricingCalculator.LineItem line(String price, int quantity) {
        return new PricingCalculator.LineItem(new BigDecimal(price), quantity);
    }
}
