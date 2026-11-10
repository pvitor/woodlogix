package com.woodlogix.server.api.mapper;

import com.woodlogix.application.customer.CustomerInput;
import com.woodlogix.application.customer.CustomerRow;
import com.woodlogix.server.api.dto.CustomerRequest;
import com.woodlogix.server.api.dto.CustomerResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface CustomerMapper {
    CustomerInput toInput(CustomerRequest request);

    CustomerResponse toResponse(CustomerRow row);

    List<CustomerResponse> toResponses(List<CustomerRow> rows);
}
