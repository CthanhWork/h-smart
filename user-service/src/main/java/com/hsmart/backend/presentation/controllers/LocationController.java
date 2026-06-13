package com.hsmart.backend.presentation.controllers;

import com.hsmart.backend.application.dto.ApiResponse;
import com.hsmart.backend.application.dto.LocationOptionDTO;
import com.hsmart.backend.service.LocationCatalogService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationCatalogService locationCatalogService;

    @GetMapping("/provinces")
    public ResponseEntity<ApiResponse<List<LocationOptionDTO>>> getProvinces() {
        List<LocationOptionDTO> response = locationCatalogService.getProvinces();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Provinces fetched successfully", response));
    }

    @GetMapping("/districts")
    public ResponseEntity<ApiResponse<List<LocationOptionDTO>>> getDistricts(@RequestParam String provinceCode) {
        List<LocationOptionDTO> response = locationCatalogService.getDistricts(provinceCode);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Districts fetched successfully", response));
    }

    @GetMapping("/wards")
    public ResponseEntity<ApiResponse<List<LocationOptionDTO>>> getWards(
            @RequestParam String provinceCode,
            @RequestParam String districtCode
    ) {
        List<LocationOptionDTO> response = locationCatalogService.getWards(provinceCode, districtCode);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK, "Wards fetched successfully", response));
    }
}
