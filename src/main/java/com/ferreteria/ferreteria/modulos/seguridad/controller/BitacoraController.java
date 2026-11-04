package com.ferreteria.ferreteria.modulos.seguridad.controller;

import com.ferreteria.ferreteria.modulos.seguridad.repository.BitacoraRepository;
import com.ferreteria.ferreteria.modulos.seguridad.service.BitacoraService;
import com.ferreteria.ferreteria.modulos.usuario.model.Usuario;
import com.ferreteria.ferreteria.modulos.usuario.repository.UsuarioRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/bitacora")
public class BitacoraController {

    /** Pantallas del menú que se registran al entrar (mismas rutas del frontend). */
    private static final Map<String, String> MODULOS = Map.ofEntries(
            Map.entry("clientes", "CLIENTES"),
            Map.entry("productos", "PRODUCTOS"),
            Map.entry("proveedores", "PROVEEDORES"),
            Map.entry("ventas", "VENTAS"),
            Map.entry("compras", "COMPRAS"),
            Map.entry("inventario", "INVENTARIO"),
            Map.entry("kardex", "KARDEX"),
            Map.entry("reportes", "REPORTES"),
            Map.entry("usuarios", "USUARIOS"),
            Map.entry("roles", "ROLES"),
            Map.entry("bitacora", "BITACORA"),
            Map.entry("inicio", "INICIO"));

    private final BitacoraRepository repo;
    private final UsuarioRepository usuarioRepo;
    private final BitacoraService bitacora;

    public BitacoraController(BitacoraRepository repo, UsuarioRepository usuarioRepo, BitacoraService bitacora) {
        this.repo = repo;
        this.usuarioRepo = usuarioRepo;
        this.bitacora = bitacora;
    }

    @GetMapping
    public List<Map<String, Object>> listar() {
        bitacora.registrar("BITACORA", "CONSULTAR", "Consultó la bitácora de auditoría");

        // Una sola consulta de usuarios en lugar de una por fila
        Map<Integer, Usuario> usuarios = usuarioRepo.findAll().stream()
                .collect(Collectors.toMap(Usuario::getIdUsuario, Function.identity(), (a, b) -> a));

        return repo.findAllByOrderByFecEventoDesc().stream().map(b -> {
            Map<String, Object> m = new HashMap<>();
            Usuario u = usuarios.get(b.getIdUsuario());
            m.put("idBitacora", b.getIdBitacora());
            m.put("fecEvento", b.getFecEvento());
            m.put("modulo", b.getModulo());
            m.put("accion", b.getAccion());
            m.put("idRegistro", b.getIdRegistro());
            m.put("detalle", b.getDetalle());
            m.put("ipOrigen", b.getIpOrigen());
            m.put("exito", b.getExito() == null || b.getExito());
            m.put("cuenta", u != null ? u.getCuenta() : "usuario " + b.getIdUsuario());
            m.put("rol", u != null && u.getRol() != null ? u.getRol().getCodigo() : "");
            return m;
        }).toList();
    }

    /**
     * El frontend avisa cada vez que el usuario abre una pantalla del menú.
     * Abierto a cualquier usuario con sesión (ver SecurityConfig); solo acepta rutas conocidas.
     */
    @PostMapping("/ingreso")
    public ResponseEntity<Void> ingreso(@RequestBody Map<String, String> body) {
        String ruta = String.valueOf(body.getOrDefault("ruta", "")).trim().toLowerCase();
        String modulo = MODULOS.get(ruta);
        if (modulo == null) return ResponseEntity.badRequest().build();
        bitacora.registrar(modulo, "INGRESO", "Ingresó al módulo " + modulo.charAt(0) + modulo.substring(1).toLowerCase());
        return ResponseEntity.noContent().build();
    }
}
