package com.hsmart.admin.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
public class AdminRoleFilter extends OncePerRequestFilter {

    private static final String USER_ROLE_HEADER = "X-User-Role";
    private static final String FORBIDDEN_MESSAGE = "Admin role is required";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/v1/admin/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String role = request.getHeader(USER_ROLE_HEADER);
        if (StringUtils.hasText(role) && "ADMIN".equalsIgnoreCase(role.trim())) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn(
                "Rejected admin request from source IP {} to path {} because the caller role is not ADMIN",
                resolveSourceIp(request),
                request.getRequestURI()
        );
        writeForbiddenResponse(response);
    }

    private String resolveSourceIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeForbiddenResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"status\":403,\"message\":\"" + FORBIDDEN_MESSAGE + "\",\"data\":null}");
    }
}
