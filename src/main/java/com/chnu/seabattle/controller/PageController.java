package com.chnu.seabattle.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PageController {

    @GetMapping("/register")
    public String showRegisterForm() {
        return "register";
    }

    @GetMapping("/login")
    public String showLoginForm(@RequestParam(required = false) String redirect, Model model) {
        if (redirect != null && redirect.startsWith("/") && !redirect.startsWith("//")) {
            model.addAttribute("redirect", redirect);
        }
        return "login";
    }

    @GetMapping("/game")
    public String index() {
        return "index";
    }

    @GetMapping("/game/**")
    public String gamePage() {
        return "game";
    }

    @GetMapping("/game/join/**")
    public String joinPage() {
        return "join";
    }
}