package com.woodlogix.application.material;

import java.util.List;
import java.util.Optional;

public interface MaterialCatalogPort {
    MaterialRow create(MaterialInput input);

    Optional<MaterialRow> update(String id, MaterialInput input);

    List<MaterialRow> findAllOrdered();

    boolean exists(String id);

    boolean isReferencedByProjectWorkpiece(String id);

    boolean isReferencedByDraftWorkpiece(String id);

    void deleteAndFlush(String id);
}
