package com.ferreteria.ferreteria.modulos.producto.controller;

import com.ferreteria.ferreteria.excepciones.ResourceNotFoundException;
import com.ferreteria.ferreteria.modulos.producto.dto.CatalogoDTO;
import com.ferreteria.ferreteria.modulos.producto.dto.ProductoDTO;
import com.ferreteria.ferreteria.modulos.producto.service.ProductoService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import com.ferreteria.ferreteria.modulos.seguridad.service.BitacoraService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/productos")
public class ProductoController {

    private final ProductoService productoService;
    private final BitacoraService bitacora;

    public ProductoController(ProductoService productoService, BitacoraService bitacora) {
    this.productoService = productoService;
    this.bitacora = bitacora;
    }  
    
    @GetMapping
    public ResponseEntity<List<ProductoDTO>> listarTodos() {
        bitacora.registrar("PRODUCTOS", "CONSULTAR", "Consultó el listado de productos");
        return ResponseEntity.ok(productoService.listarTodos());
    }

    // Para combos de selección (compras, ventas, ajustes)
    @GetMapping("/activos")
    public ResponseEntity<List<ProductoDTO>> listarActivos() {
        return ResponseEntity.ok(productoService.listarActivos());
    }

    @GetMapping("/siguiente-codigo")
    public ResponseEntity<Map<String, String>> siguienteCodigo() {
        return ResponseEntity.ok(Map.of("codigo", productoService.siguienteCodigo()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductoDTO> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(productoService.obtenerPorId(id));
    }

    @PostMapping
    public ResponseEntity<ProductoDTO> crear(@Valid @RequestBody ProductoDTO dto) {
        return new ResponseEntity<>(productoService.guardar(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductoDTO> actualizar(@PathVariable Long id, @Valid @RequestBody ProductoDTO dto) {
        return ResponseEntity.ok(productoService.actualizar(id, dto));
    }

    @PatchMapping("/{id}/estado")
    public ResponseEntity<Void> cambiarEstado(@PathVariable Long id, @RequestParam String estado) {
        productoService.cambiarEstado(id, estado);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        productoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
    

    // =========================================================================
    // CATÁLOGOS (combos del formulario). Quedan bajo /api/productos/** para
    // reutilizar las reglas de SecurityConfig sin agregar rutas nuevas.
    // =========================================================================

    @GetMapping("/categorias")
    public ResponseEntity<List<CatalogoDTO>> listarCategorias() {
        return ResponseEntity.ok(productoService.listarCategorias());
    }

    @GetMapping("/marcas")
    public ResponseEntity<List<CatalogoDTO>> listarMarcas() {
        return ResponseEntity.ok(productoService.listarMarcas());
    }

    @GetMapping("/unidades")
    public ResponseEntity<List<CatalogoDTO>> listarUnidades() {
        return ResponseEntity.ok(productoService.listarUnidades());
    }

    // =========================================================================
    // MANEJADORES DE EXCEPCIONES
    // =========================================================================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> manejarValidacionesDTO(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
            errores.put(error.getField(), error.getDefaultMessage())
        );
        return ResponseEntity.badRequest().body(errores);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> manejarExcepcionesServicio(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("mensaje", ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> manejarNoEncontrado(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("mensaje", ex.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> manejarIntegridad(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("mensaje", "La operación viola una restricción de la base de datos."));
    }
}
