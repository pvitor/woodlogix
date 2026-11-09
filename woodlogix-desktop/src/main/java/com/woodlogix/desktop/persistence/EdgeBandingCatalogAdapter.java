package com.woodlogix.desktop.persistence;

import com.woodlogix.application.edgebanding.EdgeBandingCatalogPort;
import com.woodlogix.application.edgebanding.EdgeBandingInput;
import com.woodlogix.application.edgebanding.EdgeBandingRow;
import com.woodlogix.desktop.persistence.entity.EdgeBandingEntity;
import com.woodlogix.desktop.persistence.repository.EdgeBandingRepository;
import com.woodlogix.desktop.persistence.repository.EstimateDraftRepository;
import com.woodlogix.desktop.persistence.repository.ProjectWorkpieceRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class EdgeBandingCatalogAdapter implements EdgeBandingCatalogPort {
    private final EdgeBandingRepository edgeBandings;
    private final ProjectWorkpieceRepository workpieces;
    private final EstimateDraftRepository drafts;

    public EdgeBandingCatalogAdapter(EdgeBandingRepository edgeBandings,
                                     ProjectWorkpieceRepository workpieces,
                                     EstimateDraftRepository drafts) {
        this.edgeBandings = edgeBandings;
        this.workpieces = workpieces;
        this.drafts = drafts;
    }

    @Override
    public EdgeBandingRow create(EdgeBandingInput input) {
        return toRow(edgeBandings.saveAndFlush(new EdgeBandingEntity(
                input.name(), input.widthMm(), input.pricePerMeter())));
    }

    @Override
    public Optional<EdgeBandingRow> update(String id, EdgeBandingInput input) {
        return edgeBandings.findById(id).map(entity -> {
            entity.update(input.name(), input.widthMm(), input.pricePerMeter());
            return toRow(edgeBandings.saveAndFlush(entity));
        });
    }

    @Override
    public List<EdgeBandingRow> findAllOrdered() {
        return edgeBandings.findAllByOrderByNameAscWidthMmAsc().stream()
                .map(EdgeBandingCatalogAdapter::toRow).toList();
    }

    @Override
    public boolean exists(String id) {
        return edgeBandings.existsById(id);
    }

    @Override
    public boolean isReferencedByProjectWorkpiece(String id) {
        return workpieces.existsByEdgeBandingId(id);
    }

    @Override
    public boolean isReferencedByDraftWorkpiece(String id) {
        return drafts.existsByWorkpiecesEdgeBandingId(id);
    }

    @Override
    public void deleteAndFlush(String id) {
        edgeBandings.deleteById(id);
        edgeBandings.flush();
    }

    private static EdgeBandingRow toRow(EdgeBandingEntity edgeBanding) {
        return new EdgeBandingRow(edgeBanding.getId(), edgeBanding.getName(),
                edgeBanding.getWidthMm(), edgeBanding.getPricePerMeter());
    }
}
