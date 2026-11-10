package com.woodlogix.desktop.api.mapper;

import com.woodlogix.application.customer.CustomerRow;
import com.woodlogix.desktop.api.dto.CustomerRequest;
import com.woodlogix.desktop.api.dto.CustomerResponse;
import com.woodlogix.desktop.service.CustomerData;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface CustomerMapper {
    CustomerRequest toRequest(CustomerData data);

    CustomerRow toRow(CustomerResponse response);
}
