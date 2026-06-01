package com.hsmart.order.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;

public record GhtkCreateOrderRequestDTO(
        List<Product> products,
        Order order
) {
    public record Product(
            String name,
            BigDecimal price,
            BigDecimal weight,
            Integer quantity,
            @JsonProperty("product_code") String productCode
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Order(
            String id,
            @JsonProperty("pick_name") String pickName,
            @JsonProperty("pick_address") String pickAddress,
            @JsonProperty("pick_province") String pickProvince,
            @JsonProperty("pick_district") String pickDistrict,
            @JsonProperty("pick_ward") String pickWard,
            @JsonProperty("pick_tel") String pickTel,
            String name,
            String address,
            String province,
            String district,
            String ward,
            String street,
            String hamlet,
            String tel,
            @JsonProperty("is_freeship") Integer isFreeship,
            @JsonProperty("pick_money") BigDecimal pickMoney,
            BigDecimal value
    ) {
    }
}
