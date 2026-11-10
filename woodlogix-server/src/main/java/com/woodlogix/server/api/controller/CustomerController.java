package com.woodlogix.server.api.controller;

import com.woodlogix.application.customer.CustomerCatalog;
import com.woodlogix.application.customer.CustomerInUseException;
import com.woodlogix.application.customer.CustomerInput;
import com.woodlogix.application.customer.CustomerNotFoundException;
import com.woodlogix.application.customer.CustomerRow;
import com.woodlogix.server.api.dto.ApiErrorResponse;
import com.woodlogix.server.api.dto.CustomerRequest;
import com.woodlogix.server.api.dto.CustomerResponse;
import com.woodlogix.server.api.mapper.CustomerMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping(path = "/api/v1/customers", produces = MediaType.APPLICATION_JSON_VALUE)
public class CustomerController {
    private final CustomerCatalog customerCatalog;
    private final CustomerMapper customerMapper;

    public CustomerController(CustomerCatalog customerCatalog, CustomerMapper customerMapper) {
        this.customerCatalog = customerCatalog;
        this.customerMapper = customerMapper;
    }

    @GetMapping
    public ResponseEntity<?> find(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "name", required = false) String name
    ) {
        if (search != null && name != null) {
            return invalidCustomerResponse();
        }
        List<CustomerRow> rows;
        if (search != null) {
            rows = customerCatalog.search(search);
        } else if (name != null) {
            rows = customerCatalog.findByName(name);
        } else {
            rows = customerCatalog.findAll();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(customerMapper.toResponses(rows));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> create(
            @RequestBody(required = false) CustomerRequest request
    ) {
        CustomerInput input = toInput(request);
        if (input == null) {
            return invalidCustomerResponse();
        }

        try {
            CustomerRow created = customerCatalog.create(input);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(customerMapper.toResponse(created));
        } catch (ConstraintViolationException exception) {
            if (isCustomerConstraintViolation(exception)) {
                return invalidCustomerResponse();
            }
            throw exception;
        }
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> update(
            @PathVariable("id") String id,
            @RequestBody(required = false) CustomerRequest request
    ) {
        CustomerInput input = toInput(request);
        if (input == null) {
            return invalidCustomerResponse();
        }

        try {
            CustomerRow updated = customerCatalog.update(id, input);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(customerMapper.toResponse(updated));
        } catch (CustomerNotFoundException exception) {
            return notFoundResponse();
        } catch (ConstraintViolationException exception) {
            if (isCustomerConstraintViolation(exception)) {
                return invalidCustomerResponse();
            }
            throw exception;
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable("id") String id) {
        try {
            customerCatalog.delete(id);
            return ResponseEntity.noContent().build();
        } catch (CustomerNotFoundException exception) {
            return notFoundResponse();
        } catch (CustomerInUseException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ApiErrorResponse(
                            "CONFLICT",
                            "error.customer-has-related-records",
                            List.of(),
                            null));
        }
    }

    private CustomerInput toInput(CustomerRequest request) {
        try {
            return Objects.requireNonNull(customerMapper.toInput(request));
        } catch (NullPointerException | IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean isCustomerConstraintViolation(ConstraintViolationException exception) {
        return exception.getConstraintViolations().stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .allMatch(path -> path.equals("fullName")
                        || path.equals("phone")
                        || path.equals("email")
                        || path.equals("address"));
    }

    private ResponseEntity<ApiErrorResponse> invalidCustomerResponse() {
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ApiErrorResponse(
                        "VALIDATION_ERROR",
                        "validation.customer-invalid",
                        List.of(),
                        null));
    }

    private ResponseEntity<ApiErrorResponse> notFoundResponse() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ApiErrorResponse(
                        "NOT_FOUND",
                        "error.customer-not-found",
                        List.of(),
                        null));
    }
}
