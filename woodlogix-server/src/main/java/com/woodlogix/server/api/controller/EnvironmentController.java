package com.woodlogix.server.api.controller;

import com.woodlogix.application.customer.CustomerNotFoundException;
import com.woodlogix.application.environment.EnvironmentCatalog;
import com.woodlogix.application.environment.EnvironmentInUseException;
import com.woodlogix.application.environment.EnvironmentNotFoundException;
import com.woodlogix.application.environment.EnvironmentRow;
import com.woodlogix.server.api.dto.ApiErrorResponse;
import com.woodlogix.server.api.dto.EnvironmentAddRequest;
import com.woodlogix.server.api.dto.EnvironmentReplaceRequest;
import com.woodlogix.server.api.dto.EnvironmentResponse;
import com.woodlogix.server.api.mapper.EnvironmentMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class EnvironmentController {
    private final EnvironmentCatalog environmentCatalog;
    private final EnvironmentMapper environmentMapper;

    public EnvironmentController(EnvironmentCatalog environmentCatalog,
                                 EnvironmentMapper environmentMapper) {
        this.environmentCatalog = environmentCatalog;
        this.environmentMapper = environmentMapper;
    }

    @GetMapping("/api/v1/environments")
    public List<EnvironmentResponse> find(
            @RequestParam(value = "customerId", required = false) String customerId
    ) {
        List<EnvironmentRow> rows = customerId == null
                ? environmentCatalog.findAll()
                : environmentCatalog.findByCustomer(customerId);
        return environmentMapper.toResponses(rows);
    }

    @PostMapping(
            path = "/api/v1/customers/{customerId}/environments",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> create(
            @PathVariable("customerId") String customerId,
            @RequestBody(required = false) EnvironmentAddRequest request
    ) {
        if (request == null) {
            return invalidEnvironmentResponse();
        }
        try {
            List<EnvironmentRow> created = environmentCatalog.createForCustomer(
                    customerId,
                    environmentMapper.roomTypes(request),
                    environmentMapper.customRoomType(request));
            return ResponseEntity.status(HttpStatus.CREATED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(environmentMapper.toResponses(created));
        } catch (CustomerNotFoundException exception) {
            return customerNotFoundResponse();
        } catch (EnvironmentInUseException exception) {
            return conflictResponse();
        } catch (IllegalArgumentException | NullPointerException exception) {
            return invalidEnvironmentResponse();
        }
    }

    @PutMapping(
            path = "/api/v1/customers/{customerId}/environments",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> replace(
            @PathVariable("customerId") String customerId,
            @RequestBody(required = false) EnvironmentReplaceRequest request
    ) {
        if (request == null) {
            return invalidEnvironmentResponse();
        }
        try {
            var result = environmentCatalog.saveOrUpdateCustomerEnvironments(
                    customerId, environmentMapper.replacementTypes(request));
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(environmentMapper.toResponses(result));
        } catch (CustomerNotFoundException exception) {
            return customerNotFoundResponse();
        } catch (EnvironmentInUseException exception) {
            return conflictResponse();
        } catch (IllegalArgumentException | NullPointerException exception) {
            return invalidEnvironmentResponse();
        }
    }

    @DeleteMapping("/api/v1/environments/{id}")
    public ResponseEntity<?> delete(@PathVariable("id") String id) {
        try {
            environmentCatalog.delete(id);
            return ResponseEntity.noContent().build();
        } catch (EnvironmentNotFoundException exception) {
            return environmentNotFoundResponse();
        } catch (EnvironmentInUseException exception) {
            return conflictResponse();
        }
    }

    @DeleteMapping(path = "/api/v1/customers/{customerId}/environments")
    public ResponseEntity<?> deleteAll(@PathVariable("customerId") String customerId) {
        try {
            environmentCatalog.deleteAllByCustomer(customerId);
            return ResponseEntity.noContent().build();
        } catch (CustomerNotFoundException exception) {
            return customerNotFoundResponse();
        } catch (EnvironmentInUseException exception) {
            return conflictResponse();
        }
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> invalidJson(
            HttpMessageNotReadableException exception
    ) {
        return invalidEnvironmentResponse();
    }

    private ResponseEntity<ApiErrorResponse> invalidEnvironmentResponse() {
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ApiErrorResponse(
                        "VALIDATION_ERROR",
                        "validation.environment-invalid",
                        List.of(),
                        null));
    }

    private ResponseEntity<ApiErrorResponse> customerNotFoundResponse() {
        return notFoundResponse("error.customer-not-found");
    }

    private ResponseEntity<ApiErrorResponse> environmentNotFoundResponse() {
        return notFoundResponse("error.environment-not-found");
    }

    private ResponseEntity<ApiErrorResponse> notFoundResponse(String messageKey) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ApiErrorResponse("NOT_FOUND", messageKey, List.of(), null));
    }

    private ResponseEntity<ApiErrorResponse> conflictResponse() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ApiErrorResponse(
                        "CONFLICT",
                        "error.environment-has-related-records",
                        List.of(),
                        null));
    }
}
