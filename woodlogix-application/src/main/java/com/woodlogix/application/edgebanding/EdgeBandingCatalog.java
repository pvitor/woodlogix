package com.woodlogix.application.edgebanding;

import com.woodlogix.application.transaction.TransactionPort;

import java.util.List;
import java.util.Objects;

public class EdgeBandingCatalog {
    private final EdgeBandingCatalogPort edgeBandings;
    private final TransactionPort transactions;

    public EdgeBandingCatalog(EdgeBandingCatalogPort edgeBandings, TransactionPort transactions) {
        this.edgeBandings = Objects.requireNonNull(edgeBandings);
        this.transactions = Objects.requireNonNull(transactions);
    }

    public EdgeBandingRow create(EdgeBandingInput input) {
        Objects.requireNonNull(input);
        return transactions.executeWithResult(() -> edgeBandings.create(input));
    }

    public EdgeBandingRow update(String id, EdgeBandingInput input) {
        Objects.requireNonNull(input);
        return transactions.executeWithResult(() -> edgeBandings.update(id, input)
                .orElseThrow(EdgeBandingNotFoundException::new));
    }

    public List<EdgeBandingRow> findAll() {
        return transactions.executeReadOnly(edgeBandings::findAllOrdered);
    }

    public void delete(String id) {
        transactions.execute(() -> {
            if (!edgeBandings.exists(id)) {
                throw new EdgeBandingNotFoundException();
            }
            if (edgeBandings.isReferencedByProjectWorkpiece(id)
                    || edgeBandings.isReferencedByDraftWorkpiece(id)) {
                throw new EdgeBandingInUseException();
            }
            edgeBandings.deleteAndFlush(id);
        });
    }
}
