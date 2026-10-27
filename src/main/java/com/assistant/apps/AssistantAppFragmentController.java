package com.assistant.apps;

import com.assistant.auth.AuthenticatedSession;
import com.assistant.auth.RequireSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/apps")
@RequireSession
public class AssistantAppFragmentController {

    private final ProfileAppService profileAppService;

    public AssistantAppFragmentController(ProfileAppService profileAppService) {
        this.profileAppService = profileAppService;
    }

    @GetMapping("/{appKey}/fragment")
    public String fragment(@PathVariable String appKey, AuthenticatedSession session, Model model) {
        ProfileAppService.EnabledApp app = profileAppService.requireEnabled(session.profileId(), appKey);
        model.addAttribute("appKey", app.definition().key());
        model.addAttribute("appTitle", app.definition().title());
        model.addAttribute("appConfigJson", app.configJson());
        model.addAttribute("appConfig", app.configJson());
        return app.definition().fragmentTemplate();
    }
}
