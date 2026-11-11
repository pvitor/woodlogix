package com.woodlogix.desktop.service;

import com.woodlogix.application.environment.EnvironmentRow;
import com.woodlogix.core.enums.RoomType;
import com.woodlogix.desktop.api.client.EnvironmentApiClient;
import com.woodlogix.desktop.api.mapper.EnvironmentMapper;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Objects;

@Service
@Validated
public class RemoteEnvironmentService {
    private final EnvironmentApiClient client;
    private final EnvironmentMapper mapper;

    public RemoteEnvironmentService(
            EnvironmentApiClient client,
            EnvironmentMapper mapper
    ) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    public List<EnvironmentRow> findAll() {
        return mapper.toRows(client.findAll());
    }

    public List<EnvironmentRow> findByCustomer(String customerId) {
        return mapper.toRows(client.findByCustomer(customerId));
    }

    public List<EnvironmentRow> createForCustomer(
            String customerId,
            List<RoomType> roomTypes,
            String customRoomType
    ) {
        return mapper.toRows(client.createForCustomer(
                customerId, mapper.toAddRequest(roomTypes, customRoomType)));
    }

    public List<EnvironmentRow> replaceCustomerEnvironments(
            String customerId,
            List<String> roomTypes
    ) {
        return mapper.toRows(client.replaceCustomerEnvironments(
                customerId, mapper.toReplaceRequest(roomTypes)));
    }

    public void delete(String environmentId) {
        client.delete(environmentId);
    }

    public void deleteAllByCustomer(String customerId) {
        client.deleteAllByCustomer(customerId);
    }
}
