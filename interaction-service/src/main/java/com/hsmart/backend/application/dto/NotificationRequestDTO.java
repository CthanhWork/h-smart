package com.hsmart.backend.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRequestDTO {

    @NotBlank
    private String userId;

    private String title;

    @NotBlank
    private String type;

    @NotBlank
    private String message;

    private Long productId;
    private Long orderId;
    private Long offerId;
    private String senderId;
}
