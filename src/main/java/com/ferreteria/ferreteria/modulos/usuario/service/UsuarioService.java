package com.ferreteria.ferreteria.modulos.usuario.service;

import com.ferreteria.ferreteria.modulos.usuario.dto.UsuarioResponseDTO;
import com.ferreteria.ferreteria.modulos.usuario.model.Rol;
import com.ferreteria.ferreteria.modulos.usuario.model.Usuario;
import com.ferreteria.ferreteria.modulos.usuario.repository.RolRepository;
import com.ferreteria.ferreteria.modulos.usuario.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.ferreteria.ferreteria.modulos.seguridad.service.BitacoraService;
import org.springframework.beans.factory.annotation.Value;
import com.ferreteria.ferreteria.modulos.seguridad.service.CorreoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;
    private final BitacoraService bitacora;
    private final CorreoService correoService;
    private static final Logger log = LoggerFactory.getLogger(UsuarioService.class);

    // Local: http://localhost:4200. Producción: FRONTEND_URL con la URL del frontend en Render
    @Value("${app.frontend-url}")
    private String frontendUrl;

    public UsuarioService(UsuarioRepository usuarioRepository,
                          RolRepository rolRepository,
                          PasswordEncoder passwordEncoder,
                          BitacoraService bitacora,
                          CorreoService correoService) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.passwordEncoder = passwordEncoder;
        this.bitacora = bitacora;
        this.correoService = correoService;
    }

    public List<UsuarioResponseDTO> listarTodos() {
        return usuarioRepository.findAll().stream().map(this::aDto).toList();
    }

    public UsuarioResponseDTO crear(Map<String, String> datos) {
        Usuario u = new Usuario();
        u.setCuenta(datos.get("cuenta"));
        u.setHashClave(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));
        u.setNombres(datos.get("nombresCompleto"));
        u.setApellidos("");
        u.setEmail(datos.getOrDefault("email", ""));
        u.setTelefono(datos.getOrDefault("telefono", ""));
        u.setDni(datos.getOrDefault("dni", ""));
        if (u.getDni() != null && !u.getDni().isBlank()
                && usuarioRepository.findByDni(u.getDni()).isPresent()) {
            throw new RuntimeException("El DNI ya está registrado.");
        }
        u.setTipoContrato(datos.get("tipoContrato"));
        u.setFecInicio(aFecha(datos.get("fecInicio")));
        u.setFecFin(aFecha(datos.get("fecFin")));
        u.setEstado("INACTIVO");
        u.setNumIntentos((short) 0);
        u.setFecRegistro(LocalDateTime.now());
        u.setFecModifica(LocalDateTime.now());
        u.setFecAcceso(LocalDateTime.now());
        u.setRol(buscarRol(datos.get("codigoRol")));
        String token = java.util.UUID.randomUUID().toString().replace("-", "");
        u.setTokenActivacion(token);
        u.setFecToken(LocalDateTime.now().plusHours(24));
        Usuario guardado = usuarioRepository.save(u);
        bitacora.registrar("USUARIOS", "CREAR", "Creó el usuario " + guardado.getCuenta());
        enviarActivacion(guardado);
        return aDto(guardado);
    }

    public UsuarioResponseDTO actualizar(Integer id, Map<String, String> datos) {
        Usuario u = usuarioRepository.findById(id).orElseThrow();

        StringBuilder c = new StringBuilder();
        if (!java.util.Objects.equals(u.getNombres(), datos.get("nombresCompleto")))
            c.append("Nombre: ").append(u.getNombres()).append(" → ").append(datos.get("nombresCompleto")).append("; ");
        if (!java.util.Objects.equals(u.getEmail(), datos.get("email")))
            c.append("Correo: ").append(u.getEmail()).append(" → ").append(datos.get("email")).append("; ");
        if (!java.util.Objects.equals(u.getTelefono(), datos.get("telefono")))
            c.append("Teléfono: ").append(u.getTelefono()).append(" → ").append(datos.get("telefono")).append("; ");
        if (!java.util.Objects.equals(u.getEstado(), datos.get("estado")))
            c.append("estado: ").append(u.getEstado()).append(" → ").append(datos.get("estado")).append("; ");

        if (datos.get("nombresCompleto") != null) {
            u.setNombres(datos.get("nombresCompleto"));
        }
        if (datos.get("email") != null) {
            u.setEmail(datos.get("email"));
        }
        if (datos.get("telefono") != null) {
            u.setTelefono(datos.get("telefono"));
        }
        if (datos.get("dni") != null) {
            String dniNuevo = datos.get("dni");
            usuarioRepository.findByDni(dniNuevo).ifPresent(otro -> {
                if (!otro.getIdUsuario().equals(u.getIdUsuario())) {
                    throw new RuntimeException("El DNI ya está registrado.");
                }
            });
            u.setDni(dniNuevo);
        }

        if (datos.get("tipoContrato") != null) {
            u.setTipoContrato(datos.get("tipoContrato"));
        }
        u.setFecInicio(aFecha(datos.get("fecInicio")));
        u.setFecFin(aFecha(datos.get("fecFin")));

        if (datos.get("estado") != null) {
            u.setEstado(datos.get("estado"));
            if ("ACTIVO".equalsIgnoreCase(datos.get("estado"))) {
                u.setNumIntentos((short) 0);
            }
        }
        if (datos.get("codigoRol") != null && !datos.get("codigoRol").isBlank()) {
            u.setRol(buscarRol(datos.get("codigoRol")));
        }
        if (datos.get("clave") != null && !datos.get("clave").isBlank()) {
            u.setHashClave(passwordEncoder.encode(datos.get("clave")));
        }
        u.setFecModifica(LocalDateTime.now());
        Usuario guardado = usuarioRepository.save(u);
        bitacora.registrar("USUARIOS", "EDITAR",
            "Editó a " + u.getCuenta() + ". " + (c.length() == 0 ? "Sin cambios." : c.toString()));
        return aDto(guardado);
    }

    public void reenviarActivacion(Integer id) {
        Usuario u = usuarioRepository.findById(id).orElseThrow();
        if (!"INACTIVO".equals(u.getEstado())) return;
        String token = java.util.UUID.randomUUID().toString().replace("-", "");
        u.setTokenActivacion(token);
        u.setFecToken(LocalDateTime.now().plusHours(24));
        usuarioRepository.save(u);
        enviarActivacion(u);
    }

    private void enviarActivacion(Usuario u) {
        String enlace = frontendUrl + "/activar?token=" + u.getTokenActivacion();
        try {
            String html = """
                <div style="background:#f4f4f5; padding:32px 12px; font-family:Arial, sans-serif;">
                  <div style="max-width:460px; margin:auto; background:#ffffff; border-radius:12px; overflow:hidden; box-shadow:0 2px 8px rgba(0,0,0,.06);">
                    <div style="background:#f97316; padding:22px 28px; text-align:center;">
                      <div style="color:#fff; font-size:13px; letter-spacing:2px;">LA SOLUCIÓN</div>
                      <div style="color:#fff; font-size:22px; font-weight:bold;">Ferretera</div>
                    </div>
                    <div style="padding:32px 28px; color:#1f2937;">
                      <p style="margin:0 0 8px;">Hola <strong>%s</strong>,</p>
                      <p style="margin:0 0 20px; color:#4b5563;">Tu cuenta fue creada. Para activarla y crear tu clave, pulsa el botón. El enlace vence en 24 horas.</p>
                      <div style="text-align:center; margin:24px 0;">
                        <a href="%s" style="background:#f97316; color:#fff; text-decoration:none; padding:12px 28px; border-radius:8px; font-weight:bold;">Activar cuenta</a>
                      </div>
                      <p style="margin:20px 0 0; font-size:13px; color:#9ca3af;">Si no solicitaste esto, ignora el correo.</p>
                    </div>
                    <div style="background:#fafafa; padding:14px 28px; text-align:center; font-size:12px; color:#9ca3af;">
                      La Solución Ferretera · Km. 08 Carretera Piura – Chulucanas
                    </div>
                  </div>
                </div>
                """.formatted(u.getNombres(), enlace);
            correoService.enviar(u.getEmail(), "Activa tu cuenta - La Solución Ferretera", html);
        } catch (Exception e) {
            log.error("No se pudo enviar la activación a {}: {}", CorreoService.ocultar(u.getEmail()), e.getMessage(), e);
        }
    }

    private Rol buscarRol(String codigo) {
        return rolRepository.findByCodigo(codigo).orElseThrow();
    }

    private UsuarioResponseDTO aDto(Usuario u) {
        return new UsuarioResponseDTO(
                u.getIdUsuario(),
                u.getCuenta(),
                ((u.getNombres() != null ? u.getNombres() : "") + " "
                        + (u.getApellidos() != null ? u.getApellidos() : "")).trim(),
                u.getEmail(),
                u.getTelefono(),
                u.getDni(),
                u.getEstado(),
                u.getRol() != null ? List.of(u.getRol().getNombre()) : Collections.emptyList(),
                u.getRol() != null ? u.getRol().getCodigo() : null,
                u.getTipoContrato(),
                u.getFecInicio(),
                u.getFecFin()
        );
    }

    public void eliminar(Integer id) {
        usuarioRepository.deleteById(id);
        bitacora.registrar("USUARIOS", "ELIMINAR", "Eliminó el usuario " + id);
    }

    private LocalDate aFecha(String valor) {
        if (valor == null || valor.isBlank()) return null;
        return LocalDate.parse(valor);
    }
}