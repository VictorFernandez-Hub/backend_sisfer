package com.ferreteria.ferreteria;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
public class PruebaController {

    @GetMapping
    public Map<String, String> probarConexion() {
        return Map.of("mensaje", "¡Conexión exitosa entre Spring Boot y Angular!");
    }
}