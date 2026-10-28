package com.assistant.quota;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ExternalApiQuotaRepository extends JpaRepository<ExternalApiQuota, String> {

    Optional<ExternalApiQuota> findByApiNameAndQuotaDay(String apiName, String quotaDay);

    /** Incremente d'un appel seulement si le budget n'est pas atteint ; renvoie 1 si reserve, 0 sinon. */
    @Transactional
    @Modifying
    @Query("update ExternalApiQuota q set q.calls = q.calls + 1 "
            + "where q.apiName = :api and q.quotaDay = :day and q.calls < :budget")
    int reserve(@Param("api") String api, @Param("day") String day, @Param("budget") int budget);

    @Transactional
    @Modifying
    @Query("delete from ExternalApiQuota q where q.quotaDay < :day")
    int deleteBefore(@Param("day") String day);
}
