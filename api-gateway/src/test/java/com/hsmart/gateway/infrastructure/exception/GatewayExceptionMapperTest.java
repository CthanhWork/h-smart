package com.hsmart.gateway.infrastructure.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class GatewayExceptionMapperTest {

    @Test
    void shouldResolveBadGatewayForConnectionFailure() {
        HttpStatus status = GatewayExceptionMapper.resolveStatus(new RuntimeException(new ConnectException("Connection refused")));
        assertEquals(HttpStatus.BAD_GATEWAY, status);
        assertEquals(
                "Gateway could not connect to the downstream service (Bad Gateway)",
                GatewayExceptionMapper.resolveMessage(status)
        );
    }

    @Test
    void shouldResolveGatewayTimeoutForTimeoutFailure() {
        HttpStatus status = GatewayExceptionMapper.resolveStatus(new RuntimeException(new TimeoutException("Timed out")));
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, status);
        assertEquals(
                "Gateway timed out while waiting for the downstream service (Gateway Timeout)",
                GatewayExceptionMapper.resolveMessage(status)
        );
    }

    @Test
    void shouldResolveGatewayTimeoutForSocketTimeoutFailure() {
        HttpStatus status = GatewayExceptionMapper.resolveStatus(new RuntimeException(new SocketTimeoutException("Read timed out")));
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, status);
    }

    @Test
    void shouldRespectResponseStatusExceptionWhenPresent() {
        HttpStatus status = GatewayExceptionMapper.resolveStatus(
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable")
        );
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, status);
        assertEquals(
                "The requested service is temporarily unavailable (Service Unavailable)",
                GatewayExceptionMapper.resolveMessage(status)
        );
    }
}
