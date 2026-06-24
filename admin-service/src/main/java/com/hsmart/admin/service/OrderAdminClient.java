package com.hsmart.admin.service;

import com.hsmart.admin.application.dto.OrderResponseDTO;
import com.hsmart.admin.application.dto.PageResponseDTO;
import org.springframework.data.domain.Pageable;

public interface OrderAdminClient {
    PageResponseDTO<OrderResponseDTO> listAllOrders(String status, Pageable pageable);
    OrderResponseDTO adminCancelOrder(Long orderId);
    OrderResponseDTO adminApproveReturn(Long orderId);
    OrderResponseDTO adminRejectReturn(Long orderId, String reason);
}
