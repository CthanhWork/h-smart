package com.hsmart.order.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRequestDTO {
    private String userId;
    private String title;
    private String type;
    private String message;
    private Long productId;
    private Long orderId;
    private Long offerId;
    private String senderId;
}
