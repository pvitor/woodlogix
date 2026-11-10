package com.woodlogix.server.api.controller;

import com.woodlogix.application.material.MaterialCatalog;
import com.woodlogix.server.api.dto.MaterialResponse;
import com.woodlogix.server.api.mapper.MaterialMapper;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
}
