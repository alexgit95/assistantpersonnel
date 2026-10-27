package com.assistant.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppSessionRepository extends JpaRepository<AppSession, String> {

    @Modifying
    @Query("delete from AppSession s where s.expiresAtMs <= :now")
    int deleteExpired(@Param("now") long now);

    @Modifying
    @Query("delete from AppSession s where s.profileId = :profileId")
    int deleteByProfileId(@Param("profileId") long profileId);

    @Modifying
    @Query("update AppSession s set s.expiresAtMs = :expiresAtMs where s.id = :id")
    int updateExpiry(@Param("id") String id, @Param("expiresAtMs") long expiresAtMs);
}
