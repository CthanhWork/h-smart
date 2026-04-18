package com.hsmart.backend.application.mapper;

import com.hsmart.backend.application.dto.NotificationResponseDTO;
import com.hsmart.backend.domain.entities.Notification;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NotificationMapper {
    NotificationResponseDTO toResponse(Notification notification);
}
