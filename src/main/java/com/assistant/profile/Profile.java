package com.assistant.profile;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "profile")
public class Profile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    /** Data URL d'une image reduite choisie par l'utilisateur (seule image conservee). */
    @Column(name = "avatar", columnDefinition = "TEXT")
    private String avatar;

    @Column(name = "created_at_ms", nullable = false)
    private long createdAtMs;

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<FaceEmbedding> embeddings = new ArrayList<>();

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC, appKey ASC")
    private List<ProfileApp> apps = new ArrayList<>();

    protected Profile() {
    }

    public Profile(String name, String avatar, long createdAtMs) {
        this.name = name;
        this.avatar = avatar;
        this.createdAtMs = createdAtMs;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public long getCreatedAtMs() {
        return createdAtMs;
    }

    public List<FaceEmbedding> getEmbeddings() {
        return embeddings;
    }

    public List<ProfileApp> getApps() {
        return apps;
    }

    public void addApp(ProfileApp app) {
        apps.add(app);
    }

    public void addEmbedding(String embeddingJson) {
        embeddings.add(new FaceEmbedding(this, embeddingJson));
    }
}
