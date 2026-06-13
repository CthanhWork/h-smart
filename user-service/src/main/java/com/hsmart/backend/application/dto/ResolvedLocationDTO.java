package com.hsmart.backend.application.dto;

public record ResolvedLocationDTO(
        String provinceCode,
        String provinceName,
        String districtCode,
        String districtName,
        String wardCode,
        String wardName
) {
}
