package com.rbdip.bookstore.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class NormalizationMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void migratesExistingDataAndEnforcesReferences() {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).target("1").load().migrate();
        seedLegacyData(jdbc);

        Flyway.configure().dataSource(dataSource).target("2").load().migrate();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM customers", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_items", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM products", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForList("SELECT customer_id FROM orders WHERE id IN (1, 2)", Long.class))
                .containsOnly(jdbc.queryForObject("SELECT customer_id FROM orders WHERE id = 1", Long.class));
        assertThat(jdbc.queryForList("""
                SELECT c.customer_address FROM orders o JOIN customers c ON c.id = o.customer_id
                ORDER BY o.id
                """, String.class)).containsExactly(null, null, "Other address", null);
        assertThat(jdbc.queryForList("SELECT product_id FROM order_items ORDER BY id", Long.class))
                .containsExactly(1L, 3L, 4L, 4L);
        assertThat(jdbc.queryForObject("""
                SELECT sum(i.quantity * p.price) FROM order_items i JOIN products p ON p.id = i.product_id
                """, java.math.BigDecimal.class)).isEqualByComparingTo("105.00");
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id = 1", String.class)).isEqualTo("paid");
        assertThat(jdbc.queryForObject("SELECT created_at::text FROM orders WHERE id = 1", String.class))
                .isEqualTo("2020-01-02 03:04:05");
        assertConstraints(jdbc);
        assertThat(Flyway.configure().dataSource(dataSource).target("2").load().migrate().migrationsExecuted).isZero();
    }

    private static void seedLegacyData(JdbcTemplate jdbc) {
        jdbc.execute("""
                INSERT INTO products (name, price) VALUES ('Book', 10), ('Book', 10), ('Book', 20);
                INSERT INTO orders (customer_full_name, customer_address, customer_phone, status, created_at)
                VALUES ('Alice', NULL, NULL, 'paid', '2020-01-02 03:04:05'),
                       ('Alice', NULL, NULL, 'new', now()),
                       ('Alice', 'Other address', NULL, 'new', now()),
                       ('Bob', NULL, '123', 'new', now());
                INSERT INTO order_items (order_id, product_name, product_price, quantity)
                VALUES (1, 'Book', 10, 2), (2, 'Book', 20, 3),
                       (3, 'Removed book', 5, 4), (4, 'Removed book', 5, 1);
                """);
    }

    private static void assertConstraints(JdbcTemplate jdbc) {
        assertThatThrownBy(() -> jdbc.execute("INSERT INTO orders (customer_id) VALUES (-1)"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.execute("INSERT INTO orders (customer_id) VALUES (NULL)"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.execute(
                "INSERT INTO order_items (order_id, product_id) VALUES (1, -1)"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.execute(
                "INSERT INTO order_items (order_id, product_id) VALUES (1, NULL)"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.execute(
                "INSERT INTO customers (customer_full_name) VALUES ('Alice')"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
