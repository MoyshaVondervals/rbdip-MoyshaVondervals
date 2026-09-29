package com.rbdip.bookstore.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Модуль расчёта цены заказа. Намеренно почти не покрыт тестами и
 * содержит magic numbers / нечитаемые ветвления скидок - цель для
 * характеризационных тестов (ЛР2) и mutation-testing гейта PIT (ЛР5).
 */
@Component
public class PricingCalculator {

    static final String CUSTOMER_TYPE_REGULAR = "regular";
    static final String CUSTOMER_TYPE_VIP = "vip";
    static final String CUSTOMER_TYPE_WHOLESALE = "wholesale";
    static final String COUPON_FLAT = "SAVE10";
    static final String COUPON_PERCENT = "SAVE20PERCENT";

    private static final int BULK_QUANTITY_THRESHOLD = 10;
    private static final BigDecimal BULK_DISCOUNT_MULTIPLIER = new BigDecimal("0.95");
    private static final BigDecimal VIP_DISCOUNT_MULTIPLIER = new BigDecimal("0.9");
    private static final BigDecimal WHOLESALE_DISCOUNT_MULTIPLIER = new BigDecimal("0.85");
    private static final BigDecimal COUPON_FLAT_AMOUNT = BigDecimal.TEN;
    private static final BigDecimal COUPON_PERCENT_MULTIPLIER = new BigDecimal("0.8");
    private static final BigDecimal LARGE_ORDER_THRESHOLD = new BigDecimal("1000");
    private static final BigDecimal LARGE_ORDER_DISCOUNT_MULTIPLIER = new BigDecimal("0.98");
    private static final int TOTAL_SCALE = 2;
    private static final RoundingMode TOTAL_ROUNDING = RoundingMode.HALF_UP;

    public record LineItem(BigDecimal price, int quantity) {
    }

    public BigDecimal calculateOrderTotal(List<LineItem> items, String customerType, String couponCode) {
        BigDecimal total = sumLines(items);
        total = applyCustomerTypeDiscount(total, customerType);
        total = applyCoupon(total, couponCode);
        total = atLeastZero(total);
        total = applyLargeOrderDiscount(total);
        return total.setScale(TOTAL_SCALE, TOTAL_ROUNDING);
    }

    private BigDecimal sumLines(List<LineItem> items){
        BigDecimal sum = BigDecimal.ZERO;
        for (LineItem item : items){
            sum = sum.add(lineTotal(item));
        }
        return sum;
    }

    private BigDecimal lineTotal(LineItem item){
        BigDecimal linePrice = item.price().multiply(BigDecimal.valueOf(item.quantity()));
        if (item.quantity()> BULK_QUANTITY_THRESHOLD){
            linePrice = linePrice.multiply(BULK_DISCOUNT_MULTIPLIER);
        }
        return linePrice;
    }

    private BigDecimal applyCustomerTypeDiscount(BigDecimal total, String customerType){
        if (CUSTOMER_TYPE_VIP.equals(customerType)){
            return total.multiply(VIP_DISCOUNT_MULTIPLIER);
        }
        if (CUSTOMER_TYPE_WHOLESALE.equals(customerType)){
            return total.multiply(WHOLESALE_DISCOUNT_MULTIPLIER);
        }
        return total;
    }

    private BigDecimal applyCoupon(BigDecimal total, String couponCode) {
        if (COUPON_FLAT.equals(couponCode)) {
            return total.subtract(COUPON_FLAT_AMOUNT);
        }
        if (COUPON_PERCENT.equals(couponCode)) {
            return total.multiply(COUPON_PERCENT_MULTIPLIER);
        }
        return total;
    }

    private BigDecimal atLeastZero(BigDecimal total) {
        return total.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : total;
    }

    private BigDecimal applyLargeOrderDiscount(BigDecimal total) {
        if (total.compareTo(LARGE_ORDER_THRESHOLD) > 0) {
            return total.multiply(LARGE_ORDER_DISCOUNT_MULTIPLIER);
        }
        return total;
    }
}
