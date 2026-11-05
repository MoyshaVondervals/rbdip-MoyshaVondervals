package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.rbdip.bookstore.reference.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class NormalizedOrderApiTest extends AbstractIntegrationTest {

    @Test
    void reusesCustomerAndProductReferencesWhilePreservingApi() {
        Long productId = createProduct();
        var request = request("Lab2 Customer", "Address", null, productId, null);
        Long firstOrder = createOrder(request);
        Long secondOrder = createOrder(request);
        Long changedAddressOrder = createOrder(request("Lab2 Customer", "Other address", null, productId, 2));

        Long customerId = customerId(firstOrder);
        assertThat(customerId(secondOrder)).isEqualTo(customerId);
        assertThat(customerId(changedAddressOrder)).isNotEqualTo(customerId);
        assertThat(jdbcTemplate.queryForList(
                "SELECT product_id FROM order_items ORDER BY id", Long.class))
                .containsExactly(productId, productId, productId);
        assertThat(jdbcTemplate.queryForList(
                "SELECT quantity FROM order_items ORDER BY id", Integer.class)).containsExactly(1, 1, 2);
        var response = restTemplate.getForEntity("/orders", List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(3);
        Map<?, ?> order = (Map<?, ?>) response.getBody().getFirst();
        assertThat(order.get("customerFullName")).isEqualTo("Lab2 Customer");
        assertThat(order.get("items")).isEqualTo(List.of(Map.of("productName", "Lab2 Book", "quantity", 1)));
    }

    @Test
    void invalidOrdersDoNotLeaveCustomersOrdersOrItems() {
        Long productId = createProduct();
        List<CreateOrderRequest> invalid = List.of(
                request(null, "Address", null, productId, 1),
                request(" ", "Address", null, productId, 1),
                request("Lab2 Invalid", null, null, productId, 1),
                request("Lab2 Invalid", " ", null, productId, 1),
                request("Lab2 Invalid", "Address", null, productId, 0),
                request("Lab2 Invalid", "Address", null, productId, -1),
                request("Lab2 Invalid", "Address", null, -1L, 1),
                new CreateOrderRequest("Lab2 Invalid", "Address", null, null, null, null),
                new CreateOrderRequest("Lab2 Invalid", "Address", null, null, null, List.of()));
        for (CreateOrderRequest request : invalid) {
            assertThat(restTemplate.postForEntity("/orders", request, Map.class).getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM orders", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM order_items", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM customers WHERE customer_full_name = 'Lab2 Invalid'", Integer.class)).isZero();
    }

    private Long createProduct() {
        var response = restTemplate.postForEntity(
                "/products", Map.of("name", "Lab2 Book", "price", new BigDecimal("12.50")), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) response.getBody().get("id")).longValue();
    }

    private Long createOrder(CreateOrderRequest request) {
        var response = restTemplate.postForEntity("/orders", request, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsEntry("status", "new");
        return ((Number) response.getBody().get("id")).longValue();
    }

    private Long customerId(Long orderId) {
        return jdbcTemplate.queryForObject("SELECT customer_id FROM orders WHERE id = ?", Long.class, orderId);
    }

    private static CreateOrderRequest request(
            String name, String address, String phone, Long productId, Integer quantity) {
        return new CreateOrderRequest(name, address, phone, null, null,
                List.of(new CreateOrderRequest.Item(productId, quantity)));
    }
}
