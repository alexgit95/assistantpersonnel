package com.assistant.admin;

import com.assistant.profile.ProfileService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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

    public AdminController(ProfileService profileService, BackupService backupService) {
        this.profileService = profileService;
        this.backupService = backupService;
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

    private String restoreError(String message, int status, int page, Model model, HttpServletResponse response) {
        response.setStatus(status);
        model.addAttribute("notice", message);
        populateIndex(page, model);
        return "admin/index";
    }

    private void populateIndex(int page, Model model) {
        model.addAttribute("profiles", profileService.listAdminProfiles(
                PageRequest.of(Math.max(0, page), PAGE_SIZE, Sort.by(Sort.Direction.ASC, "id"))));
    }
}
