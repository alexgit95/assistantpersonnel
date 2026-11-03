package com.assistant.config;

import com.assistant.auth.RequireSession;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@Hidden
public class HomeController {

    private final AppVersion appVersion;

    public HomeController(AppVersion appVersion) {
        this.appVersion = appVersion;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("appVersion", appVersion.display());
        return "index";
    }

    @GetMapping("/configuration")
    @RequireSession
    public String configuration() {
        return "configuration";
    }
}
