package com.ferreteria.ferreteria.modulos.seguridad.service;

import com.ferreteria.ferreteria.modulos.seguridad.model.Bitacora;
import com.ferreteria.ferreteria.modulos.seguridad.repository.BitacoraRepository;
import com.ferreteria.ferreteria.modulos.usuario.model.Usuario;
import com.ferreteria.ferreteria.modulos.usuario.repository.UsuarioRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class BitacoraService {
    private final BitacoraRepository repo;
    private final UsuarioRepository usuarioRepo;

    public BitacoraService(BitacoraRepository repo, UsuarioRepository usuarioRepo) {
        this.repo = repo;
        this.usuarioRepo = usuarioRepo;
    }

    public void registrar(String modulo, String accion, String detalle) {
        String cuenta = SecurityContextHolder.getContext().getAuthentication().getName();
        Usuario u = usuarioRepo.findByCuenta(cuenta).orElse(null);
        if (u == null) return;

        Bitacora b = new Bitacora();
        b.setIdUsuario(u.getIdUsuario());
        b.setFecEvento(LocalDateTime.now());
        b.setModulo(modulo);
        b.setAccion(accion);
        b.setTablaObj(modulo.toLowerCase());
        b.setIdRegistro("");
        b.setDetalle(detalle);
        b.setIpOrigen("local");
        b.setExito(true);
        repo.save(b);
    }
}