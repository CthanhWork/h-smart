package com.hsmart.order.infrastructure.validation;

import com.hsmart.order.application.exceptions.OrderStateException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Component
public class FileValidator {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp"
    );

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "webp"
    );

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final int MAX_IMAGE_DIMENSION = 4096; // 4K resolution
    private static final int MIN_IMAGE_DIMENSION = 100; // Minimum 100px

    public void validateImages(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new OrderStateException("At least one image is required");
        }

        for (MultipartFile file : files) {
            validateImage(file);
        }
    }

    public void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new OrderStateException("Empty file is not allowed");
        }

        // Check file size
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new OrderStateException("File size exceeds maximum allowed size of 10MB");
        }

        // Check content type
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new OrderStateException("Invalid file type. Only JPEG, PNG, and WebP images are allowed");
        }

        // Check file extension
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null) {
            String extension = getFileExtension(originalFilename);
            if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
                throw new OrderStateException("Invalid file extension. Only .jpg, .jpeg, .png, and .webp are allowed");
            }
        }

        // Validate actual image dimensions
        try {
            BufferedImage image = ImageIO.read(file.getInputStream());
            if (image == null) {
                throw new OrderStateException("File is not a valid image");
            }

            int width = image.getWidth();
            int height = image.getHeight();

            if (width < MIN_IMAGE_DIMENSION || height < MIN_IMAGE_DIMENSION) {
                throw new OrderStateException("Image dimensions must be at least " + MIN_IMAGE_DIMENSION + "x" + MIN_IMAGE_DIMENSION + " pixels");
            }

            if (width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION) {
                throw new OrderStateException("Image dimensions must not exceed " + MAX_IMAGE_DIMENSION + "x" + MAX_IMAGE_DIMENSION + " pixels");
            }

        } catch (IOException ex) {
            log.error("Failed to read image file: {}", ex.getMessage());
            throw new OrderStateException("Failed to validate image file: " + ex.getMessage());
        }
    }

    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "";
        }
        return filename.substring(lastDotIndex + 1);
    }
}
