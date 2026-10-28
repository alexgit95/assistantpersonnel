package com.assistant.admin;

import com.assistant.announcements.AnnouncementService;
import com.assistant.common.ApiException;
import com.assistant.profile.ProfileService;
import com.assistant.proxy.ProxySettingsService;
import com.assistant.proxy.ProxySettingsService.ProxyInput;
import com.assistant.integrations.ExternalApiModeService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AdminController {

    private static final int PAGE_SIZE = 50;
    private static final DateTimeFormatter BACKUP_FILENAME_DATE = DateTimeFormatter
            .ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final ProfileService profileService;
    private final BackupService backupService;
    private final AnnouncementService announcementService;
    private final ProxySettingsService proxySettingsService;
    private final ExternalApiModeService externalApiModeService;

    public AdminController(ProfileService profileService, BackupService backupService,
                           AnnouncementService announcementService, ProxySettingsService proxySettingsService,
                           ExternalApiModeService externalApiModeService) {
        this.profileService = profileService;
        this.backupService = backupService;
        this.announcementService = announcementService;
        this.proxySettingsService = proxySettingsService;
        this.externalApiModeService = externalApiModeService;
    }

    @GetMapping("/admin/login")
    public String login() {
        return "admin/login";
    }

    @GetMapping("/admin")
    public String index(@RequestParam(defaultValue = "0") int page, Model model) {
        populateIndex(page, model);
        return "admin/index";
    }

    @GetMapping("/admin/backup")
    public ResponseEntity<StreamingResponseBody> downloadBackup() {
        String filename = "assistant-backup-" + BACKUP_FILENAME_DATE.format(Instant.now()) + ".json";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(backupService.export());
    }

    @PostMapping("/admin/backup")
    public String restoreBackup(@RequestParam(name = "backupFile", required = false) MultipartFile backupFile,
                                @RequestParam(defaultValue = "false") boolean confirmed,
                                @RequestParam(defaultValue = "0") int page,
                                Model model, HttpServletResponse response,
                                RedirectAttributes redirectAttributes) {
        if (!confirmed) {
            return restoreError("Confirmez explicitement le remplacement de toutes les données.",
                    HttpServletResponse.SC_BAD_REQUEST, page, model, response);
        }
        if (backupFile == null || backupFile.isEmpty()) {
            return restoreError("Sélectionnez un fichier de sauvegarde.",
                    HttpServletResponse.SC_BAD_REQUEST, page, model, response);
        }
        try (var input = backupFile.getInputStream()) {
            backupService.restore(input, backupFile.getSize());
        } catch (BackupService.BackupException exception) {
            return restoreError(exception.getMessage(), exception.status().value(), page, model, response);
        } catch (IOException exception) {
            return restoreError("Le fichier de sauvegarde ne peut pas être lu.",
                    HttpServletResponse.SC_BAD_REQUEST, page, model, response);
        }
        redirectAttributes.addFlashAttribute("notice",
            "La sauvegarde a été restaurée. Toutes les sessions des profils ont été invalidées.");
        return "redirect:/admin";
    }

    @PostMapping("/admin/profiles/{profileId}/purge")
    public String purge(@PathVariable long profileId, RedirectAttributes redirectAttributes) {
        profileService.delete(profileId);
        redirectAttributes.addFlashAttribute("notice", "Profil purgé. La personne devra créer un nouveau profil.");
        return "redirect:/admin";
    }

    @PostMapping("/admin/announcements")
    public String createAnnouncement(@RequestParam String title, @RequestParam String startsAt,
                                     @RequestParam String endsAt,
                                     @RequestParam(defaultValue = "false") boolean countdown,
                                     RedirectAttributes redirectAttributes) {
        return saveAnnouncement(redirectAttributes, "Annonce créée.",
                () -> announcementService.create(title, parseParis(startsAt), parseParis(endsAt), countdown));
    }

    @PostMapping("/admin/announcements/{id}")
    public String updateAnnouncement(@PathVariable long id, @RequestParam String title,
                                     @RequestParam String startsAt, @RequestParam String endsAt,
                                     @RequestParam(defaultValue = "false") boolean countdown,
                                     RedirectAttributes redirectAttributes) {
        return saveAnnouncement(redirectAttributes, "Annonce modifiée.",
                () -> announcementService.update(id, title, parseParis(startsAt), parseParis(endsAt), countdown));
    }

    @PostMapping("/admin/announcements/{id}/delete")
    public String deleteAnnouncement(@PathVariable long id, RedirectAttributes redirectAttributes) {
        return saveAnnouncement(redirectAttributes, "Annonce supprimée.", () -> announcementService.delete(id));
    }

    @PostMapping("/admin/proxy")
    public String saveProxy(@RequestParam(defaultValue = "false") boolean enabled,
                            @RequestParam(defaultValue = "") String url,
                            @RequestParam(defaultValue = "") String username,
                            @RequestParam(defaultValue = "") String password,
                            @RequestParam(defaultValue = "") String noProxy,
                            RedirectAttributes redirectAttributes) {
        return saveAnnouncement(redirectAttributes, "Proxy enregistré.",
                () -> proxySettingsService.save(new ProxyInput(enabled, url, username, password, noProxy)));
    }

    @PostMapping("/admin/proxy/test")
    public String testProxy(@RequestParam(defaultValue = "") String url,
                            @RequestParam(defaultValue = "") String username,
                            @RequestParam(defaultValue = "") String password,
                            @RequestParam(defaultValue = "") String noProxy,
                            RedirectAttributes redirectAttributes) {
        var result = proxySettingsService.test(new ProxyInput(true, url, username, password, noProxy));
        redirectAttributes.addFlashAttribute("notice", result.message());
        return "redirect:/admin";
    }

    @PostMapping("/admin/external-api-mode")
    public String updateExternalApiMode(@RequestParam(defaultValue = "false") boolean testMode,
                                        RedirectAttributes redirectAttributes) {
        externalApiModeService.setTestMode(testMode);
        redirectAttributes.addFlashAttribute("notice", testMode
                ? "Mode TEST activé : les réponses des API externes sont simulées."
                : "Mode réel activé : les API externes sont utilisées.");
        return "redirect:/admin";
    }

    private String saveAnnouncement(RedirectAttributes redirectAttributes, String successMessage, Runnable action) {
        try {
            action.run();
            redirectAttributes.addFlashAttribute("notice", successMessage);
        } catch (ApiException exception) {
            redirectAttributes.addFlashAttribute("notice", exception.getMessage());
        }
        return "redirect:/admin";
    }

    private static LocalDateTime parseParis(String value) {
        try {
            return LocalDateTime.parse(value == null ? "" : value.strip());
        } catch (DateTimeParseException exception) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "Date invalide.");
        }
    }

    private String restoreError(String message, int status, int page, Model model, HttpServletResponse response) {
        response.setStatus(status);
        model.addAttribute("notice", message);
        populateIndex(page, model);
        return "admin/index";
    }

    private void populateIndex(int page, Model model) {
        model.addAttribute("announcements", announcementService.listAll());
        model.addAttribute("proxy", proxySettingsService.current());
        model.addAttribute("testMode", externalApiModeService.isTestMode());
        model.addAttribute("profiles", profileService.listAdminProfiles(
                PageRequest.of(Math.max(0, page), PAGE_SIZE, Sort.by(Sort.Direction.ASC, "id"))));
    }
}
