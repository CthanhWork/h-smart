package com.hsmart.backend.service.impl;

import com.hsmart.backend.application.dto.DetectionDTO;
import com.hsmart.backend.application.dto.PredictResponseDTO;
import com.hsmart.backend.application.exceptions.AiServiceUnavailableException;
import com.hsmart.backend.application.exceptions.FileProcessingException;
import com.hsmart.backend.infrastructure.config.AiServiceProperties;
import com.hsmart.backend.service.VisionService;
import java.io.IOException;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class VisionServiceImpl implements VisionService {

    private final RestTemplate restTemplate;
    private final AiServiceProperties aiServiceProperties;

    @Override
    public PredictResponseDTO detectObjects(MultipartFile file) {
        try {
            byte[] imageBytes = file.getBytes();
            String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.jpg";
            String contentType = file.getContentType() != null ? file.getContentType() : MediaType.IMAGE_JPEG_VALUE;

            ByteArrayResource fileResource = new ByteArrayResource(imageBytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };

            HttpHeaders fileHeaders = new HttpHeaders();
            fileHeaders.setContentType(MediaType.parseMediaType(contentType));
            HttpEntity<ByteArrayResource> filePart = new HttpEntity<>(fileResource, fileHeaders);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", filePart);

            HttpHeaders requestHeaders = new HttpHeaders();
            requestHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, requestHeaders);

            PredictResponseDTO response = restTemplate.postForObject(
                    aiServiceProperties.baseUrl() + "/api/v1/predict",
                    requestEntity,
                    PredictResponseDTO.class
            );

            if (response == null) {
                return PredictResponseDTO.builder()
                        .numDetections(0)
                        .detections(new ArrayList<DetectionDTO>())
                        .build();
            }

            if (response.getDetections() == null) {
                response.setDetections(new ArrayList<>());
            }
            if (response.getNumDetections() == null) {
                response.setNumDetections(response.getDetections().size());
            }

            return response;
        } catch (ResourceAccessException exception) {
            throw new AiServiceUnavailableException("AI service did not respond", exception);
        } catch (RestClientException exception) {
            throw new AiServiceUnavailableException("Failed to call AI service", exception);
        } catch (IOException exception) {
            throw new FileProcessingException("Unable to read uploaded image", exception);
        }
    }
}
