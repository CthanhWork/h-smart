package com.hsmart.review.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewCreatedEvent {
    private Long reviewId;
    private String sellerId;
    private Integer rating;
}
