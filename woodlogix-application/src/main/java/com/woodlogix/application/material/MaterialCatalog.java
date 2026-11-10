package com.woodlogix.application.material;

import com.woodlogix.application.transaction.TransactionPort;

import java.util.List;
import java.util.Objects;

public class MaterialCatalog {
    private final MaterialCatalogPort materials;
    private final TransactionPort transactions;

    public MaterialCatalog(MaterialCatalogPort materials, TransactionPort transactions) {
        this.materials = Objects.requireNonNull(materials);
        this.transactions = Objects.requireNonNull(transactions);
    }

    public MaterialRow create(MaterialInput input) {
        Objects.requireNonNull(input);
        return transactions.executeWithResult(() -> materials.create(input));
    }

    public MaterialRow update(String id, MaterialInput input) {
        Objects.requireNonNull(input);
        return transactions.executeWithResult(() -> materials.update(id, input)
                .orElseThrow(MaterialNotFoundException::new));
    }

    public List<MaterialRow> findAll() {
        return transactions.executeReadOnly(materials::findAllOrdered);
    }

    public void delete(String id) {
        transactions.execute(() -> {
            if (!materials.exists(id)) {
                throw new MaterialNotFoundException();
            }
            if (materials.isReferencedByProjectWorkpiece(id) || materials.isReferencedByDraftWorkpiece(id)) {
                throw new MaterialInUseException();
            }
            materials.deleteAndFlush(id);
        });
    }
}
