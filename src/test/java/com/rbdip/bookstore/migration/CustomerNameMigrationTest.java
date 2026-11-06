package com.rbdip.bookstore.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class CustomerNameMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void supportsLegacyAndNewWritersUntilContractWithoutLosingData() throws Exception {
        var source = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var jdbc = new JdbcTemplate(source);
        Flyway.configure().dataSource(source).target("2").load().migrate();
        List<String> names = List.of("Ivan Petrov", "Prince", "Иван Иванович Петров", " Alice  Smith ", "Alice ");
        for (String name : names) {
            jdbc.update("INSERT INTO customers (customer_full_name) VALUES (?)", name);
        }

        var running = new AtomicBoolean(true);
        var operations = new AtomicInteger();
        var ready = new CountDownLatch(1);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var legacyTraffic = pool.submit(() -> {
                while (running.get()) {
                    int operation = operations.get();
                    jdbc.update("INSERT INTO customers (customer_full_name) VALUES (?)", "Concurrent " + operation);
                    assertThat(jdbc.queryForObject("SELECT customer_full_name FROM customers WHERE id = 1",
                            String.class)).isEqualTo("Ivan Petrov");
                    operations.incrementAndGet();
                    ready.countDown();
                }
            });
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                Flyway.configure().dataSource(source).target("3").load().migrate();
                assertDualWrites(jdbc);
                Flyway.configure().dataSource(source).target("4").load().migrate();
            } finally {
                running.set(false);
                legacyTraffic.get(10, TimeUnit.SECONDS);
            }
        }
        assertThat(operations.get()).as("Legacy traffic").isGreaterThan(1);
        System.out.printf("Legacy: %d pairs%n", operations.get());
        assertThat(jdbc.queryForList("""
                SELECT first_name || CASE WHEN last_name IS NULL THEN '' ELSE ' ' || last_name END
                FROM customers WHERE id <= 5 ORDER BY id
                """, String.class)).containsExactlyElementsOf(names);
        assertThat(jdbc.queryForList("SELECT customer_full_name FROM customers WHERE id <= 5 ORDER BY id",
                String.class)).containsExactlyElementsOf(names);
        assertThatThrownBy(() -> jdbc.execute("INSERT INTO customers (first_name) VALUES ('Prince')"))
                .isInstanceOf(DataIntegrityViolationException.class);

        Flyway.configure().dataSource(source).load().migrate();
        assertContract(jdbc);
        assertThat(Flyway.configure().dataSource(source).load().migrate().migrationsExecuted).isZero();
    }

    private static void assertDualWrites(JdbcTemplate jdbc) {
        jdbc.execute("INSERT INTO customers (customer_full_name) VALUES ('Legacy Writer')");
        assertThat(jdbc.queryForObject("SELECT first_name FROM customers WHERE customer_full_name = 'Legacy Writer'",
                String.class)).isEqualTo("Legacy");
        jdbc.execute("INSERT INTO customers (first_name, last_name) VALUES ('New', 'Writer')");
        assertThat(jdbc.queryForObject("SELECT customer_full_name FROM customers WHERE first_name = 'New'",
                String.class)).isEqualTo("New Writer");
        jdbc.execute("UPDATE customers SET customer_full_name = 'Legacy Updated Name' WHERE first_name = 'Legacy'");
        assertThat(jdbc.queryForObject("SELECT last_name FROM customers WHERE first_name = 'Legacy'",
                String.class)).isEqualTo("Updated Name");
        jdbc.execute("UPDATE customers SET last_name = 'Updated Name' WHERE first_name = 'New'");
        assertThat(jdbc.queryForObject("SELECT customer_full_name FROM customers WHERE first_name = 'New'",
                String.class)).isEqualTo("New Updated Name");
        assertThatThrownBy(() -> jdbc.execute("""
                INSERT INTO customers (customer_full_name, first_name, last_name)
                VALUES ('Wrong Name', 'Other', 'Name')
                """)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.execute("""
                UPDATE customers SET customer_full_name = 'Wrong Name', first_name = 'Other'
                WHERE first_name = 'New'
                """)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private static void assertContract(JdbcTemplate jdbc) {
        assertThat(jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'customers'
                """, String.class)).contains("first_name", "last_name").doesNotContain("customer_full_name");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customers WHERE first_name IS NULL", Integer.class))
                .isZero();
        jdbc.execute("INSERT INTO customers (first_name) VALUES ('Postcontract')");
        assertThatThrownBy(() -> jdbc.execute("INSERT INTO customers (first_name) VALUES ('Prince')"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.execute("INSERT INTO customers (first_name) VALUES (NULL)"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.execute("""
                INSERT INTO customers (first_name, last_name) VALUES (repeat('a', 255), 'b')
                """)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_trigger WHERE tgname = 'customers_sync_name'",
                Integer.class)).isZero();
    }
}
