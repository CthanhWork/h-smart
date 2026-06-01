package com.hsmart.admin.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UserStatusUpdateRequestDTO(@JsonProperty("isActive") boolean isActive) {
}
