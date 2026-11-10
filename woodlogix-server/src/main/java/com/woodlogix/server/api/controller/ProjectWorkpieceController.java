package com.woodlogix.server.api.controller;

import com.woodlogix.application.edgebanding.EdgeBandingNotFoundException;
import com.woodlogix.application.material.MaterialNotFoundException;
import com.woodlogix.application.workpiece.ProjectWorkpiece;
import com.woodlogix.application.workpiece.ProjectWorkpieceNotFoundException;
import com.woodlogix.application.workpiece.ProjectWorkpieceRow;
import com.woodlogix.server.api.dto.ApiErrorResponse;
import com.woodlogix.server.api.dto.ProjectWorkpieceRequest;
import com.woodlogix.server.api.dto.ProjectWorkpieceResponse;
import com.woodlogix.server.api.mapper.ProjectWorkpieceMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(path = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class ProjectWorkpieceController {
    private final ProjectWorkpiece projectWorkpiece;
    private final ProjectWorkpieceMapper projectWorkpieceMapper;

    public ProjectWorkpieceController(
            ProjectWorkpiece projectWorkpiece,
            ProjectWorkpieceMapper projectWorkpieceMapper
    ) {
        this.projectWorkpiece = projectWorkpiece;
        this.projectWorkpieceMapper = projectWorkpieceMapper;
    }

    @GetMapping("/project-workpieces")
    public List<ProjectWorkpieceResponse> findAll() {
        return projectWorkpieceMapper.toResponses(projectWorkpiece.findAll());
    }

    @GetMapping("/sales-projects/{projectId}/workpieces")
    public List<ProjectWorkpieceResponse> findByProject(
            @PathVariable("projectId") String projectId
    ) {
        return projectWorkpieceMapper.toResponses(projectWorkpiece.findByProject(projectId));
    }

    @PostMapping(
            path = "/project-workpieces",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> create(
            @RequestBody(required = false) ProjectWorkpieceRequest request
    ) {
        if (request == null) {
            return invalidResponse();
        }
        try {
            ProjectWorkpieceRow created =
                    projectWorkpiece.create(projectWorkpieceMapper.toInput(request));
            return ResponseEntity.status(HttpStatus.CREATED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(projectWorkpieceMapper.toResponse(created));
        } catch (RuntimeException exception) {
            return mapWriteError(exception);
        }
    }

    @PutMapping(
            path = "/project-workpieces/{id}",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> update(
            @PathVariable("id") String id,
            @RequestBody(required = false) ProjectWorkpieceRequest request
    ) {
        if (request == null) {
            return invalidResponse();
        }
        try {
            ProjectWorkpieceRow updated =
                    projectWorkpiece.update(id, projectWorkpieceMapper.toInput(request));
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(projectWorkpieceMapper.toResponse(updated));
        } catch (RuntimeException exception) {
            return mapWriteError(exception);
        }
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> invalidJson(
            HttpMessageNotReadableException exception
    ) {
        return invalidResponse();
    }

    private ResponseEntity<?> mapWriteError(RuntimeException exception) {
        if (exception instanceof ProjectWorkpieceNotFoundException) {
            return notFoundResponse("error.workpiece-not-found");
        }
        if (exception instanceof MaterialNotFoundException) {
            return notFoundResponse("error.material-not-found");
        }
        if (exception instanceof EdgeBandingNotFoundException) {
            return notFoundResponse("error.edge-banding-not-found");
        }
        if (exception instanceof IllegalArgumentException illegalArgumentException
                && "error.sales-project-not-found"
                .equals(illegalArgumentException.getMessage())) {
            return notFoundResponse("error.sales-project-not-found");
        }
        if (exception instanceof IllegalArgumentException
                || exception instanceof NullPointerException) {
            return invalidResponse();
        }
        throw exception;
    }

    private ResponseEntity<ApiErrorResponse> invalidResponse() {
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ApiErrorResponse(
                        "VALIDATION_ERROR",
                        "validation.workpiece-invalid",
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
