package com.hsmart.backend.infrastructure.security;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class InternalSecurityFilter extends OncePerRequestFilter {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";
    private static final String UNAUTHORIZED_MESSAGE = "Invalid internal service credentials";

    private final boolean enabled;
    private final String internalSharedSecret;

    public InternalSecurityFilter(
            @Value("${internal.security.enabled:true}") boolean enabled,
            @Value("${internal.security.secret:}") String internalSharedSecret
    ) {
        this.enabled = enabled;
        this.internalSharedSecret = internalSharedSecret;
    }

    @PostConstruct
    void validateConfiguration() {
        if (enabled && !StringUtils.hasText(internalSharedSecret)) {
            throw new IllegalStateException("INTERNAL_SHARED_SECRET must be configured for user-service");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!enabled || hasValidInternalSecret(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn(
                "Rejected internal request from source IP {} to path {} because X-Internal-Secret is missing or invalid",
                resolveSourceIp(request),
                request.getRequestURI()
        );
        writeUnauthorizedResponse(response);
    }

    private boolean hasValidInternalSecret(HttpServletRequest request) {
        String providedSecret = request.getHeader(INTERNAL_SECRET_HEADER);
        if (!StringUtils.hasText(providedSecret)) {
            return false;
        }
        return MessageDigest.isEqual(
                internalSharedSecret.getBytes(StandardCharsets.UTF_8),
                providedSecret.trim().getBytes(StandardCharsets.UTF_8)
        );
    }

    private String resolveSourceIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeUnauthorizedResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"status\":401,\"message\":\"" + UNAUTHORIZED_MESSAGE + "\",\"data\":null}");
    }
}
