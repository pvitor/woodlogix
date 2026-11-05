package com.woodlogix.desktop.service;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import com.woodlogix.desktop.persistence.entity.CustomerEntity;
import com.woodlogix.desktop.persistence.repository.CustomerRepository;
import com.woodlogix.desktop.service.event.CustomerChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Objects;

@Service
@Validated
public class CustomerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CustomerService.class);

    private final CustomerRepository customerRepository;
    private final ApplicationEventPublisher eventPublisher;

    public CustomerService(
            CustomerRepository customerRepository, ApplicationEventPublisher eventPublisher
    ) {
        this.customerRepository = Objects.requireNonNull(
                customerRepository, "customerRepository must not be null"
        );
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Transactional(readOnly = true)
    public List<CustomerRow> findAll() {
        return customerRepository.findAll().stream()
                .map(this::toRow)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CustomerRow> search(String searchTerm) {
        if (searchTerm == null || searchTerm.isBlank()) {
            return findAll();
        }
        String term = searchTerm.trim();
        return customerRepository
                .findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrPhoneContainingIgnoreCase(
                        term, term, term
                )
                .stream()
                .map(this::toRow)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CustomerRow> findByName(String name) {
        if (name == null || name.isBlank()) {
            return findAll();
        }
        return customerRepository.findByFullNameContainingIgnoreCase(name.trim()).stream()
                .map(this::toRow)
                .toList();
    }

    @Transactional
    public CustomerRow create(@Valid CustomerData data) {
        CustomerEntity customer = customerRepository.saveAndFlush(
                new CustomerEntity(data.fullName(), data.phone(), data.email(), data.address())
        );
        eventPublisher.publishEvent(new CustomerChangedEvent());
        LOGGER.info("Customer registered");
        return toRow(customer);
    }

    @Transactional
    public CustomerRow update(String customerId, @Valid CustomerData data) {
        CustomerEntity customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new EntityNotFoundException("error.customer-not-found"));
        customer.update(data.fullName(), data.phone(), data.email(), data.address());
        CustomerEntity updated = customerRepository.saveAndFlush(customer);
        eventPublisher.publishEvent(new CustomerChangedEvent());
        LOGGER.info("Customer updated");
        return toRow(updated);
    }

    @Transactional
    public void delete(String customerId) {
        if (!customerRepository.existsById(customerId)) {
            throw new EntityNotFoundException("error.customer-not-found");
        }
        try {
            customerRepository.deleteById(customerId);
            customerRepository.flush();
            eventPublisher.publishEvent(new CustomerChangedEvent());
            LOGGER.info("Customer deleted");
        } catch (DataIntegrityViolationException exception) {
            LOGGER.warn("Customer deletion rejected because related records exist", exception);
            throw new IllegalStateException("error.customer-has-related-records", exception);
        }
    }

    private CustomerRow toRow(CustomerEntity customer) {
        return new CustomerRow(customer.getId(), customer.getFullName(), customer.getPhone(),
                customer.getEmail(), customer.getAddress());
    }
}
