package com.woodlogix.server.api.controller;

import com.woodlogix.application.hardware.DeleteHardware;
import com.woodlogix.application.hardware.HardwareCatalog;
import com.woodlogix.application.hardware.HardwareInUseException;
import com.woodlogix.application.hardware.HardwareInput;
import com.woodlogix.application.hardware.HardwareNotFoundException;
import com.woodlogix.application.hardware.HardwareRow;
import com.woodlogix.server.api.dto.ApiErrorResponse;
import com.woodlogix.server.api.dto.HardwareRequest;
import com.woodlogix.server.api.dto.HardwareResponse;
import com.woodlogix.server.api.mapper.HardwareMapper;
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
@RequestMapping(path = "/api/v1/hardwares", produces = MediaType.APPLICATION_JSON_VALUE)
public class HardwareController {
    private final HardwareCatalog hardwareCatalog;
    private final DeleteHardware deleteHardware;
    private final HardwareMapper hardwareMapper;

    public HardwareController(HardwareCatalog hardwareCatalog,
                              DeleteHardware deleteHardware,
                              HardwareMapper hardwareMapper) {
        this.hardwareCatalog = hardwareCatalog;
        this.deleteHardware = deleteHardware;
        this.hardwareMapper = hardwareMapper;
    }

    @GetMapping
    public List<HardwareResponse> findAll() {
        return hardwareMapper.toResponses(hardwareCatalog.findAll());
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> create(
            @RequestBody(required = false) HardwareRequest request
    ) {
        HardwareInput input = toInput(request);
        if (input == null) {
            return invalidHardwareResponse();
        }

        HardwareRow created = hardwareCatalog.create(input);
        return ResponseEntity.status(HttpStatus.CREATED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(hardwareMapper.toResponse(created));
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> update(
            @PathVariable("id") String id,
            @RequestBody(required = false) HardwareRequest request
    ) {
        HardwareInput input = toInput(request);
        if (input == null) {
            return invalidHardwareResponse();
        }

        try {
            HardwareRow updated = hardwareCatalog.update(id, input);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(hardwareMapper.toResponse(updated));
        } catch (HardwareNotFoundException exception) {
            return notFoundResponse();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable("id") String id) {
        try {
            deleteHardware.execute(id);
            return ResponseEntity.noContent().build();
        } catch (HardwareNotFoundException exception) {
            return notFoundResponse();
        } catch (HardwareInUseException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ApiErrorResponse(
                            "CONFLICT",
                            "error.hardware-has-related-records",
                            List.of(),
                            null));
        }
    }

    private HardwareInput toInput(HardwareRequest request) {
        try {
            return Objects.requireNonNull(hardwareMapper.toInput(request));
        } catch (NullPointerException | IllegalArgumentException exception) {
            return null;
        }
    }

    private ResponseEntity<ApiErrorResponse> invalidHardwareResponse() {
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ApiErrorResponse(
                        "VALIDATION_ERROR",
                        "validation.hardware-invalid",
                        List.of(),
                        null));
    }

    private ResponseEntity<ApiErrorResponse> notFoundResponse() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ApiErrorResponse(
                        "NOT_FOUND",
                        "error.hardware-not-found",
                        List.of(),
                        null));
    }
}
