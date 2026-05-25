package com.hsmart.backend.service.impl;

import com.hsmart.backend.service.PriceSuggestionService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisPriceSuggestionService implements PriceSuggestionService {

    private static final String AVERAGE_PRICE_KEY_PREFIX = "price:avg:";

    private final RedisTemplate<String, String> productRedisTemplate;

    @Override
    public Optional<BigDecimal> findSuggestedPrice(String label) {
        String key = buildAveragePriceKey(label);
        if (key == null) {
            return Optional.empty();
        }

        try {
            String value = productRedisTemplate.opsForValue().get(key);
            if (!StringUtils.hasText(value)) {
                return Optional.empty();
            }

            return Optional.of(new BigDecimal(value));
        } catch (NumberFormatException exception) {
            log.warn("Ignored invalid average price cache value for key {}", key);
            return Optional.empty();
        } catch (RuntimeException exception) {
            log.warn("Average price cache lookup failed for key {}", key, exception);
            return Optional.empty();
        }
    }

    @Override
    public void storeAveragePrice(String label, BigDecimal averagePrice) {
        String key = buildAveragePriceKey(label);
        if (key == null || averagePrice == null) {
            return;
        }

        productRedisTemplate.opsForValue().set(
                key,
                averagePrice.setScale(2, RoundingMode.HALF_UP).toPlainString()
        );
    }

    private String buildAveragePriceKey(String label) {
        if (!StringUtils.hasText(label)) {
            return null;
        }
        return AVERAGE_PRICE_KEY_PREFIX + label.trim().toLowerCase(Locale.ROOT);
    }
}
