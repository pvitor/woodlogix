package com.woodlogix.desktop.persistence;

import com.woodlogix.application.material.MaterialCatalogPort;
import com.woodlogix.application.material.MaterialInput;
import com.woodlogix.application.material.MaterialRow;
import com.woodlogix.desktop.persistence.entity.MaterialEntity;
import com.woodlogix.desktop.persistence.repository.EstimateDraftRepository;
import com.woodlogix.desktop.persistence.repository.MaterialRepository;
import com.woodlogix.desktop.persistence.repository.ProjectWorkpieceRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class MaterialCatalogAdapter implements MaterialCatalogPort {
    private final MaterialRepository materials;
    private final ProjectWorkpieceRepository workpieces;
    private final EstimateDraftRepository drafts;

    public MaterialCatalogAdapter(MaterialRepository materials, ProjectWorkpieceRepository workpieces,
                                  EstimateDraftRepository drafts) {
        this.materials = materials;
        this.workpieces = workpieces;
        this.drafts = drafts;
    }

    @Override
    public MaterialRow create(MaterialInput input) {
        return toRow(materials.saveAndFlush(new MaterialEntity(input.name(), input.color(),
                input.thicknessMm(), input.lengthMm(), input.widthMm(), input.pricePerSheet())));
    }

    @Override
    public Optional<MaterialRow> update(String id, MaterialInput input) {
        return materials.findById(id).map(entity -> {
            entity.update(input.name(), input.color(), input.thicknessMm(), input.lengthMm(),
                    input.widthMm(), input.pricePerSheet());
            return toRow(materials.saveAndFlush(entity));
        });
    }

    @Override
    public List<MaterialRow> findAllOrdered() {
        return materials.findAllByOrderByNameAscColorAscThicknessMmAsc().stream()
                .map(MaterialCatalogAdapter::toRow).toList();
    }

    @Override
    public boolean exists(String id) {
        return materials.existsById(id);
    }

    @Override
    public boolean isReferencedByProjectWorkpiece(String id) {
        return workpieces.existsByMaterialId(id);
    }

    @Override
    public boolean isReferencedByDraftWorkpiece(String id) {
        return drafts.existsByWorkpiecesMaterialId(id);
    }

    @Override
    public void deleteAndFlush(String id) {
        materials.deleteById(id);
        materials.flush();
    }

    private static MaterialRow toRow(MaterialEntity material) {
        return new MaterialRow(material.getId(), material.getName(), material.getColor(),
                material.getThicknessMm(), material.getLengthMm(), material.getWidthMm(),
                material.getPricePerSheet());
    }
}
