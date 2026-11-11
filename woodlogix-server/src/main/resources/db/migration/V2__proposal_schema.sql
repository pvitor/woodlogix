CREATE TABLE proposals (
    id VARCHAR(32) NOT NULL PRIMARY KEY,
    sales_project_id VARCHAR(36) NOT NULL,
    persistence_version BIGINT NOT NULL,
    CONSTRAINT fk_proposal_project
        FOREIGN KEY (sales_project_id) REFERENCES sales_projects(id)
);

CREATE INDEX idx_proposals_sales_project ON proposals(sales_project_id);

CREATE TABLE proposal_id_sequences (
    proposal_year INTEGER NOT NULL PRIMARY KEY,
    next_sequence INTEGER NOT NULL,
    CONSTRAINT ck_proposal_sequence_year CHECK (proposal_year BETWEEN 1 AND 9999),
    CONSTRAINT ck_proposal_sequence_next CHECK (next_sequence BETWEEN 1 AND 10000)
);

CREATE TABLE proposal_drafts (
    draft_id VARCHAR(100) NOT NULL PRIMARY KEY,
    proposal_id VARCHAR(32) NOT NULL,
    position INTEGER NOT NULL,
    source_version_number VARCHAR(20),
    revision BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    customer_id VARCHAR(100) NOT NULL,
    customer_display_name VARCHAR(255) NOT NULL,
    recipient_display_name VARCHAR(255),
    recipient_contact_display_name VARCHAR(255),
    recipient_contact_phone VARCHAR(100),
    recipient_contact_email VARCHAR(255),
    currency VARCHAR(20) NOT NULL,
    conversion_uyu_per_usd TEXT,
    general_discount_type VARCHAR(20),
    general_discount_percentage TEXT,
    general_discount_amount TEXT,
    general_discount_currency VARCHAR(20),
    validity_days INTEGER NOT NULL,
    payment_terms VARCHAR(255) NOT NULL,
    lead_time VARCHAR(255),
    transport_terms VARCHAR(255),
    installation_terms VARCHAR(255),
    commercial_notes TEXT,
    CONSTRAINT fk_draft_proposal
        FOREIGN KEY (proposal_id) REFERENCES proposals(id) ON DELETE CASCADE,
    CONSTRAINT uk_proposal_draft_position UNIQUE (proposal_id, position),
    CONSTRAINT ck_proposal_draft_position CHECK (position >= 0),
    CONSTRAINT ck_draft_revision CHECK (revision > 0),
    CONSTRAINT ck_draft_validity CHECK (validity_days IN (15, 30, 45))
);

CREATE INDEX idx_proposal_drafts_proposal ON proposal_drafts(proposal_id);

CREATE TABLE proposal_draft_items (
    draft_id VARCHAR(100) NOT NULL,
    line_id VARCHAR(100) NOT NULL,
    position INTEGER NOT NULL,
    description VARCHAR(255) NOT NULL,
    quantity TEXT NOT NULL,
    unit_code VARCHAR(50) NOT NULL,
    unit_price_amount TEXT NOT NULL,
    unit_price_currency VARCHAR(20) NOT NULL,
    individual_discount_type VARCHAR(20),
    individual_discount_percentage TEXT,
    individual_discount_amount TEXT,
    individual_discount_currency VARCHAR(20),
    tax_mode VARCHAR(20) NOT NULL,
    tax_rate TEXT NOT NULL,
    catalog_type VARCHAR(30),
    catalog_id VARCHAR(100),
    materials_amount TEXT,
    materials_currency VARCHAR(20),
    edge_banding_amount TEXT,
    edge_banding_currency VARCHAR(20),
    hardware_amount TEXT,
    hardware_currency VARCHAR(20),
    labor_amount TEXT,
    labor_currency VARCHAR(20),
    machines_amount TEXT,
    machines_currency VARCHAR(20),
    extras_amount TEXT,
    extras_currency VARCHAR(20),
    PRIMARY KEY (draft_id, line_id),
    UNIQUE (draft_id, position),
    CONSTRAINT ck_proposal_item_position CHECK (position >= 0),
    CONSTRAINT fk_item_draft
        FOREIGN KEY (draft_id) REFERENCES proposal_drafts(draft_id) ON DELETE CASCADE
);

CREATE INDEX idx_proposal_draft_items_draft_position
    ON proposal_draft_items(draft_id, position);
