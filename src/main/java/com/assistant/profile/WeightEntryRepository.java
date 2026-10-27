package com.assistant.profile;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeightEntryRepository extends JpaRepository<WeightEntry, Long> {

    Page<WeightEntry> findAllByProfile_IdOrderByEntryDateDescIdDesc(long profileId, Pageable pageable);

    Optional<WeightEntry> findByIdAndProfile_Id(long id, long profileId);
}