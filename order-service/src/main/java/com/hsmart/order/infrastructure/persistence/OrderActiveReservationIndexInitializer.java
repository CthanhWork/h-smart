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

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute(CREATE_ACTIVE_ORDER_INDEX_SQL);
            log.info("Ensured active order reservation index exists");
        } catch (RuntimeException exception) {
            log.warn("Could not create active order reservation index. Service-level validation remains active", exception);
        }
    }
}
