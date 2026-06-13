package com.hsmart.backend.service.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hsmart.backend.application.dto.LocationOptionDTO;
import com.hsmart.backend.application.dto.ResolvedLocationDTO;
import com.hsmart.backend.infrastructure.exception.InvalidLocationException;
import com.hsmart.backend.infrastructure.exception.LocationCatalogUnavailableException;
import com.hsmart.backend.service.LocationCatalogService;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
@RequiredArgsConstructor
public class LocationCatalogServiceImpl implements LocationCatalogService {

    private static final ParameterizedTypeReference<List<ProvinceNode>> PROVINCE_LIST_TYPE =
            new ParameterizedTypeReference<>() {};

    private final @Qualifier("locationCatalogRestClient") RestClient restClient;

    private volatile List<LocationOptionDTO> provincesCache = List.of();
    private final Map<String, List<LocationOptionDTO>> districtsCache = new ConcurrentHashMap<>();
    private final Map<String, List<LocationOptionDTO>> wardsCache = new ConcurrentHashMap<>();

    @Override
    public List<LocationOptionDTO> getProvinces() {
        if (!provincesCache.isEmpty()) {
            return provincesCache;
        }

        synchronized (this) {
            if (!provincesCache.isEmpty()) {
                return provincesCache;
            }
            provincesCache = fetchProvinceOptions();
            return provincesCache;
        }
    }

    @Override
    public List<LocationOptionDTO> getDistricts(String provinceCode) {
        String normalizedProvinceCode = normalizeCode(provinceCode, "Province code is required");
        return districtsCache.computeIfAbsent(normalizedProvinceCode, this::fetchDistrictOptions);
    }

    @Override
    public List<LocationOptionDTO> getWards(String provinceCode, String districtCode) {
        String normalizedProvinceCode = normalizeCode(provinceCode, "Province code is required");
        String normalizedDistrictCode = normalizeCode(districtCode, "District code is required");

        boolean districtBelongsToProvince = getDistricts(normalizedProvinceCode).stream()
                .anyMatch(option -> option.getCode().equals(normalizedDistrictCode));
        if (!districtBelongsToProvince) {
            throw new InvalidLocationException("District code does not belong to the selected province");
        }

        return wardsCache.computeIfAbsent(normalizedDistrictCode, this::fetchWardOptions);
    }

    @Override
    public ResolvedLocationDTO resolveLocation(String provinceCode, String districtCode, String wardCode) {
        String normalizedProvinceCode = normalizeCode(provinceCode, "Province code is required");
        String normalizedDistrictCode = normalizeCode(districtCode, "District code is required");
        String normalizedWardCode = normalizeCode(wardCode, "Ward code is required");

        ProvinceNode province = fetchProvince(normalizedProvinceCode, 2);
        DistrictNode district = safeList(province.districts()).stream()
                .filter(item -> normalizedDistrictCode.equals(asCode(item.code())))
                .findFirst()
                .orElseThrow(() -> new InvalidLocationException("District code does not belong to the selected province"));

        DistrictNode districtWithWards = fetchDistrict(normalizedDistrictCode, 2);
        WardNode ward = safeList(districtWithWards.wards()).stream()
                .filter(item -> normalizedWardCode.equals(asCode(item.code())))
                .findFirst()
                .orElseThrow(() -> new InvalidLocationException("Ward code does not belong to the selected district"));

        return new ResolvedLocationDTO(
                normalizedProvinceCode,
                province.name(),
                normalizedDistrictCode,
                district.name(),
                normalizedWardCode,
                ward.name()
        );
    }

    private List<LocationOptionDTO> fetchProvinceOptions() {
        try {
            List<ProvinceNode> response = restClient.get()
                    .uri("/p/")
                    .retrieve()
                    .body(PROVINCE_LIST_TYPE);

            return safeList(response).stream()
                    .map(item -> toOption(item.code(), item.name()))
                    .collect(Collectors.toList());
        } catch (RestClientResponseException exception) {
            throw handleRemoteFailure(exception, "Location catalog is temporarily unavailable");
        } catch (RestClientException exception) {
            throw new LocationCatalogUnavailableException("Location catalog is temporarily unavailable", exception);
        }
    }

    private List<LocationOptionDTO> fetchDistrictOptions(String provinceCode) {
        ProvinceNode province = fetchProvince(provinceCode, 2);
        return safeList(province.districts()).stream()
                .map(item -> toOption(item.code(), item.name()))
                .collect(Collectors.toList());
    }

    private List<LocationOptionDTO> fetchWardOptions(String districtCode) {
        DistrictNode district = fetchDistrict(districtCode, 2);
        return safeList(district.wards()).stream()
                .map(item -> toOption(item.code(), item.name()))
                .collect(Collectors.toList());
    }

    private ProvinceNode fetchProvince(String provinceCode, int depth) {
        try {
            ProvinceNode response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/p/{code}").queryParam("depth", depth).build(provinceCode))
                    .retrieve()
                    .body(ProvinceNode.class);
            if (response == null || !StringUtils.hasText(response.name())) {
                throw new InvalidLocationException("Province code was not found");
            }
            return response;
        } catch (RestClientResponseException exception) {
            throw handleRemoteFailure(exception, "Province code was not found");
        } catch (RestClientException exception) {
            throw new LocationCatalogUnavailableException("Location catalog is temporarily unavailable", exception);
        }
    }

    private DistrictNode fetchDistrict(String districtCode, int depth) {
        try {
            DistrictNode response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/d/{code}").queryParam("depth", depth).build(districtCode))
                    .retrieve()
                    .body(DistrictNode.class);
            if (response == null || !StringUtils.hasText(response.name())) {
                throw new InvalidLocationException("District code was not found");
            }
            return response;
        } catch (RestClientResponseException exception) {
            throw handleRemoteFailure(exception, "District code was not found");
        } catch (RestClientException exception) {
            throw new LocationCatalogUnavailableException("Location catalog is temporarily unavailable", exception);
        }
    }

    private LocationOptionDTO toOption(Integer code, String name) {
        return LocationOptionDTO.builder()
                .code(asCode(code))
                .name(name)
                .build();
    }

    private String normalizeCode(String code, String message) {
        if (!StringUtils.hasText(code)) {
            throw new InvalidLocationException(message);
        }
        return code.trim();
    }

    private String asCode(Integer code) {
        return code == null ? "" : String.valueOf(code);
    }

    private <T> List<T> safeList(List<T> source) {
        return source == null ? Collections.emptyList() : source;
    }

    private RuntimeException handleRemoteFailure(RestClientResponseException exception, String notFoundMessage) {
        if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
            return new InvalidLocationException(notFoundMessage);
        }
        return new LocationCatalogUnavailableException("Location catalog is temporarily unavailable", exception);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ProvinceNode(Integer code, String name, List<DistrictNode> districts) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DistrictNode(Integer code, String name, List<WardNode> wards) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WardNode(Integer code, String name) {
    }
}
