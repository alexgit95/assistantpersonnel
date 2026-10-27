package com.assistant.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class CertificatePageController {

    @GetMapping("/certificat")
    public String certificateHelp() {
        return "certificate";
    }
}