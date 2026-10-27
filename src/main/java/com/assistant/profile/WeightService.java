package com.assistant.profile;

import com.assistant.common.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WeightService {

    private static final Pattern WEIGHT_VALUE = Pattern.compile("^[0-9]{1,3}(?:[.,][0-9])?$");
    private static final int MIN_KG_TENTHS = 200;
    private static final int MAX_KG_TENTHS = 4000;
    private static final int MAX_PAGE_SIZE = 100;

    private final ProfileRepository profiles;
    private final WeightEntryRepository entries;

    public WeightService(ProfileRepository profiles, WeightEntryRepository entries) {
        this.profiles = profiles;
        this.entries = entries;
    }

    @Transactional(readOnly = true)
    public WeightPage list(long profileId, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw invalid("La pagination demandée est invalide.");
        }
        Page<WeightEntry> result = entries.findAllByProfile_IdOrderByEntryDateDescIdDesc(profileId,
                PageRequest.of(page, size));
        List<WeightView> values = result.getContent().stream().map(WeightService::toView).toList();
        return new WeightPage(values, result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional
    public WeightView create(long profileId, LocalDate entryDate, String value) {
        int kgTenths = parseWeight(value);
        WeightEntry entry = new WeightEntry(profiles.getReferenceById(profileId), entryDate, kgTenths);
        return toView(entries.save(entry));
    }

    @Transactional
    public WeightView update(long profileId, long entryId, LocalDate entryDate, String value) {
        WeightEntry entry = entries.findByIdAndProfile_Id(entryId, profileId)
                .orElseThrow(WeightService::notFound);
        entry.setEntryDate(entryDate);
        entry.setKgTenths(parseWeight(value));
        return toView(entries.save(entry));
    }

    @Transactional
    public void delete(long profileId, long entryId) {
        WeightEntry entry = entries.findByIdAndProfile_Id(entryId, profileId)
                .orElseThrow(WeightService::notFound);
        entries.delete(entry);
    }

    static int parseWeight(String value) {
        if (value == null || !WEIGHT_VALUE.matcher(value.trim()).matches()) {
            throw invalid("Saisissez un poids valide avec au maximum une décimale.");
        }
        try {
            int kgTenths = new BigDecimal(value.trim().replace(',', '.')).movePointRight(1).intValueExact();
            if (kgTenths < MIN_KG_TENTHS || kgTenths > MAX_KG_TENTHS) {
                throw invalid("Le poids doit être compris entre 20,0 et 400,0 kg.");
            }
            return kgTenths;
        } catch (ArithmeticException ex) {
            throw invalid("Saisissez un poids valide avec au maximum une décimale.");
        }
    }

    private static WeightView toView(WeightEntry entry) {
        return new WeightView(entry.getId(), entry.getEntryDate(), entry.getKgTenths());
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Entrée de poids introuvable.");
    }

    public record WeightView(Long id, LocalDate entryDate, int kgTenths) {
    }

    public record WeightPage(List<WeightView> entries, int page, int size, long totalElements, int totalPages) {
    }
}