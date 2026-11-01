package com.ferreteria.ferreteria.modulos.seguridad.controller;

import com.ferreteria.ferreteria.modulos.seguridad.repository.BitacoraRepository;
import com.ferreteria.ferreteria.modulos.usuario.repository.UsuarioRepository;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bitacora")
public class BitacoraController {
    private final BitacoraRepository repo;
    private final UsuarioRepository usuarioRepo;

    public BitacoraController(BitacoraRepository repo, UsuarioRepository usuarioRepo) {
        this.repo = repo;
        this.usuarioRepo = usuarioRepo;
    }

    @GetMapping
    public List<Map<String, Object>> listar() {
        return repo.findAllByOrderByFecEventoDesc().stream().map(b -> {
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("idBitacora", b.getIdBitacora());
            m.put("fecEvento", b.getFecEvento());
            m.put("modulo", b.getModulo());
            m.put("accion", b.getAccion());
            m.put("detalle", b.getDetalle());
            String cuenta = usuarioRepo.findById(b.getIdUsuario())
                .map(u -> u.getCuenta()).orElse("usuario " + b.getIdUsuario());
            m.put("cuenta", cuenta);
            return m;
        }).toList();
    }
}