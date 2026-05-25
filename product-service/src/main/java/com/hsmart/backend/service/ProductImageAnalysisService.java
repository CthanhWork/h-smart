package com.hsmart.backend.service;

import com.hsmart.backend.application.dto.ImageAnalysisResponseDTO;
import org.springframework.web.multipart.MultipartFile;

public interface ProductImageAnalysisService {
    ImageAnalysisResponseDTO analyzeImage(MultipartFile file);
}
