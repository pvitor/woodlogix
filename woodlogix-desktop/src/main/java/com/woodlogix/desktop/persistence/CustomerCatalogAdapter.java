package com.woodlogix.desktop.persistence;

import com.woodlogix.application.customer.CustomerCatalogPort;
import com.woodlogix.application.customer.CustomerInput;
import com.woodlogix.application.customer.CustomerRow;
import com.woodlogix.desktop.persistence.entity.CustomerEntity;
import com.woodlogix.desktop.persistence.repository.CustomerRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class CustomerCatalogAdapter implements CustomerCatalogPort {
    private final CustomerRepository customers;

    public CustomerCatalogAdapter(CustomerRepository customers) {
        this.customers = customers;
    }

    @Override
    public CustomerRow create(CustomerInput input) {
        return toRow(customers.saveAndFlush(new CustomerEntity(
                input.fullName(), input.phone(), input.email(), input.address())));
    }

    @Override
    public Optional<CustomerRow> update(String id, CustomerInput input) {
        return customers.findById(id).map(entity -> {
            entity.update(input.fullName(), input.phone(), input.email(), input.address());
            return toRow(customers.saveAndFlush(entity));
        });
    }

    @Override
    public List<CustomerRow> findAll() {
        return customers.findAll().stream().map(CustomerCatalogAdapter::toRow).toList();
    }

    @Override
    public List<CustomerRow> searchByAnyField(String term) {
        return customers
                .findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrPhoneContainingIgnoreCase(
                        term, term, term)
                .stream().map(CustomerCatalogAdapter::toRow).toList();
    }

    @Override
    public List<CustomerRow> searchByName(String name) {
        return customers.findByFullNameContainingIgnoreCase(name).stream()
                .map(CustomerCatalogAdapter::toRow).toList();
    }

    @Override
    public boolean exists(String id) {
        return customers.existsById(id);
    }

    @Override
    public void deleteAndFlush(String id) {
        customers.deleteById(id);
        customers.flush();
    }

    private static CustomerRow toRow(CustomerEntity customer) {
        return new CustomerRow(customer.getId(), customer.getFullName(), customer.getPhone(),
                customer.getEmail(), customer.getAddress());
    }
}
