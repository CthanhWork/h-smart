package com.hsmart.backend.presentation.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hsmart.backend.application.dto.ApiResponse;
import com.hsmart.backend.application.dto.ProductDescriptionRequest;
import com.hsmart.backend.application.dto.ProductDescriptionResponse;
import com.hsmart.backend.application.exceptions.MissingUserContextException;
import com.hsmart.backend.infrastructure.context.UserContextHolder;
import com.hsmart.backend.service.AssistantService;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AssistantControllerTest {

    @AfterEach
    void clearUserContext() {
        UserContextHolder.clear();
    }

    @Test
    void generateDescriptionShouldReturnStandardApiResponse() {
        AssistantService assistantService = mock(AssistantService.class);
        AssistantController controller = new AssistantController(assistantService);
        ProductDescriptionRequest request = validRequest();
        ProductDescriptionResponse generated = new ProductDescriptionResponse("- Sofa dep");
        UserContextHolder.setCurrentUserId("user-1");
        when(assistantService.generateProductDescription(request)).thenReturn(generated);

        ResponseEntity<ApiResponse<ProductDescriptionResponse>> response = controller.generateDescription(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(200, response.getBody().getStatus());
        assertEquals("Description generated successfully", response.getBody().getMessage());
        assertEquals("- Sofa dep", response.getBody().getData().generatedDescription());
        verify(assistantService).generateProductDescription(request);
    }

    @Test
    void generateDescriptionShouldRequireAuthenticatedUserContext() {
        AssistantService assistantService = mock(AssistantService.class);
        AssistantController controller = new AssistantController(assistantService);

        assertThrows(MissingUserContextException.class, () -> controller.generateDescription(validRequest()));
    }

    private ProductDescriptionRequest validRequest() {
        return ProductDescriptionRequest.builder()
                .productName("Sofa")
                .category("Furniture")
                .condition("Used")
                .price(BigDecimal.valueOf(1500000))
                .build();
    }
}
