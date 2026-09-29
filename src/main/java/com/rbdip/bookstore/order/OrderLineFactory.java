package com.rbdip.bookstore.order;

import com.rbdip.bookstore.product.Product;
import com.rbdip.bookstore.product.ProductRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class OrderLineFactory {
    private static final int DEFAULT_QUANTITY = 1;
    private final ProductRepository productRepository;
    public OrderLineFactory(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<OrderLine> createLines(List<CreateOrderRequest.Item> items) {
        List<OrderLine> lines = new ArrayList<>(items.size());
        for (CreateOrderRequest.Item item : items) {
            lines.add(createLine(item));
        }
        return lines;
    }

    private OrderLine createLine(CreateOrderRequest.Item item) {
        Product product = productRepository.findById(item.productId()).orElseThrow(()->
                new IllegalArgumentException("product " + item.productId() + " not found"));
        int quantity = item.quantity() == null ? DEFAULT_QUANTITY : item.quantity();
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        return new OrderLine(product, quantity);
    }

}
