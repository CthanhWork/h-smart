package com.hsmart.gateway.infrastructure.ratelimit;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.ratelimit.AbstractRateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Primary
@Component("assistantRedisRateLimiter")
public class AssistantRedisRateLimiter extends AbstractRateLimiter<AssistantRedisRateLimiter.Config> {

    private static final String CONFIGURATION_PROPERTY_NAME = "assistant-redis-rate-limiter";
    private static final String KEY_PREFIX = "hsmart:gateway:rate-limit:assistant";
    private static final int REQUESTED_TOKENS = 1;
    private static final String REMAINING_HEADER = "X-RateLimit-Remaining";
    private static final String REPLENISH_RATE_HEADER = "X-RateLimit-Replenish-Rate";
    private static final String BURST_CAPACITY_HEADER = "X-RateLimit-Burst-Capacity";
    private static final String REFILL_PERIOD_HEADER = "X-RateLimit-Refill-Period-Seconds";

    private static final String TOKEN_BUCKET_SCRIPT = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local replenish_rate = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local refill_period_millis = tonumber(ARGV[4])
            local requested = tonumber(ARGV[5])
            local ttl_millis = tonumber(ARGV[6])

            local bucket = redis.call('HMGET', key, 'tokens', 'timestamp')
            local tokens = tonumber(bucket[1])
            local timestamp = tonumber(bucket[2])

            if tokens == nil then
                tokens = capacity
            end

            if timestamp == nil then
                timestamp = now
            end

            local delta = math.max(0, now - timestamp)
            local filled_tokens = math.min(capacity, tokens + ((delta * replenish_rate) / refill_period_millis))
            local allowed = 0

            if filled_tokens >= requested then
                allowed = 1
                filled_tokens = filled_tokens - requested
            end

            redis.call('HMSET', key, 'tokens', filled_tokens, 'timestamp', now)
            redis.call('PEXPIRE', key, ttl_millis)

            return { allowed, math.floor(filled_tokens) }
            """;

    private final ReactiveStringRedisTemplate redisTemplate;
    private final Clock clock;
    private final DefaultRedisScript<List> rateLimitScript;

    @Autowired
    public AssistantRedisRateLimiter(
            ReactiveStringRedisTemplate redisTemplate,
            ConfigurationService configurationService
    ) {
        this(redisTemplate, configurationService, Clock.systemUTC());
    }

    AssistantRedisRateLimiter(
            ReactiveStringRedisTemplate redisTemplate,
            ConfigurationService configurationService,
            Clock clock
    ) {
        super(Config.class, CONFIGURATION_PROPERTY_NAME, configurationService);
        this.redisTemplate = redisTemplate;
        this.clock = clock;
        this.rateLimitScript = new DefaultRedisScript<>(TOKEN_BUCKET_SCRIPT, List.class);
    }

    @Override
    public Mono<Response> isAllowed(String routeId, String id) {
        Config config = getConfig().getOrDefault(routeId, newConfig());
        String redisKey = KEY_PREFIX + ":" + routeId + ":" + id;
        long now = clock.millis();
        long refillPeriodMillis = config.getRefillPeriodSeconds() * 1000L;
        long ttlMillis = calculateTtlMillis(config, refillPeriodMillis);

        return redisTemplate.execute(
                        rateLimitScript,
                        List.of(redisKey),
                        List.of(
                                String.valueOf(config.getBurstCapacity()),
                                String.valueOf(config.getReplenishRate()),
                                String.valueOf(now),
                                String.valueOf(refillPeriodMillis),
                                String.valueOf(REQUESTED_TOKENS),
                                String.valueOf(ttlMillis)
                        )
                )
                .next()
                .map(result -> buildResponse(result, config))
                .onErrorResume(exception -> {
                    log.error("Redis rate limiter failed for route {} and key {}", routeId, id, exception);
                    return Mono.just(new Response(false, buildHeaders(config, 0)));
                });
    }

    @Override
    public Config newConfig() {
        return new Config();
    }

    private Response buildResponse(List result, Config config) {
        boolean allowed = asLong(result.get(0)) == 1L;
        long remaining = asLong(result.get(1));
        return new Response(allowed, buildHeaders(config, remaining));
    }

    private long calculateTtlMillis(Config config, long refillPeriodMillis) {
        long refillRate = Math.max(1, config.getReplenishRate());
        long burstCapacity = Math.max(1, config.getBurstCapacity());
        return Math.max(refillPeriodMillis, ((burstCapacity * refillPeriodMillis) / refillRate) * 2);
    }

    private Map<String, String> buildHeaders(Config config, long remaining) {
        return Map.of(
                REMAINING_HEADER, String.valueOf(Math.max(0, remaining)),
                REPLENISH_RATE_HEADER, String.valueOf(config.getReplenishRate()),
                BURST_CAPACITY_HEADER, String.valueOf(config.getBurstCapacity()),
                REFILL_PERIOD_HEADER, String.valueOf(config.getRefillPeriodSeconds())
        );
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    @Getter
    @Setter
    public static class Config {
        private int replenishRate = 5;
        private int burstCapacity = 10;
        private int refillPeriodSeconds = 60;
    }
}
