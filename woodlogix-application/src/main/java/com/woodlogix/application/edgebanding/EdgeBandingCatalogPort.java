package com.woodlogix.application.edgebanding;

import java.util.List;
import java.util.Optional;

public interface EdgeBandingCatalogPort {
    EdgeBandingRow create(EdgeBandingInput input);

    Optional<EdgeBandingRow> update(String id, EdgeBandingInput input);

    List<EdgeBandingRow> findAllOrdered();

    boolean exists(String id);

    boolean isReferencedByProjectWorkpiece(String id);

    boolean isReferencedByDraftWorkpiece(String id);

    void deleteAndFlush(String id);
}
