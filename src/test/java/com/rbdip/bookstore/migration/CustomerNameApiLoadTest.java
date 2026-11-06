package com.rbdip.bookstore.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.rbdip.bookstore.order.CreateOrderRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.flyway.target=4", "spring.jpa.properties.hibernate.generate_statistics=false"})
class CustomerNameApiLoadTest {

    private static final int CLIENTS = 4;
    private static final int REQUESTS_PER_CLIENT = 20;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void contractDoesNotBreakRunningApi() throws Exception {
        var response = restTemplate.postForEntity("/products", Map.of("name", "Migration Book", "price", 10), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long productId = ((Number) response.getBody().get("id")).longValue();
        var ready = new CountDownLatch(CLIENTS);
        var migrate = new CountDownLatch(1);
        var migrating = new AtomicBoolean();
        var duringMigration = new AtomicInteger();
        var servedDuringMigration = new CountDownLatch(CLIENTS);
        List<Future<?>> requests = new ArrayList<>();
        try (var pool = Executors.newFixedThreadPool(CLIENTS + 1)) {
            for (int client = 0; client < CLIENTS; client++) {
                int clientId = client;
                requests.add(pool.submit(() -> {
                    var http = httpClient();
                    sendOrderAndRead(http, productId, clientId, -1);
                    ready.countDown();
                    assertThat(migrate.await(10, TimeUnit.SECONDS)).isTrue();
                    int requestId = 0;
                    do {
                        sendOrderAndRead(http, productId, clientId, requestId++);
                        duringMigration.incrementAndGet();
                        servedDuringMigration.countDown();
                    } while (migrating.get());
                    for (int i = 0; i < REQUESTS_PER_CLIENT; i++) {
                        sendOrderAndRead(http, productId, clientId, requestId++);
                    }
                    return null;
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            Future<?> migration = pool.submit(() -> {
                migrating.set(true);
                try {
                    return Flyway.configure().dataSource(dataSource)
                            .callbacks(migrationWindow(migrate, servedDuringMigration)).load().migrate();
                } finally {
                    migrating.set(false);
                    migrate.countDown();
                }
            });
            migration.get(30, TimeUnit.SECONDS);
            for (Future<?> request : requests) {
                request.get(30, TimeUnit.SECONDS);
            }
        } finally {
            migrate.countDown();
            migrating.set(false);
        }
        assertThat(duringMigration.get()).as("HTTP during migration").isPositive();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class))
                .isEqualTo(CLIENTS * (REQUESTS_PER_CLIENT + 1) + duringMigration.get());
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'customers' AND column_name = 'customer_full_name'
                """, Integer.class)).isZero();
        System.out.printf("HTTP: %d requests, %d pairs during migration%n",
                (CLIENTS * (REQUESTS_PER_CLIENT + 1) + duringMigration.get()) * 2, duringMigration.get());
    }

    private static Callback migrationWindow(CountDownLatch started, CountDownLatch served) {
        return new Callback() {
            @Override
            public boolean supports(Event event, Context context) {
                return event == Event.BEFORE_EACH_MIGRATE;
            }

            @Override
            public boolean canHandleInTransaction(Event event, Context context) {
                return true;
            }

            @Override
            public void handle(Event event, Context context) {
                started.countDown();
                try {
                    assertThat(served.await(10, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }

            @Override
            public String getCallbackName() {
                return "migration-load";
            }
        };
    }

    private TestRestTemplate httpClient() {
        return new TestRestTemplate(new RestTemplateBuilder().rootUri(restTemplate.getRootUri())
                .setConnectTimeout(Duration.ofSeconds(5)).setReadTimeout(Duration.ofSeconds(5)));
    }

    private static void sendOrderAndRead(TestRestTemplate http, long productId, int client, int requestId) {
        String name = "Client" + client + " Name" + requestId;
        var request = new CreateOrderRequest(name, "Address", null, null, null,
                List.of(new CreateOrderRequest.Item(productId, 1)));
        var created = http.postForEntity("/orders", request, Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var orders = http.getForEntity("/orders", List.class);
        assertThat(orders.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(orders.getBody()).anySatisfy(value -> {
            Map<?, ?> order = (Map<?, ?>) value;
            assertThat(order.get("id")).isEqualTo(created.getBody().get("id"));
            assertThat(order.get("customerFullName")).isEqualTo(name);
        });
    }
}
