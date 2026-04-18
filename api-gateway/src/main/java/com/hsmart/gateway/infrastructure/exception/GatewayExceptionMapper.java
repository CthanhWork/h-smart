package com.hsmart.gateway.infrastructure.exception;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class GatewayExceptionMapper {

    private GatewayExceptionMapper() {
    }

    public static HttpStatus resolveStatus(Throwable throwable) {
        if (throwable instanceof ResponseStatusException responseStatusException) {
            HttpStatus status = HttpStatus.resolve(responseStatusException.getStatusCode().value());
            if (status != null) {
                return status;
            }
        }

        if (hasCause(throwable, TimeoutException.class) || hasCause(throwable, SocketTimeoutException.class)) {
            return HttpStatus.GATEWAY_TIMEOUT;
        }

        if (hasCause(throwable, ConnectException.class)
                || hasCause(throwable, UnknownHostException.class)
                || hasCause(throwable, NoRouteToHostException.class)) {
            return HttpStatus.BAD_GATEWAY;
        }

        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    public static String resolveMessage(HttpStatus status) {
        return switch (status) {
            case BAD_GATEWAY -> "Gateway could not connect to the downstream service (Bad Gateway)";
            case GATEWAY_TIMEOUT -> "Gateway timed out while waiting for the downstream service (Gateway Timeout)";
            case SERVICE_UNAVAILABLE -> "The requested service is temporarily unavailable (Service Unavailable)";
            case NOT_FOUND -> "No route found for the requested resource";
            case UNAUTHORIZED -> "Unauthorized";
            case FORBIDDEN -> "Forbidden";
            default -> "Internal server error";
        };
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> targetType) {
        Throwable current = throwable;
        while (current != null) {
            if (targetType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
