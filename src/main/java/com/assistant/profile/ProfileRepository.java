package com.assistant.profile;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProfileRepository extends JpaRepository<Profile, Long> {

    @EntityGraph(attributePaths = "embeddings")
    @Query("select p from Profile p where p.id not in (select g.profileId from GuestSetting g) order by p.id")
    List<Profile> findAllWithEmbeddings();

    @Query(value = "select p from Profile p where p.id not in (select g.profileId from GuestSetting g)",
            countQuery = "select count(p) from Profile p where p.id not in (select g.profileId from GuestSetting g)")
    Page<Profile> findAllWithoutGuest(Pageable pageable);

    @Query("select count(p) from Profile p where p.id not in (select g.profileId from GuestSetting g)")
    long countWithoutGuest();

    @Query("select count(g) > 0 from GuestSetting g where g.profileId = :id")
    boolean isGuest(@Param("id") Long id);

    @EntityGraph(attributePaths = "embeddings")
    @Query("select p from Profile p where p.id = :id")
    java.util.Optional<Profile> findByIdWithEmbeddings(@Param("id") Long id);

    @Query("select p.name from Profile p where p.id = :id")
    java.util.Optional<String> findNameById(@Param("id") Long id);

    /** Empreintes des autres profils, par lots pour borner la memoire. */
    @Query("select e.embeddingJson from FaceEmbedding e where e.profile.id <> :excludedId order by e.id")
    Slice<String> findEmbeddingJsonExcluding(@Param("excludedId") Long excludedId, Pageable pageable);
}
