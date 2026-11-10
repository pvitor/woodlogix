package com.woodlogix.server.api.controller;

import com.woodlogix.application.material.MaterialCatalog;
import com.woodlogix.application.material.MaterialInput;
import com.woodlogix.application.material.MaterialRow;
import com.woodlogix.server.api.dto.ApiErrorResponse;
import com.woodlogix.server.api.dto.MaterialRequest;
import com.woodlogix.server.api.dto.MaterialResponse;
import com.woodlogix.server.api.mapper.MaterialMapper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping(path = "/api/v1/materials", produces = MediaType.APPLICATION_JSON_VALUE)
public class MaterialController {
    private final MaterialCatalog materialCatalog;
    private final MaterialMapper materialMapper;

    public MaterialController(MaterialCatalog materialCatalog, MaterialMapper materialMapper) {
        this.materialCatalog = materialCatalog;
        this.materialMapper = materialMapper;
    }

    @GetMapping
    public List<MaterialResponse> findAll() {
        return materialMapper.toResponses(materialCatalog.findAll());
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> create(@RequestBody(required = false) MaterialRequest request) {
        MaterialInput input;
        try {
            input = Objects.requireNonNull(materialMapper.toInput(request));
        } catch (NullPointerException | IllegalArgumentException exception) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ApiErrorResponse(
                            "VALIDATION_ERROR",
                            "validation.material-invalid",
                            List.of(),
                            null));
        }

        MaterialRow created = materialCatalog.create(input);
        return ResponseEntity.status(201)
                .contentType(MediaType.APPLICATION_JSON)
                .body(materialMapper.toResponse(created));
    }
}
