package com.hsmart.backend.service;

import com.hsmart.backend.application.dto.LocationOptionDTO;
import com.hsmart.backend.application.dto.ResolvedLocationDTO;
import java.util.List;

public interface LocationCatalogService {
    List<LocationOptionDTO> getProvinces();
    List<LocationOptionDTO> getDistricts(String provinceCode);
    List<LocationOptionDTO> getWards(String provinceCode, String districtCode);
    ResolvedLocationDTO resolveLocation(String provinceCode, String districtCode, String wardCode);
}
