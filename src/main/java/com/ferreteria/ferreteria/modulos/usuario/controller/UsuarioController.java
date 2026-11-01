package com.ferreteria.ferreteria.modulos.usuario.controller;

import com.ferreteria.ferreteria.modulos.usuario.dto.UsuarioResponseDTO;
import com.ferreteria.ferreteria.modulos.usuario.service.UsuarioService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;
import com.ferreteria.ferreteria.modulos.seguridad.service.BitacoraService;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final BitacoraService bitacora;

    public UsuarioController(UsuarioService usuarioService, BitacoraService bitacora) {
    this.usuarioService = usuarioService;
    this.bitacora = bitacora;
    }
    
    @GetMapping
    public ResponseEntity<List<UsuarioResponseDTO>> listarUsuarios() {
        bitacora.registrar("USUARIOS", "CONSULTAR", "Consultó el listado de usuarios");
        return ResponseEntity.ok(usuarioService.listarTodos());
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody java.util.Map<String, String> datos) {
    try {
        return ResponseEntity.ok(usuarioService.crear(datos));
    } catch (RuntimeException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Integer id,
                                    @RequestBody java.util.Map<String, String> datos) {
    try {
        return ResponseEntity.ok(usuarioService.actualizar(id, datos));
    } catch (RuntimeException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}


    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
    usuarioService.eliminar(id);
    return ResponseEntity.noContent().build();
    }
}