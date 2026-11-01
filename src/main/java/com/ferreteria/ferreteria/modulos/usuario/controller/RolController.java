package com.ferreteria.ferreteria.modulos.usuario.controller;

import com.ferreteria.ferreteria.modulos.usuario.model.Rol;
import com.ferreteria.ferreteria.modulos.usuario.model.RolModulo;
import com.ferreteria.ferreteria.modulos.usuario.repository.RolModuloRepository;
import com.ferreteria.ferreteria.modulos.usuario.repository.RolRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/roles")
public class RolController {

    private final RolRepository rolRepository;
    private final RolModuloRepository rolModuloRepository;

    public RolController(RolRepository rolRepository, RolModuloRepository rolModuloRepository) {
        this.rolRepository = rolRepository;
        this.rolModuloRepository = rolModuloRepository;
    }

    @GetMapping
    public List<Map<String, Object>> listar() {
        return rolRepository.findAll().stream().map(this::aMapa).toList();
    }

    @PostMapping
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
        guardarModulos(rol.getIdRol(), datos.get("modulos"));
        return ResponseEntity.ok(aMapa(rol));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Short id, @RequestBody Map<String, Object> datos) {
        Rol rol = rolRepository.findById(id).orElse(null);
        if (rol == null) return ResponseEntity.notFound().build();
            rol.setNombre(String.valueOf(datos.getOrDefault("nombre", rol.getNombre())));
            rol.setDescripcion(String.valueOf(datos.getOrDefault("descripcion", "")));
                rolRepository.save(rol);
                guardarModulos(rol.getIdRol(), datos.get("modulos"));
            return ResponseEntity.ok(aMapa(rol));
}

@DeleteMapping("/{id}")
public ResponseEntity<?> eliminar(@PathVariable Short id) {
    if (rolRepository.findById(id).isEmpty()) return ResponseEntity.notFound().build();
    rolModuloRepository.deleteByIdRol(id);
    rolRepository.deleteById(id);
    return ResponseEntity.ok("Rol eliminado.");
}

    private void guardarModulos(Short idRol, Object modulos) {
        rolModuloRepository.deleteByIdRol(idRol);
        if (modulos instanceof List<?> lista) {
            for (Object m : lista) {
                RolModulo rm = new RolModulo();
                rm.setIdRol(idRol);
                rm.setModulo(String.valueOf(m));
                rolModuloRepository.save(rm);
            }
        }
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