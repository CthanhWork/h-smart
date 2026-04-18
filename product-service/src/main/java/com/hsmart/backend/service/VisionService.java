package com.hsmart.backend.service;

import com.hsmart.backend.application.dto.PredictResponseDTO;
import org.springframework.web.multipart.MultipartFile;

public interface VisionService {
    PredictResponseDTO detectObjects(MultipartFile file);
}
