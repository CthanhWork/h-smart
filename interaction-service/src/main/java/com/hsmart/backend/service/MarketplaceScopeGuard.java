package com.hsmart.backend.service;

import com.hsmart.backend.domain.entities.ChatMessage;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class MarketplaceScopeGuard {

    public static final String OUT_OF_SCOPE_REPLY =
            "Mình là trợ lý H-Smart nên chủ yếu hỗ trợ các vấn đề về mua bán, đơn hàng, giao nhận "
            + "và cách sử dụng sàn H-Smart. Bạn cần mình giúp gì về việc mua hoặc bán trên H-Smart không?";

    /**
     * Chỉ chặn những chủ đề rõ ràng không liên quan tới sàn. Mọi nội dung khác — kể cả lời chào,
     * trò chuyện thông thường và các câu hỏi ngoài lề nhưng vẫn gắn với việc dùng sàn (cách nhận
     * hàng, đóng gói, kiểm tra hàng, an toàn giao dịch...) — đều được chuyển tới trợ lý để trả lời
     * tự nhiên dựa trên ngữ cảnh H-Smart. Việc giữ bot bám phạm vi H-Smart do system prompt đảm nhiệm.
     */
    private static final List<String> EXPLICIT_OUT_OF_SCOPE_PHRASES = List.of(
            "thoi tiet",
            "bong da",
            "chinh tri",
            "lap trinh",
            "viet code",
            "giai toan",
            "bai tap ve nha",
            "du lich",
            "phim anh",
            "am nhac",
            "tu vi",
            "xo so"
    );

    public boolean isInScope(String message, List<ChatMessage> history) {
        if (!StringUtils.hasText(message)) {
            return false;
        }

        // Mặc định cho phép. Chỉ từ chối khi câu hỏi rơi đúng vào chủ đề ngoài lề rõ ràng,
        // không liên quan gì tới việc mua bán hay sử dụng sàn H-Smart.
        String normalizedMessage = normalize(message);
        return !containsAny(normalizedMessage, EXPLICIT_OUT_OF_SCOPE_PHRASES);
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
