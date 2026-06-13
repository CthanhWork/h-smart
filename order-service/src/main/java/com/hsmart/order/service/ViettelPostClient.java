package com.hsmart.order.service;

import com.hsmart.order.domain.entities.DeliveryMethod;

public interface ViettelPostClient extends ShippingProviderClient {
    @Override
    default DeliveryMethod deliveryMethod() {
        return DeliveryMethod.VIETTEL_POST;
    }
}
