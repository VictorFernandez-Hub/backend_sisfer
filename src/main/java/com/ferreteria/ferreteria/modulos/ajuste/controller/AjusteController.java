package com.ferreteria.ferreteria.modulos.ajuste.controller;

import com.ferreteria.ferreteria.excepciones.ResourceNotFoundException;
import com.ferreteria.ferreteria.modulos.ajuste.dto.AjusteRequest;
import com.ferreteria.ferreteria.modulos.ajuste.dto.AjusteResultado;
import com.ferreteria.ferreteria.modulos.ajuste.service.AjusteService;
import com.ferreteria.ferreteria.modulos.seguridad.service.BitacoraService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Baja o ingreso que no es una compra ni una venta: merma, hallazgo o conteo.
 */
@RestController
@RequestMapping("/api/ajustes")
public class AjusteController {

    private final AjusteService ajusteService;
    private final BitacoraService bitacora;

    public AjusteController(AjusteService ajusteService, BitacoraService bitacora) {
        this.ajusteService = ajusteService;
        this.bitacora = bitacora;
    }

    @PostMapping
    public ResponseEntity<AjusteResultado> registrar(@Valid @RequestBody AjusteRequest request) {
        AjusteResultado creado = ajusteService.registrarYConfirmar(request);
        bitacora.registrar("INVENTARIO", "AJUSTAR",
                "Confirmó el ajuste " + creado.numero() + " (" + request.motivo() + " " + request.tipo() + ")");
        return new ResponseEntity<>(creado, HttpStatus.CREATED);
    }

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
