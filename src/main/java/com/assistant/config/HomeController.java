package com.assistant.config;

import com.assistant.auth.RequireSession;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@Hidden
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/configuration")
    @RequireSession
    public String configuration() {
        return "configuration";
    }
}
