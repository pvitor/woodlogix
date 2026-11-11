package com.woodlogix.server.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "proposals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProposalEntity {
    @Id
    @Column(nullable = false, length = 32)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sales_project_id", nullable = false)
    private SalesProjectEntity salesProject;

    @Version
    @Column(name = "persistence_version", nullable = false)
    private long persistenceVersion;

    @OneToMany(mappedBy = "proposal", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<ProposalDraftEntity> drafts = new ArrayList<>();

    public ProposalEntity(String id, SalesProjectEntity salesProject) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.salesProject = Objects.requireNonNull(
                salesProject, "salesProject must not be null");
    }

    public void addDraft(ProposalDraftEntity draft) {
        Objects.requireNonNull(draft, "draft must not be null");
        draft.attachTo(this);
        drafts.add(draft);
    }
}
