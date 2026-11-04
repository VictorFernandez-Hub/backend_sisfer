package com.ferreteria.ferreteria.modulos.usuario.controller;

import com.ferreteria.ferreteria.modulos.usuario.model.Rol;
import com.ferreteria.ferreteria.modulos.usuario.model.RolModulo;
import com.ferreteria.ferreteria.modulos.usuario.repository.RolModuloRepository;
import com.ferreteria.ferreteria.modulos.usuario.repository.RolRepository;
import com.ferreteria.ferreteria.modulos.seguridad.service.BitacoraService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/roles")
public class RolController {

    /**
     * Módulos que se pueden asignar a un rol. Cada uno se convierte en la autoridad MOD_xxx
     * al iniciar sesión (UserDetailsServiceImpl). USUARIOS, ROLES y BITACORA permiten crear
     * roles administradores que gestionan usuarios y asignan módulos a los demás.
     */
    public static final Set<String> MODULOS_ASIGNABLES = Set.of(
            "CLIENTES", "PRODUCTOS", "PROVEEDORES", "VENTAS", "COMPRAS", "INVENTARIO", "KARDEX",
            "USUARIOS", "ROLES", "BITACORA");

    private final RolRepository rolRepository;
    private final RolModuloRepository rolModuloRepository;
    private final BitacoraService bitacora;

    public RolController(RolRepository rolRepository, RolModuloRepository rolModuloRepository,
                         BitacoraService bitacora) {
        this.rolRepository = rolRepository;
        this.rolModuloRepository = rolModuloRepository;
        this.bitacora = bitacora;
    }

    // No se anota en bitácora: el menú lo consulta en cada pantalla para saber qué mostrar
    @GetMapping
    public List<Map<String, Object>> listar() {
        return rolRepository.findAll().stream().map(this::aMapa).toList();
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> crear(@RequestBody Map<String, Object> datos) {
        String codigo = String.valueOf(datos.getOrDefault("codigo", "")).trim().toUpperCase();
        if (codigo.isBlank() || rolRepository.findByCodigo(codigo).isPresent()) {
            return ResponseEntity.badRequest().body("El código ya existe o está vacío.");
        }
        Rol rol = new Rol();
        rol.setCodigo(codigo);
        rol.setNombre(String.valueOf(datos.getOrDefault("nombre", codigo)));
        rol.setDescripcion(String.valueOf(datos.getOrDefault("descripcion", "")));
        rol.setEstado("ACTIVO");
        rol.setEsTecnico(false);
        rol.setFecRegistro(LocalDateTime.now());
        rol = rolRepository.save(rol);
        List<String> modulos = guardarModulos(rol.getIdRol(), datos.get("modulos"));
        bitacora.registrar("ROLES", "CREAR", rol.getIdRol(),
                "Creó el rol " + codigo + " (" + rol.getNombre() + ") con los módulos: "
                        + (modulos.isEmpty() ? "ninguno" : String.join(", ", modulos)));
        return ResponseEntity.ok(aMapa(rol));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> actualizar(@PathVariable Short id, @RequestBody Map<String, Object> datos) {
        Rol rol = rolRepository.findById(id).orElse(null);
        if (rol == null) return ResponseEntity.notFound().build();

        StringBuilder c = new StringBuilder();
        String nombre = String.valueOf(datos.getOrDefault("nombre", rol.getNombre()));
        String descripcion = String.valueOf(datos.getOrDefault("descripcion", ""));
        if (!Objects.equals(rol.getNombre(), nombre))
            c.append("Nombre: ").append(rol.getNombre()).append(" → ").append(nombre).append("; ");
        if (!Objects.equals(rol.getDescripcion(), descripcion))
            c.append("Descripción: ").append(rol.getDescripcion()).append(" → ").append(descripcion).append("; ");

        List<String> antes = modulosDe(id);
        rol.setNombre(nombre);
        rol.setDescripcion(descripcion);
        rolRepository.save(rol);
        List<String> despues = guardarModulos(rol.getIdRol(), datos.get("modulos"));

        List<String> agregados = despues.stream().filter(m -> !antes.contains(m)).toList();
        List<String> quitados = antes.stream().filter(m -> !despues.contains(m)).toList();
        if (!agregados.isEmpty()) c.append("Módulos agregados → ").append(String.join(", ", agregados)).append("; ");
        if (!quitados.isEmpty()) c.append("Módulos quitados → ").append(String.join(", ", quitados)).append("; ");

        bitacora.registrar("ROLES", "EDITAR", id,
                "Editó el rol " + rol.getCodigo() + ". " + (c.length() == 0 ? "Sin cambios." : c.toString()));
        return ResponseEntity.ok(aMapa(rol));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> eliminar(@PathVariable Short id) {
        Rol rol = rolRepository.findById(id).orElse(null);
        if (rol == null) return ResponseEntity.notFound().build();
        List<String> modulos = modulosDe(id);
        try {
            rolModuloRepository.deleteByIdRol(id);
            rolRepository.deleteById(id);
            rolRepository.flush(); // Fuerza el DELETE aquí para capturar la FK con usuarios
        } catch (DataIntegrityViolationException ex) {
            bitacora.registrarFallo("ROLES", "ELIMINAR", id,
                    "Intentó eliminar el rol " + rol.getCodigo() + ", pero tiene usuarios asignados");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No se puede eliminar el rol porque tiene usuarios asignados.");
        }
        bitacora.registrar("ROLES", "ELIMINAR", id,
                "Eliminó el rol " + rol.getCodigo() + " (" + rol.getNombre() + "). Tenía los módulos: "
                        + (modulos.isEmpty() ? "ninguno" : String.join(", ", modulos)));
        return ResponseEntity.ok("Rol eliminado.");
    }

    private List<String> modulosDe(Short idRol) {
        return rolModuloRepository.findByIdRol(idRol).stream().map(RolModulo::getModulo).sorted().toList();
    }

    /** Reemplaza los módulos del rol y devuelve los que quedaron guardados. */
    private List<String> guardarModulos(Short idRol, Object modulos) {
        rolModuloRepository.deleteByIdRol(idRol);
        List<String> guardados = new ArrayList<>();
        if (modulos instanceof List<?> lista) {
            for (Object m : lista) {
                String modulo = String.valueOf(m).trim().toUpperCase();
                if (!MODULOS_ASIGNABLES.contains(modulo) || guardados.contains(modulo)) continue; // Ignora desconocidos
                RolModulo rm = new RolModulo();
                rm.setIdRol(idRol);
                rm.setModulo(modulo);
                rolModuloRepository.save(rm);
                guardados.add(modulo);
            }
        }
        Collections.sort(guardados);
        return guardados;
    }

    private Map<String, Object> aMapa(Rol rol) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("idRol", rol.getIdRol());
        mapa.put("codigo", rol.getCodigo());
        mapa.put("nombre", rol.getNombre());
        mapa.put("descripcion", rol.getDescripcion());
        mapa.put("estado", rol.getEstado());
        mapa.put("modulos", rolModuloRepository.findByIdRol(rol.getIdRol())
                .stream().map(RolModulo::getModulo).toList());
        return mapa;
    }
}