package com.woodlogix.server.persistence.entity;

import com.woodlogix.core.enums.MaterialType;
import com.woodlogix.core.enums.VisibleEdge;
import com.woodlogix.core.service.EdgeBandingCalculator;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "furniture_parts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FurniturePartEntity {
    private static final EdgeBandingCalculator EDGE_BANDING_CALCULATOR =
            new EdgeBandingCalculator();

    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_environment_id", nullable = false)
    @NotNull
    private ProjectEnvironmentEntity projectEnvironment;

    @Column(name = "nome", nullable = false)
    @NotBlank
    private String name;

    @Column(name = "comprimento_mm", nullable = false, precision = 10, scale = 2)
    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal lengthMm;

    @Column(name = "largura_mm", nullable = false, precision = 10, scale = 2)
    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal widthMm;

    @Column(name = "espessura_mm", nullable = false, precision = 10, scale = 2)
    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal thicknessMm;

    @jakarta.persistence.Convert(converter = MaterialTypeConverter.class)
    @Column(nullable = false)
    @NotNull
    private MaterialType material;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "furniture_part_visible_edges",
            joinColumns = @JoinColumn(name = "furniture_part_id")
    )
    @Column(name = "visible_edge", nullable = false)
    @Enumerated(EnumType.STRING)
    @Getter(AccessLevel.NONE)
    private Set<VisibleEdge> visibleEdges = EnumSet.noneOf(VisibleEdge.class);

    public FurniturePartEntity(
            ProjectEnvironmentEntity projectEnvironment,
            String name,
            BigDecimal lengthMm,
            BigDecimal widthMm,
            BigDecimal thicknessMm,
            MaterialType material,
            Set<VisibleEdge> visibleEdges
    ) {
        this(UUID.randomUUID().toString(), projectEnvironment, name, lengthMm, widthMm,
                thicknessMm, material, visibleEdges);
    }

    public FurniturePartEntity(
            String id,
            ProjectEnvironmentEntity projectEnvironment,
            String name,
            BigDecimal lengthMm,
            BigDecimal widthMm,
            BigDecimal thicknessMm,
            MaterialType material,
            Set<VisibleEdge> visibleEdges
    ) {
        this.id = requireText(id, "id");
        update(projectEnvironment, name, lengthMm, widthMm, thicknessMm, material, visibleEdges);
    }

    public void update(
            ProjectEnvironmentEntity projectEnvironment,
            String name,
            BigDecimal lengthMm,
            BigDecimal widthMm,
            BigDecimal thicknessMm,
            MaterialType material,
            Set<VisibleEdge> visibleEdges
    ) {
        this.projectEnvironment = Objects.requireNonNull(
                projectEnvironment, "projectEnvironment must not be null"
        );
        this.name = requireText(name, "name");
        this.lengthMm = requirePositive(lengthMm, "lengthMm");
        this.widthMm = requirePositive(widthMm, "widthMm");
        this.thicknessMm = requirePositive(thicknessMm, "thicknessMm");
        this.material = Objects.requireNonNull(material, "material must not be null");
        Objects.requireNonNull(visibleEdges, "visibleEdges must not be null");
        this.visibleEdges = visibleEdges.isEmpty()
                ? EnumSet.noneOf(VisibleEdge.class)
                : EnumSet.copyOf(visibleEdges);
    }

    public Set<VisibleEdge> getVisibleEdges() {
        return Collections.unmodifiableSet(visibleEdges);
    }

    public BigDecimal calculateEdgeBandingMeters() {
        return EDGE_BANDING_CALCULATOR.calculateLinearMeters(
                lengthMm, widthMm, visibleEdges
        );
    }

    private static BigDecimal requirePositive(BigDecimal value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
