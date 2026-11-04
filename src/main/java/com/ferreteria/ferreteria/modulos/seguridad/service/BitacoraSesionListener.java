package com.ferreteria.ferreteria.modulos.seguridad.service;

import com.ferreteria.ferreteria.modulos.seguridad.controller.AuthController;
import com.ferreteria.ferreteria.modulos.usuario.repository.UsuarioRepository;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * Cierra el ciclo de la sesión en la bitácora cuando el usuario NO pulsa "Cerrar sesión":
 * cerró el navegador o dejó de usar el sistema y la sesión venció por inactividad.
 * Si ya hubo logout, AuthController marca la sesión y aquí no se registra de nuevo.
 */
@Component
public class BitacoraSesionListener implements HttpSessionListener {

    private final BitacoraService bitacora;
    private final UsuarioRepository usuarioRepository;

    public BitacoraSesionListener(BitacoraService bitacora, UsuarioRepository usuarioRepository) {
        this.bitacora = bitacora;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent event) {
        try {
            HttpSession session = event.getSession();
            if (Boolean.TRUE.equals(session.getAttribute(AuthController.CIERRE_REGISTRADO))) return;

            Object ctx = session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            if (!(ctx instanceof SecurityContext contexto)) return;
            Authentication auth = contexto.getAuthentication();
            if (auth == null || auth.getName() == null) return;

            usuarioRepository.findByCuenta(auth.getName()).ifPresent(u ->
                    bitacora.registrarUsuario(u.getIdUsuario(), "SEGURIDAD", "SESION_EXPIRADA",
                            "La sesión se cerró por inactividad (sin pulsar Cerrar sesión)", true, ""));
        } catch (IllegalStateException ignorada) {
            // La sesión ya estaba invalidada: no hay nada que leer
        }
    }
}
