package com.assistant.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "weight_entry", indexes = @Index(name = "idx_weight_profile_date", columnList = "profile_id, entry_date"))
public class WeightEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "kg_tenths", nullable = false)
    private int kgTenths;

    protected WeightEntry() {
    }

    public WeightEntry(Profile profile, LocalDate entryDate, int kgTenths) {
        this.profile = profile;
        this.entryDate = entryDate;
        this.kgTenths = kgTenths;
    }

    public Long getId() {
        return id;
    }

    public Profile getProfile() {
        return profile;
    }

    public LocalDate getEntryDate() {
        return entryDate;
    }

    public void setEntryDate(LocalDate entryDate) {
        this.entryDate = entryDate;
    }

    public int getKgTenths() {
        return kgTenths;
    }

    public void setKgTenths(int kgTenths) {
        this.kgTenths = kgTenths;
    }
}