package com.hsmart.backend.presentation.websocket;

import com.hsmart.backend.application.dto.ChatMessageRequestDTO;
import com.hsmart.backend.application.exceptions.MissingUserContextException;
import com.hsmart.backend.service.ChatService;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final ChatService chatService;

    @MessageMapping("/chat.send")
    public void sendMessage(@Valid @Payload ChatMessageRequestDTO request, Principal principal) {
        if (principal == null || !StringUtils.hasText(principal.getName())) {
            throw new MissingUserContextException();
        }

        chatService.processIncomingMessage(principal.getName(), request);
        log.info("Processed WebSocket chat message from {} to {} for product {}",
                principal.getName(), request.getReceiverId(), request.getProductId());
    }
}
