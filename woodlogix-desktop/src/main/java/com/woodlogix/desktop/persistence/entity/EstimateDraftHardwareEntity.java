package com.woodlogix.desktop.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "project_estimate_hardwares")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EstimateDraftHardwareEntity {
    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estimate_id", nullable = false)
    private EstimateDraftEntity estimate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hardware_id", nullable = false)
    private HardwareEntity hardware;

    @Column(nullable = false)
    private int quantity;

    public EstimateDraftHardwareEntity(HardwareEntity hardware, int quantity) {
        this.id = UUID.randomUUID().toString();
        this.hardware = hardware;
        this.quantity = quantity;
    }

    void attachTo(EstimateDraftEntity estimate) {
        this.estimate = estimate;
    }
}
