package com.woodlogix.server.api.controller;

import com.woodlogix.application.edgebanding.EdgeBandingCatalog;
import com.woodlogix.application.edgebanding.EdgeBandingInUseException;
import com.woodlogix.application.edgebanding.EdgeBandingInput;
import com.woodlogix.application.edgebanding.EdgeBandingNotFoundException;
import com.woodlogix.application.edgebanding.EdgeBandingRow;
import com.woodlogix.server.api.dto.ApiErrorResponse;
import com.woodlogix.server.api.dto.EdgeBandingRequest;
import com.woodlogix.server.api.dto.EdgeBandingResponse;
import com.woodlogix.server.api.mapper.EdgeBandingMapper;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping(path = "/api/v1/edge-bandings", produces = MediaType.APPLICATION_JSON_VALUE)
public class EdgeBandingController {
    private final EdgeBandingCatalog edgeBandingCatalog;
    private final EdgeBandingMapper edgeBandingMapper;

    public EdgeBandingController(EdgeBandingCatalog edgeBandingCatalog,
                                 EdgeBandingMapper edgeBandingMapper) {
        this.edgeBandingCatalog = edgeBandingCatalog;
        this.edgeBandingMapper = edgeBandingMapper;
    }

    @GetMapping
    public List<EdgeBandingResponse> findAll() {
        return edgeBandingMapper.toResponses(edgeBandingCatalog.findAll());
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> create(
            @RequestBody(required = false) EdgeBandingRequest request
    ) {
        EdgeBandingInput input = toInput(request);
        if (input == null) {
            return invalidEdgeBandingResponse();
        }

        EdgeBandingRow created = edgeBandingCatalog.create(input);
        return ResponseEntity.status(HttpStatus.CREATED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(edgeBandingMapper.toResponse(created));
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> update(
            @PathVariable("id") String id,
            @RequestBody(required = false) EdgeBandingRequest request
    ) {
        EdgeBandingInput input = toInput(request);
        if (input == null) {
            return invalidEdgeBandingResponse();
        }

        try {
            EdgeBandingRow updated = edgeBandingCatalog.update(id, input);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(edgeBandingMapper.toResponse(updated));
        } catch (EdgeBandingNotFoundException exception) {
            return notFoundResponse();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable("id") String id) {
        try {
            edgeBandingCatalog.delete(id);
            return ResponseEntity.noContent().build();
        } catch (EdgeBandingNotFoundException exception) {
            return notFoundResponse();
        } catch (EdgeBandingInUseException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ApiErrorResponse(
                            "CONFLICT",
                            "error.edge-banding-has-related-records",
                            List.of(),
                            null));
        }
    }

    private EdgeBandingInput toInput(EdgeBandingRequest request) {
        try {
            return Objects.requireNonNull(edgeBandingMapper.toInput(request));
        } catch (NullPointerException | IllegalArgumentException exception) {
            return null;
        }
    }

    private ResponseEntity<ApiErrorResponse> invalidEdgeBandingResponse() {
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ApiErrorResponse(
                        "VALIDATION_ERROR",
                        "validation.edge-banding-invalid",
                        List.of(),
                        null));
    }

    private ResponseEntity<ApiErrorResponse> notFoundResponse() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ApiErrorResponse(
                        "NOT_FOUND",
                        "error.edge-banding-not-found",
                        List.of(),
                        null));
    }
}
