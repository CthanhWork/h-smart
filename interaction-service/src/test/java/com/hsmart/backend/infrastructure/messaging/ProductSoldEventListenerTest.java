package com.hsmart.backend.infrastructure.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hsmart.backend.application.dto.NotificationRequestDTO;
import com.hsmart.backend.application.dto.ProductSoldEvent;
import com.hsmart.backend.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductSoldEventListenerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ProductSoldEventListener listener;

    @Test
    void handleProductSoldEventShouldCreateSellerNotification() {
        ProductSoldEvent event = ProductSoldEvent.builder()
                .productId(12L)
                .sellerId("seller-1")
                .title("Microwave")
                .build();

        listener.handleProductSoldEvent(event);

        ArgumentCaptor<NotificationRequestDTO> captor = ArgumentCaptor.forClass(NotificationRequestDTO.class);
        verify(notificationService).createNotification(captor.capture());

        NotificationRequestDTO request = captor.getValue();
        assertEquals("seller-1", request.getUserId());
        assertEquals("Sản phẩm đã bán", request.getTitle());
        assertEquals("PRODUCT_SOLD", request.getType());
        assertEquals(12L, request.getProductId());
        assertEquals("Sản phẩm \"Microwave\" của bạn đã được đánh dấu là đã bán.", request.getMessage());
    }

    @Test
    void handleProductSoldEventShouldIgnoreInvalidEvent() {
        ProductSoldEvent event = ProductSoldEvent.builder()
                .productId(12L)
                .title("Microwave")
                .build();

        listener.handleProductSoldEvent(event);

        verify(notificationService, never()).createNotification(org.mockito.ArgumentMatchers.any());
    }
}
