package com.hsmart.backend.service;

import com.hsmart.backend.domain.entities.ChatMessage;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class MarketplaceScopeGuard {

    public static final String OUT_OF_SCOPE_REPLY = "Toi chi ho tro cac tac vu mua va ban tren H-Smart.";

    private static final List<String> MARKETPLACE_PHRASES = List.of(
            "mua",
            "ban",
            "san pham",
            "mat hang",
            "do cu",
            "gia dung",
            "tim kiem",
            "tim san pham",
            "xem san pham",
            "chi tiet san pham",
            "wishlist",
            "yeu thich",
            "don hang",
            "order",
            "dat hang",
            "tao don",
            "trang thai don",
            "huy don",
            "xac nhan don",
            "hoan tat don",
            "tra gia",
            "offer",
            "nguoi ban",
            "nguoi mua",
            "seller",
            "buyer",
            "van chuyen",
            "giao hang",
            "phi ship",
            "ship",
            "thanh toan",
            "chinh sach",
            "doi tra",
            "hoan tien",
            "dang ky",
            "dang nhap",
            "xac minh email",
            "quen mat khau",
            "dat lai mat khau",
            "dang tin",
            "dang bai",
            "tao tin",
            "sua tin",
            "xoa tin",
            "con hang",
            "het hang"
    );

    private static final List<String> FOLLOW_UP_PHRASES = List.of(
            "tu van them",
            "goi y them",
            "them giup minh",
            "them nua",
            "cai nao",
            "loai nao",
            "cai nay",
            "cai do",
            "san pham do",
            "mat hang do",
            "don do",
            "don nay",
            "re hon",
            "tot hon",
            "bao nhieu",
            "gia sao",
            "duoc khong",
            "on khong",
            "con khong"
    );

    private static final List<String> EXPLICIT_OUT_OF_SCOPE_PHRASES = List.of(
            "thoi tiet",
            "tin tuc",
            "bong da",
            "chinh tri",
            "lap trinh",
            "viet code",
            "giai toan",
            "bai tap",
            "suc khoe",
            "y te",
            "thuoc",
            "du lich",
            "nau an",
            "cong thuc",
            "phim",
            "am nhac",
            "lich su",
            "dia ly"
    );

    private final ProductKeywordExtractor productKeywordExtractor;

    public MarketplaceScopeGuard(ProductKeywordExtractor productKeywordExtractor) {
        this.productKeywordExtractor = productKeywordExtractor;
    }

    public boolean isInScope(String message, List<ChatMessage> history) {
        if (!StringUtils.hasText(message)) {
            return false;
        }

        String normalizedMessage = normalize(message);
        if (containsAny(normalizedMessage, EXPLICIT_OUT_OF_SCOPE_PHRASES)) {
            return false;
        }

        if (matchesMarketplaceRequest(message, normalizedMessage)) {
            return true;
        }

        return isFollowUpToMarketplaceConversation(normalizedMessage, history);
    }

    private boolean isFollowUpToMarketplaceConversation(String normalizedMessage, List<ChatMessage> history) {
        if (!containsAny(normalizedMessage, FOLLOW_UP_PHRASES) || history == null || history.isEmpty()) {
            return false;
        }

        int startIndex = Math.max(0, history.size() - 6);
        for (int index = startIndex; index < history.size(); index++) {
            ChatMessage chatMessage = history.get(index);
            if (chatMessage != null && matchesMarketplaceRequest(chatMessage.getContent(), normalize(chatMessage.getContent()))) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesMarketplaceRequest(String originalMessage, String normalizedMessage) {
        return containsAny(normalizedMessage, MARKETPLACE_PHRASES)
                || !productKeywordExtractor.extractKeywords(originalMessage).isEmpty();
    }

    private boolean containsAny(String value, List<String> phrases) {
        return phrases.stream().anyMatch(value::contains);
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return normalized.replace('đ', 'd');
    }
}
