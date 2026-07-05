package com.hsmart.order.infrastructure.validation;

import com.hsmart.order.application.exceptions.OrderStateException;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class FileValidatorTest {

    private FileValidator fileValidator;

    @BeforeEach
    void setUp() {
        fileValidator = new FileValidator();
    }

    @Test
    void shouldAcceptValidJpegImage() {
        MockMultipartFile validImage = new MockMultipartFile(
                "file",
                "test.jpg",
                "image/jpeg",
                createValidImageBytes()
        );

        assertThatCode(() -> fileValidator.validateImage(validImage))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectEmptyFileList() {
        assertThatThrownBy(() -> fileValidator.validateImages(Collections.emptyList()))
                .isInstanceOf(OrderStateException.class)
                .hasMessageContaining("At least one image is required");
    }

    @Test
    void shouldRejectNullFile() {
        assertThatThrownBy(() -> fileValidator.validateImages(null))
                .isInstanceOf(OrderStateException.class)
                .hasMessageContaining("At least one image is required");
    }

    @Test
    void shouldRejectOversizedFile() {
        byte[] largeContent = new byte[11 * 1024 * 1024]; // 11MB
        MockMultipartFile oversizedFile = new MockMultipartFile(
                "file",
                "large.jpg",
                "image/jpeg",
                largeContent
        );

        assertThatThrownBy(() -> fileValidator.validateImage(oversizedFile))
                .isInstanceOf(OrderStateException.class)
                .hasMessageContaining("exceeds maximum allowed size");
    }

    @Test
    void shouldRejectInvalidContentType() {
        MockMultipartFile invalidType = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "not an image".getBytes()
        );

        assertThatThrownBy(() -> fileValidator.validateImage(invalidType))
                .isInstanceOf(OrderStateException.class)
                .hasMessageContaining("Invalid file type");
    }

    @Test
    void shouldRejectInvalidExtension() {
        MockMultipartFile invalidExt = new MockMultipartFile(
                "file",
                "test.exe",
                "image/jpeg",
                createValidImageBytes()
        );

        assertThatThrownBy(() -> fileValidator.validateImage(invalidExt))
                .isInstanceOf(OrderStateException.class)
                .hasMessageContaining("Invalid file extension");
    }

    @Test
    void shouldAcceptValidPngImage() {
        MockMultipartFile pngImage = new MockMultipartFile(
                "file",
                "test.png",
                "image/png",
                createValidImageBytes()
        );

        assertThatCode(() -> fileValidator.validateImage(pngImage))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptValidWebpImage() {
        MockMultipartFile webpImage = new MockMultipartFile(
                "file",
                "test.webp",
                "image/webp",
                createValidImageBytes()
        );

        assertThatCode(() -> fileValidator.validateImage(webpImage))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldValidateMultipleImagesSuccessfully() {
        List<MultipartFile> images = List.of(
                new MockMultipartFile("file1", "test1.jpg", "image/jpeg", createValidImageBytes()),
                new MockMultipartFile("file2", "test2.png", "image/png", createValidImageBytes())
        );

        assertThatCode(() -> fileValidator.validateImages(images))
                .doesNotThrowAnyException();
    }

    private byte[] createValidImageBytes() {
        // Create a minimal valid JPEG image (1x1 pixel)
        return new byte[]{
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46,
                0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
                (byte) 0xFF, (byte) 0xDB, 0x00, 0x43, 0x00, 0x08, 0x06, 0x06, 0x07, 0x06,
                0x05, 0x08, 0x07, 0x07, 0x07, 0x09, 0x09, 0x08, 0x0A, 0x0C, 0x14, 0x0D,
                0x0C, 0x0B, 0x0B, 0x0C, 0x19, 0x12, 0x13, 0x0F, 0x14, 0x1D, 0x1A, 0x1F,
                0x1E, 0x1D, 0x1A, 0x1C, 0x1C, 0x20, 0x24, 0x2E, 0x27, 0x20, 0x22, 0x2C,
                0x23, 0x1C, 0x1C, 0x28, 0x37, 0x29, 0x2C, 0x30, 0x31, 0x34, 0x34, 0x34,
                0x1F, 0x27, 0x39, 0x3D, 0x38, 0x32, 0x3C, 0x2E, 0x33, 0x34, 0x32,
                (byte) 0xFF, (byte) 0xC0, 0x00, 0x0B, 0x08, 0x00, (byte) 0xC8, 0x00,
                (byte) 0xC8, 0x01, 0x01, 0x11, 0x00, (byte) 0xFF, (byte) 0xC4, 0x00,
                0x14, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x03, (byte) 0xFF, (byte) 0xC4,
                0x00, 0x14, 0x10, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, (byte) 0xFF, (byte) 0xDA,
                0x00, 0x08, 0x01, 0x01, 0x00, 0x00, 0x3F, 0x00, 0x7F, (byte) 0xFF, (byte) 0xD9
        };
    }
}
