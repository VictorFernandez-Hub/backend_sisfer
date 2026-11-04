package com.ferreteria.ferreteria.modulos.compra.controller;

import com.ferreteria.ferreteria.excepciones.ResourceNotFoundException;
import com.ferreteria.ferreteria.modulos.compra.dto.CompraDetalle;
import com.ferreteria.ferreteria.modulos.compra.dto.CompraRequest;
import com.ferreteria.ferreteria.modulos.compra.dto.CompraResumen;
import com.ferreteria.ferreteria.modulos.compra.service.CompraService;
import com.ferreteria.ferreteria.modulos.seguridad.service.BitacoraService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compras: registrar en borrador, confirmar (sube el stock y llena el kardex) o anular.
 */
@RestController
@RequestMapping("/api/compras")
@CrossOrigin(origins = "http://localhost:4200")
public class CompraController {

    private final CompraService compraService;
    private final BitacoraService bitacora;

    public CompraController(CompraService compraService, BitacoraService bitacora) {
        this.compraService = compraService;
        this.bitacora = bitacora;
    }

    @GetMapping
    public ResponseEntity<List<CompraResumen>> listar() {
        bitacora.registrar("COMPRAS", "CONSULTAR", "Consultó el listado de compras");
        return ResponseEntity.ok(compraService.listar());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CompraDetalle> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(compraService.obtener(id));
    }

    @PostMapping
    public ResponseEntity<CompraDetalle> registrar(@Valid @RequestBody CompraRequest request) {
        CompraDetalle creada = compraService.registrar(request);
        bitacora.registrar("COMPRAS", "CREAR", "Registró la compra " + creada.numero() + " en borrador");
        return new ResponseEntity<>(creada, HttpStatus.CREATED);
    }

    @PostMapping("/{id}/confirmar")
    public ResponseEntity<CompraDetalle> confirmar(@PathVariable Long id) {
        compraService.confirmar(id);
        CompraDetalle c = compraService.obtener(id);
        bitacora.registrar("COMPRAS", "CONFIRMAR", "Confirmó la compra " + c.numero() + " (ingresó mercadería)");
        return ResponseEntity.ok(c);
    }

    @PostMapping("/{id}/anular")
    public ResponseEntity<CompraDetalle> anular(@PathVariable Long id) {
        compraService.anular(id);
        CompraDetalle c = compraService.obtener(id);
        bitacora.registrar("COMPRAS", "ANULAR", "Anuló la compra " + c.numero());
        return ResponseEntity.ok(c);
    }

    // ----------------------------------------------------------- errores

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> manejarValidaciones(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> errores.put(e.getField(), e.getDefaultMessage()));
        return ResponseEntity.badRequest().body(errores);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> manejarReglas(IllegalArgumentException ex) {
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
