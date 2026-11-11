package com.woodlogix.server.persistence.entity;

import java.io.Serializable;
import java.util.Objects;

public class ProposalDraftItemKey implements Serializable {
    private String draftId;
    private String lineId;

    public ProposalDraftItemKey() {
    }

    public ProposalDraftItemKey(String draftId, String lineId) {
        this.draftId = draftId;
        this.lineId = lineId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProposalDraftItemKey key)) {
            return false;
        }
        return Objects.equals(draftId, key.draftId)
                && Objects.equals(lineId, key.lineId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(draftId, lineId);
    }
}
