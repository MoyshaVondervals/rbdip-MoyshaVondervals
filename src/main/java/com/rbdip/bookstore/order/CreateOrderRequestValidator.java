package com.rbdip.bookstore.order;

import org.springframework.stereotype.Component;

@Component
public class CreateOrderRequestValidator {

    public void validate(CreateOrderRequest request) {
        requireNotBlank(request.customerFullName(), "customerFullName");
        requireNotBlank(request.customerAddress(), "customerAddress");
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("order must contain at least one item");
        }
    }

    private static void requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
