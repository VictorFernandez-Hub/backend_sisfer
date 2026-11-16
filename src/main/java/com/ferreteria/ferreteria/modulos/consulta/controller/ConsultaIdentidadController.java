package com.ferreteria.ferreteria.modulos.consulta.controller;

import com.ferreteria.ferreteria.modulos.consulta.service.ConsultaIdentidadService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/consulta")
public class ConsultaIdentidadController {

    private final ConsultaIdentidadService consulta;

    public ConsultaIdentidadController(ConsultaIdentidadService consulta) {
        this.consulta = consulta;
    }

    @GetMapping("/dni/{dni}")
    public Map<String, String> dni(@PathVariable String dni) {
        return consulta.dni(dni);
    }

    @GetMapping("/ruc/{ruc}")
    public Map<String, String> ruc(@PathVariable String ruc) {
        return consulta.ruc(ruc);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> regla(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("mensaje", ex.getMessage()));
    }
}
