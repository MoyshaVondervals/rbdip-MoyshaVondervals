package com.rbdip.bookstore.order;

import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingOrderNotifier implements OrderNotifier {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingOrderNotifier.class);

    @Override
    public void orderPlaced(Order order, BigDecimal total) {
        if (LOG.isInfoEnabled()) {
            LOG.info(
                    "Dear {}, your order #{} for {} has been placed.",
                    order.getCustomerFullName(),
                    order.getId(),
                    total);
        }
    }
}
