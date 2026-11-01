package com.ferreteria.ferreteria.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model; // <-- Asegúrate de importar esto
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class Controller403 {

    @RequestMapping("/403")
    public String accessDenied(Model model) { 
        model.addAttribute("redirectUrl", "/"); 
        
        return "componentes/403"; 
    }
}