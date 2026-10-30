package com.assistant.guest;

import com.assistant.apps.AssistantApp;
import com.assistant.apps.AssistantAppRegistry;
import com.assistant.apps.ProfileAppService;
import com.assistant.apps.WeatherLocation;
import com.assistant.apps.WeatherService;
import com.assistant.common.ApiException;
import com.assistant.news.NewsCategoryService;
import com.assistant.news.NewsService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Formulaire reserve au role ADMIN (/admin/**) : emplacements, ville et categories de l'ecran invite. */
@Controller
@RequestMapping("/admin/guest")
public class GuestAdminController {

    private static final List<String> SLOT_LABELS = List.of("Principal", "Haut gauche", "Bas gauche", "Haut droite",
            "Bas droite");
    private static final String REDIRECT = "redirect:/admin/guest";

    private final GuestService guests;
    private final ProfileAppService profileApps;
    private final AssistantAppRegistry registry;
    private final WeatherService weather;
    private final NewsService news;
    private final NewsCategoryService categories;
    private final ObjectMapper objectMapper;

    public GuestAdminController(GuestService guests, ProfileAppService profileApps, AssistantAppRegistry registry,
                                WeatherService weather, NewsService news, NewsCategoryService categories,
                                ObjectMapper objectMapper) {
        this.guests = guests;
        this.profileApps = profileApps;
        this.registry = registry;
        this.weather = weather;
        this.news = news;
        this.categories = categories;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public String page(@RequestParam(defaultValue = "") String cityQuery, Model model) {
        long guestId = guests.ensureGuest();
        List<ProfileAppService.AppPreference> all = profileApps.listForProfile(guestId);
        List<ProfileAppService.AppPreference> shown = profileApps.listForGuest(guestId);
        List<SlotRow> slots = new ArrayList<>();
        for (int index = 0; index < SLOT_LABELS.size(); index++) {
            int position = index;
            String key = shown.stream().filter(app -> app.position() == position).map(ProfileAppService.AppPreference::key)
                    .findFirst().orElse("");
            slots.add(new SlotRow(index, SLOT_LABELS.get(index), key));
        }
        model.addAttribute("slots", slots);
        model.addAttribute("apps", guestApps());
        model.addAttribute("cityName", currentCity(all));
        model.addAttribute("cityQuery", cityQuery);
        model.addAttribute("cityResults", searchCities(cityQuery, model));
        model.addAttribute("categories", categoryChoices(all));
        return "admin/guest";
    }

    @PostMapping("/layout")
    public String saveLayout(@RequestParam Map<String, String> params, RedirectAttributes redirectAttributes) {
        return run(redirectAttributes, "Emplacements enregistrés.", () -> {
            long guestId = guests.ensureGuest();
            Map<String, Integer> assigned = new java.util.LinkedHashMap<>();
            for (int index = 0; index < SLOT_LABELS.size(); index++) {
                String key = params.getOrDefault("slot" + index, "").strip();
                if (key.isEmpty()) {
                    continue;
                }
                if (guestApps().stream().noneMatch(app -> app.key().equals(key))) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Cette application n'est pas autorisée pour l'invité.");
                }
                if (assigned.putIfAbsent(key, index) != null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Une application ne peut occuper qu'un emplacement.");
                }
            }
            List<ProfileAppService.AppPreference> current = profileApps.listForProfile(guestId);
            List<ProfileAppService.Selection> selections = new ArrayList<>();
            for (AppOption app : guestApps()) {
                String config = current.stream().filter(item -> item.key().equals(app.key()))
                        .map(ProfileAppService.AppPreference::configJson).findFirst().orElse("{}");
                Integer slot = assigned.get(app.key());
                selections.add(new ProfileAppService.Selection(app.key(), slot != null, slot == null ? -1 : slot, config));
            }
            profileApps.replaceAll(guestId, selections);
        });
    }

    @PostMapping("/city")
    public String saveCity(@RequestParam long id, @RequestParam String name,
                           @RequestParam(defaultValue = "") String region, @RequestParam String country,
                           @RequestParam double latitude, @RequestParam double longitude,
                           RedirectAttributes redirectAttributes) {
        return run(redirectAttributes, "Ville enregistrée.", () -> weather.saveLocation(guests.ensureGuest(),
                new WeatherLocation(id, name, region, country, latitude, longitude)));
    }

    @PostMapping("/news")
    public String saveNews(@RequestParam(required = false) List<Long> categoryIds,
                           RedirectAttributes redirectAttributes) {
        return run(redirectAttributes, "Catégories enregistrées.",
                () -> news.saveSelection(guests.ensureGuest(), categoryIds == null ? List.of() : categoryIds));
    }

    private List<AppOption> guestApps() {
        return registry.all().stream().filter(AssistantApp::guestAllowed)
                .map(app -> new AppOption(app.key(), app.title())).toList();
    }

    private List<WeatherLocation> searchCities(String query, Model model) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        try {
            return weather.searchCities(query);
        } catch (ApiException exception) {
            model.addAttribute("notice", exception.getMessage());
            return List.of();
        }
    }

    private List<CategoryChoice> categoryChoices(List<ProfileAppService.AppPreference> all) {
        String config = configOf(all, "news");
        boolean everything = !news.hasSelection(config);
        Set<Long> selected = new HashSet<>();
        try {
            JsonNode ids = objectMapper.readTree(config).path("categoryIds");
            ids.forEach(item -> selected.add(item.longValue()));
        } catch (JacksonException exception) {
            // Configuration illisible : traitee comme une selection vide.
        }
        return categories.listAll().stream()
                .map(category -> new CategoryChoice(category.getId(), category.getName(),
                        everything || selected.contains(category.getId())))
                .toList();
    }

    private String currentCity(List<ProfileAppService.AppPreference> all) {
        try {
            JsonNode location = objectMapper.readTree(configOf(all, "weather")).path("location");
            return location.isObject() ? location.path("name").asString("") : "";
        } catch (JacksonException exception) {
            return "";
        }
    }

    private static String configOf(List<ProfileAppService.AppPreference> all, String key) {
        return all.stream().filter(app -> app.key().equals(key)).map(ProfileAppService.AppPreference::configJson)
                .findFirst().orElse("{}");
    }

    private String run(RedirectAttributes redirectAttributes, String success, Runnable action) {
        try {
            action.run();
            redirectAttributes.addFlashAttribute("notice", success);
        } catch (ApiException exception) {
            redirectAttributes.addFlashAttribute("notice", exception.getMessage());
        }
        return REDIRECT;
    }

    public record SlotRow(int index, String label, String selectedKey) {
    }

    public record AppOption(String key, String title) {
    }

    public record CategoryChoice(long id, String name, boolean checked) {
    }
}
