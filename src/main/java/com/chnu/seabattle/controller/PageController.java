package com.chnu.seabattle.controller;

import com.chnu.seabattle.dto.UserRegistrationRequest;
import com.chnu.seabattle.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequiredArgsConstructor
public class PageController {

    private final AuthService authService;

    @GetMapping("/register")
    public String showRegisterForm(Model model) {
        model.addAttribute("userRegistrationRequest", new UserRegistrationRequest());
        model.addAttribute("success", false);
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("userRegistrationRequest") UserRegistrationRequest registrationRequest,
                           BindingResult result,
                           Model model) {
        if (result.hasErrors()) {
            model.addAttribute("success", false);
            return "register";
        }

        try {
            authService.register(registrationRequest);
            model.addAttribute("success", true);
            model.addAttribute("userRegistrationRequest", new UserRegistrationRequest());
            return "register";
        } catch (IllegalArgumentException e) {
            model.addAttribute("success", false);
            model.addAttribute("error", e.getMessage());
            return "register";
        }
    }

    @GetMapping("/login")
    public String showLoginForm() {
        return "login";
    }
}
