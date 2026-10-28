package com.assistant.quota;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** Budget journalier d'appels persiste : la reservation est atomique et precede l'appel externe. */
@Service
public class ApiQuotaService {

    static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final int RETENTION_DAYS = 7;

    private final ExternalApiQuotaRepository repository;
    private final Clock clock;

    public ApiQuotaService(ExternalApiQuotaRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Reserve un appel ; faux si le budget du jour (heure de Paris) est atteint. */
    public boolean tryReserve(String api, int budget) {
        LocalDate today = LocalDate.now(clock.withZone(PARIS));
        String day = today.toString();
        if (repository.reserve(api, day, budget) == 1) {
            return true;
        }
        if (repository.findByApiNameAndQuotaDay(api, day).isPresent()) {
            return false;
        }
        try {
            repository.saveAndFlush(new ExternalApiQuota(api, day, 0));
        } catch (DataIntegrityViolationException concurrentInsert) {
            // Un autre appel a cree la ligne du jour entre-temps.
        }
        repository.deleteBefore(today.minusDays(RETENTION_DAYS).toString());
        return repository.reserve(api, day, budget) == 1;
    }

    public int used(String api) {
        String day = LocalDate.now(clock.withZone(PARIS)).toString();
        return repository.findByApiNameAndQuotaDay(api, day).map(ExternalApiQuota::getCalls).orElse(0);
    }
}
