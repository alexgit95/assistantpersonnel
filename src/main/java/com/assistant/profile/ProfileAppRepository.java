package com.assistant.profile;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileAppRepository extends JpaRepository<ProfileApp, Long> {

    List<ProfileApp> findByProfile_IdOrderByPositionAscAppKeyAsc(Long profileId);

    Optional<ProfileApp> findByProfile_IdAndAppKey(Long profileId, String appKey);
}
