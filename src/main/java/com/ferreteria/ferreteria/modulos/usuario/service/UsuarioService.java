package com.ferreteria.ferreteria.modulos.usuario.service;

import com.ferreteria.ferreteria.modulos.usuario.dto.UsuarioResponseDTO;
import com.ferreteria.ferreteria.modulos.usuario.model.Rol;
import com.ferreteria.ferreteria.modulos.usuario.model.Usuario;
import com.ferreteria.ferreteria.modulos.usuario.repository.RolRepository;
import com.ferreteria.ferreteria.modulos.usuario.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.ferreteria.ferreteria.modulos.seguridad.service.BitacoraService;

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

    public UsuarioService(UsuarioRepository usuarioRepository,
                      RolRepository rolRepository,
                      PasswordEncoder passwordEncoder,
                      BitacoraService bitacora) {
    this.usuarioRepository = usuarioRepository;
    this.rolRepository = rolRepository;
    this.passwordEncoder = passwordEncoder;
    this.bitacora = bitacora;
    }

    public List<UsuarioResponseDTO> listarTodos() {
        return usuarioRepository.findAll().stream().map(this::aDto).toList();
    }

    public UsuarioResponseDTO crear(Map<String, String> datos) {
        Usuario u = new Usuario();
        u.setCuenta(datos.get("cuenta"));
        u.setHashClave(passwordEncoder.encode(datos.get("clave")));
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
        u.setEstado(datos.getOrDefault("estado", "ACTIVO"));
        u.setNumIntentos((short) 0);
        u.setFecRegistro(LocalDateTime.now());
        u.setFecModifica(LocalDateTime.now());
        u.setFecAcceso(LocalDateTime.now());
        u.setRol(buscarRol(datos.get("codigoRol")));
        Usuario guardado = usuarioRepository.save(u);
            bitacora.registrar("USUARIOS", "CREAR", "Creó el usuario " + guardado.getCuenta());
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