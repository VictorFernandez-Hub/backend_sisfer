package com.ferreteria.ferreteria.controller;

import org.springframework.stereotype.Controller;

import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ControladorPrincipal {

    @GetMapping("/login")
    public String login() {
        return "vistas/login";
    }

    @GetMapping("/")
    public String inicio() {
        return "index";
    }
}
