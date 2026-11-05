package com.rbdip.bookstore.order;

import com.rbdip.bookstore.customer.Customer;
import com.rbdip.bookstore.customer.CustomerRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final CreateOrderRequestValidator validator;
    private final OrderLineFactory orderLineFactory;
    private final PricingCalculator pricingCalculator;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderNotifier orderNotifier;
    private final CustomerRepository customerRepository;

    public OrderService(
            CreateOrderRequestValidator validator,
            OrderLineFactory orderLineFactory,
            PricingCalculator pricingCalculator,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            OrderNotifier orderNotifier,
            CustomerRepository customerRepository) {
        this.validator = validator;
        this.orderLineFactory = orderLineFactory;
        this.pricingCalculator = pricingCalculator;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderNotifier = orderNotifier;
        this.customerRepository = customerRepository;
    }

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        validator.validate(request);

        List<OrderLine> lines = orderLineFactory.createLines(request.items());
        BigDecimal total = pricingCalculator.calculateOrderTotal(
                lines.stream().map(OrderLine::toPricingLineItem).toList(),
                customerTypeOf(request),
                request.couponCode());

        Customer customer = customerRepository.findByFullNameAndAddressAndPhone(
            request.customerFullName(),request.customerAddress(), request.customerPhone())
            .orElseGet(() -> customerRepository.save(new Customer(request.customerFullName(),
                    request.customerAddress(), request.customerPhone())));
        Order order = orderRepository.save(new Order(customer, Order.STATUS_NEW));
        for (OrderLine line : lines) {
            orderItemRepository.save(line.toOrderItem(order.getId()));
        }

        orderNotifier.orderPlaced(order, total);

        return order;
    }

    private static String customerTypeOf(CreateOrderRequest request) {
        return request.customerType() == null
                ? PricingCalculator.CUSTOMER_TYPE_REGULAR
                : request.customerType();
    }
}
