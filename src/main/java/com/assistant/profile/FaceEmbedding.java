package com.assistant.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "face_embedding")
public class FaceEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    /** Tableau JSON de 128 flottants. */
    @Column(name = "embedding_json", nullable = false, columnDefinition = "TEXT")
    private String embeddingJson;

    protected FaceEmbedding() {
    }

    FaceEmbedding(Profile profile, String embeddingJson) {
        this.profile = profile;
        this.embeddingJson = embeddingJson;
    }

    public Long getId() {
        return id;
    }

    public Profile getProfile() {
        return profile;
    }

    public String getEmbeddingJson() {
        return embeddingJson;
    }
}
