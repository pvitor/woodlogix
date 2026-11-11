package com.woodlogix.application.proposal;

import com.woodlogix.core.enums.CurrencyCode;
import com.woodlogix.core.model.CommercialTermsDraft;
import com.woodlogix.core.model.DiscountInput;
import com.woodlogix.core.model.ManualConversion;
import com.woodlogix.core.model.ProposalDraftItem;
import com.woodlogix.core.model.RecipientSnapshot;
import com.woodlogix.core.model.SalesProjectId;
import com.woodlogix.core.model.ValidityPeriod;

import java.util.List;
import java.util.Objects;

public record CreateProposalInput(
        SalesProjectId salesProjectId,
        RecipientSnapshot recipient,
        CurrencyCode currency,
        ManualConversion conversion,
        List<ProposalDraftItem> items,
        DiscountInput generalDiscount,
        ValidityPeriod validityPeriod,
        CommercialTermsDraft commercialTerms
) {
    public CreateProposalInput {
        Objects.requireNonNull(salesProjectId, "salesProjectId must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(items, "items must not be null");
        for (ProposalDraftItem item : items) {
            Objects.requireNonNull(item, "items must not contain null");
        }
        items = List.copyOf(items);
        Objects.requireNonNull(validityPeriod, "validityPeriod must not be null");
        Objects.requireNonNull(commercialTerms, "commercialTerms must not be null");
    }
}
