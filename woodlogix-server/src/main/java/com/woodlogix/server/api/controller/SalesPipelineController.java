package com.woodlogix.server.api.controller;

import com.woodlogix.application.sales.SalesPipeline;
import com.woodlogix.application.sales.SalesProjectRow;
import com.woodlogix.server.api.dto.ApiErrorResponse;
import com.woodlogix.server.api.dto.ChangeSalesStageRequest;
import com.woodlogix.server.api.dto.PipelineResponse;
import com.woodlogix.server.api.dto.SalesProjectRequest;
import com.woodlogix.server.api.dto.SalesProjectResponse;
import com.woodlogix.server.api.mapper.SalesPipelineMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.List;

@RestController
@RequestMapping(path = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class SalesPipelineController {
    private final SalesPipeline salesPipeline;
    private final SalesPipelineMapper salesPipelineMapper;

    public SalesPipelineController(
            SalesPipeline salesPipeline,
            SalesPipelineMapper salesPipelineMapper
    ) {
        this.salesPipeline = salesPipeline;
        this.salesPipelineMapper = salesPipelineMapper;
    }

    @GetMapping("/sales-pipeline")
    public PipelineResponse findPipeline() {
        return salesPipelineMapper.toResponse(salesPipeline.findPipeline());
    }

    @PostMapping(
            path = "/sales-projects",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> create(
            @RequestBody(required = false) SalesProjectRequest request
    ) {
        if (request == null) {
            return invalidResponse();
        }
        try {
            SalesProjectRow created = salesPipeline.create(salesPipelineMapper.toInput(request));
            return ResponseEntity.status(HttpStatus.CREATED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(salesPipelineMapper.toResponse(created));
        } catch (IllegalArgumentException exception) {
            return mapCreateError(exception);
        } catch (NullPointerException exception) {
            return invalidResponse();
        }
    }

    @GetMapping("/sales-projects/{id}")
    public ResponseEntity<?> findById(@PathVariable("id") String id) {
        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(salesPipelineMapper.toResponse(salesPipeline.findById(id)));
        } catch (IllegalArgumentException exception) {
            if ("error.project-not-found".equals(exception.getMessage())) {
                return notFoundResponse("error.project-not-found");
            }
            throw exception;
        }
    }

    @PutMapping(
            path = "/sales-projects/{id}/stage",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> move(
            @PathVariable("id") String id,
            @RequestBody(required = false) ChangeSalesStageRequest request
    ) {
        if (request == null || request.stage() == null) {
            return invalidResponse();
        }
        try {
            salesPipeline.move(id, salesPipelineMapper.toStage(request));
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException exception) {
            if ("error.project-not-found".equals(exception.getMessage())) {
                return notFoundResponse("error.project-not-found");
            }
            throw exception;
        } catch (NullPointerException exception) {
            return invalidResponse();
        }
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> invalidJson(
            HttpMessageNotReadableException exception
    ) {
        return invalidResponse();
    }

    private ResponseEntity<?> mapCreateError(IllegalArgumentException exception) {
        return switch (exception.getMessage()) {
            case "error.customer-not-found" ->
                    notFoundResponse("error.customer-not-found");
            case "error.environment-not-found" ->
                    notFoundResponse("error.environment-not-found");
            case "error.environment-customer-mismatch" ->
                    invalidResponse();
            default -> invalidResponse();
        };
    }

    private ResponseEntity<ApiErrorResponse> invalidResponse() {
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ApiErrorResponse(
                        "VALIDATION_ERROR",
                        "validation.sales-project-invalid",
                        List.of(),
                        null));
    }

    private ResponseEntity<ApiErrorResponse> notFoundResponse(String messageKey) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ApiErrorResponse(
                        "NOT_FOUND",
                        messageKey,
                        List.of(),
                        null));
    }
}
