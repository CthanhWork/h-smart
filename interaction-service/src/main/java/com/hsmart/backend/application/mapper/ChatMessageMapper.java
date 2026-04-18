package com.hsmart.backend.application.mapper;

import com.hsmart.backend.application.dto.ChatMessageResponseDTO;
import com.hsmart.backend.domain.entities.ChatMessage;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ChatMessageMapper {
    ChatMessageResponseDTO toResponse(ChatMessage chatMessage);
}
