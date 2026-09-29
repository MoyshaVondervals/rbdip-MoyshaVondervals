package com.rbdip.bookstore.order;

import java.math.BigDecimal;

public interface OrderNotifier {
    void orderPlaced(Order order, BigDecimal total);
}
