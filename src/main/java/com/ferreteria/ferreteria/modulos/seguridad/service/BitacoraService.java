package com.ferreteria.ferreteria.modulos.seguridad.service;

import com.ferreteria.ferreteria.modulos.seguridad.model.Bitacora;
import com.ferreteria.ferreteria.modulos.seguridad.repository.BitacoraRepository;
import com.ferreteria.ferreteria.modulos.usuario.model.Usuario;
import com.ferreteria.ferreteria.modulos.usuario.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * Bitácora de auditoría: quién hizo qué, en qué módulo, sobre qué registro, desde qué IP y si salió bien.
 *
 * - registrar(...)        : acción exitosa del usuario en sesión. Va en la misma transacción de la
 *                           operación: si la operación se revierte, el registro también.
 * - registrarFallo(...)   : intento rechazado (acceso denegado, regla de negocio). Usa su propia
 *                           transacción para que quede guardado aunque la operación se revierta.
 * - registrarUsuario(...) : eventos sin sesión activa (login, login fallido, bloqueo, cierre de sesión,
 *                           sesión expirada, recuperación de clave).
 *
 * Los textos se recortan al tamaño de las columnas: un detalle largo nunca debe romper la operación.
 */
@Service
public class BitacoraService {

    private static final Logger log = LoggerFactory.getLogger(BitacoraService.class);

    // Tamaños de la tabla bitacora (V1__esquema_base.sql)
    private static final int MAX_MODULO = 20;
    private static final int MAX_ACCION = 20;
    private static final int MAX_TABLA = 20;
    private static final int MAX_ID_REGISTRO = 20;
    private static final int MAX_DETALLE = 300;
    private static final int MAX_IP = 45;

    private final BitacoraRepository repo;
    private final UsuarioRepository usuarioRepo;

    public BitacoraService(BitacoraRepository repo, UsuarioRepository usuarioRepo) {
        this.repo = repo;
        this.usuarioRepo = usuarioRepo;
    }

    // ------------------------------------------------------------------ usuario en sesión

    public void registrar(String modulo, String accion, String detalle) {
        registrar(modulo, accion, "", detalle);
    }

    public void registrar(String modulo, String accion, Object idRegistro, String detalle) {
        Usuario u = usuarioEnSesion();
        if (u == null) return;
        guardar(u.getIdUsuario(), modulo, accion, idRegistro, detalle, true, ipActual());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarFallo(String modulo, String accion, Object idRegistro, String detalle) {
        try {
            Usuario u = usuarioEnSesion();
            if (u == null) return;
            guardar(u.getIdUsuario(), modulo, accion, idRegistro, detalle, false, ipActual());
        } catch (Exception e) {
            log.warn("No se pudo registrar el fallo en la bitácora: {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------------ sin sesión activa

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarUsuario(Integer idUsuario, String modulo, String accion,
                                 String detalle, boolean exito) {
        registrarUsuario(idUsuario, modulo, accion, detalle, exito, ipActual());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarUsuario(Integer idUsuario, String modulo, String accion,
                                 String detalle, boolean exito, String ip) {
        if (idUsuario == null) return;
        try {
            guardar(idUsuario, modulo, accion, idUsuario, detalle, exito, ip);
        } catch (Exception e) {
            log.warn("No se pudo registrar {} en la bitácora: {}", accion, e.getMessage());
        }
    }

    // ------------------------------------------------------------------ apoyo

    private void guardar(Integer idUsuario, String modulo, String accion, Object idRegistro,
                         String detalle, boolean exito, String ip) {
        Bitacora b = new Bitacora();
        b.setIdUsuario(idUsuario);
        b.setFecEvento(LocalDateTime.now());
        b.setModulo(cortar(modulo, MAX_MODULO));
        b.setAccion(cortar(accion, MAX_ACCION));
        b.setTablaObj(cortar(modulo == null ? "" : modulo.toLowerCase(), MAX_TABLA));
        b.setIdRegistro(cortar(idRegistro == null ? "" : String.valueOf(idRegistro), MAX_ID_REGISTRO));
        b.setDetalle(cortar(detalle, MAX_DETALLE));
        b.setIpOrigen(cortar(ip, MAX_IP));
        b.setExito(exito);
        repo.save(b);
    }

    private Usuario usuarioEnSesion() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) return null;
        return usuarioRepo.findByCuenta(auth.getName()).orElse(null);
    }

    /** IP del cliente. Detrás de un proxy (Render) la real viene en X-Forwarded-For. */
    public static String ipActual() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return "";
        }
        HttpServletRequest request = attrs.getRequest();
        String reenviada = request.getHeader("X-Forwarded-For");
        if (reenviada != null && !reenviada.isBlank()) {
            return reenviada.split(",")[0].trim();
        }
        return request.getRemoteAddr() == null ? "" : request.getRemoteAddr();
    }

    private static String cortar(String texto, int max) {
        if (texto == null) return "";
        return texto.length() <= max ? texto : texto.substring(0, max - 1) + "…";
    }
}
