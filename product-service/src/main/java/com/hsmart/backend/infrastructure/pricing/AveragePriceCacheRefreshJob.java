package com.hsmart.backend.infrastructure.pricing;

import com.hsmart.backend.application.dto.AveragePriceByLabelProjection;
import com.hsmart.backend.infrastructure.persistence.ProductRepository;
import com.hsmart.backend.service.PriceSuggestionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AveragePriceCacheRefreshJob {

    private final ProductRepository productRepository;
    private final PriceSuggestionService priceSuggestionService;

    @Scheduled(cron = "${pricing.average-refresh-cron:0 0 * * * *}")
    @Transactional(readOnly = true)
    public void refreshAverageSoldPrices() {
        try {
            List<AveragePriceByLabelProjection> averagePrices = productRepository.findAverageSoldPricesByAiLabel();
            averagePrices.forEach(price ->
                    priceSuggestionService.storeAveragePrice(price.getLabel(), price.getAveragePrice())
            );
            log.info("Refreshed average sold price cache for {} AI labels", averagePrices.size());
        } catch (RuntimeException exception) {
            log.error("Average sold price cache refresh failed", exception);
        }
    }
}
