package com.hsmart.admin.infrastructure.config;

public record ServiceClientProperties(String baseUrl, int connectTimeoutMs, int readTimeoutMs) {
}
