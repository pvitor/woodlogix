package com.woodlogix.application.customer;

import java.util.List;
import java.util.Optional;

public interface CustomerCatalogPort {
    CustomerRow create(CustomerInput input);

    Optional<CustomerRow> update(String id, CustomerInput input);

    List<CustomerRow> findAll();

    List<CustomerRow> searchByAnyField(String term);

    List<CustomerRow> searchByName(String name);

    boolean exists(String id);

    void deleteAndFlush(String id);
}
