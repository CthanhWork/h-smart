package com.hsmart.order.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderActiveReservationIndexInitializer implements ApplicationRunner {

    private static final String CREATE_ACTIVE_ORDER_INDEX_SQL = """
            create unique index if not exists ux_orders_active_product
            on orders(product_id)
            where status in ('PENDING', 'PROCESSING')
            """;

    private static final String RECREATE_DELIVERY_METHOD_CHECK_SQL = """
            alter table orders drop constraint if exists orders_delivery_method_check;
            alter table orders add constraint orders_delivery_method_check
            check (delivery_method in ('GHTK', 'VIETTEL_POST'))
            """;

    // Hibernate ddl-auto=update creates the status check on first run but never updates it
    // when the OrderStatus enum gains values (e.g. RETURN_REQUESTED, RETURNED) — refresh it here.
    private static final String RECREATE_STATUS_CHECK_SQL = """
            alter table orders drop constraint if exists orders_status_check;
            alter table orders add constraint orders_status_check
            check (status in ('PENDING', 'PROCESSING', 'COMPLETED', 'CANCELLED', 'RETURN_REQUESTED', 'RETURNED'))
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute(CREATE_ACTIVE_ORDER_INDEX_SQL);
            log.info("Ensured active order reservation index exists");
        } catch (RuntimeException exception) {
            log.warn("Could not create active order reservation index. Service-level validation remains active", exception);
        }

        try {
            jdbcTemplate.execute(RECREATE_DELIVERY_METHOD_CHECK_SQL);
            log.info("Ensured order delivery method check constraint supports configured providers");
        } catch (RuntimeException exception) {
            log.warn("Could not refresh order delivery method check constraint", exception);
        }

        try {
            jdbcTemplate.execute(RECREATE_STATUS_CHECK_SQL);
            log.info("Ensured order status check constraint supports return statuses");
        } catch (RuntimeException exception) {
            log.warn("Could not refresh order status check constraint", exception);
        }
    }
}
