package com.hsmart.backend.service.impl;

import com.hsmart.backend.domain.entities.User;
import com.hsmart.backend.infrastructure.exception.ResourceNotFoundException;
import com.hsmart.backend.infrastructure.persistence.UserRepository;
import com.hsmart.backend.service.UserTrustScoreService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserTrustScoreServiceImpl implements UserTrustScoreService {

    private final UserRepository userRepository;

    @Override
    public void applyReviewRating(String sellerId, int rating) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Review rating must be between 1 and 5");
        }

        User seller = userRepository.findByUsername(sellerId)
                .orElseThrow(() -> new ResourceNotFoundException("Seller not found"));

        long currentCount = seller.getReviewCount() == null ? 0L : seller.getReviewCount();
        BigDecimal currentScore = seller.getTrustScore() == null ? BigDecimal.ZERO : seller.getTrustScore();
        BigDecimal currentTotal = currentScore
                .multiply(BigDecimal.valueOf(currentCount));
        long newCount = currentCount + 1;
        BigDecimal newScore = currentTotal.add(BigDecimal.valueOf(rating))
                .divide(BigDecimal.valueOf(newCount), 2, RoundingMode.HALF_UP);

        seller.setReviewCount(newCount);
        seller.setTrustScore(newScore);
        userRepository.save(seller);
        log.info("Updated trust score for seller {} to {} after {} reviews", sellerId, newScore, newCount);
    }
}
