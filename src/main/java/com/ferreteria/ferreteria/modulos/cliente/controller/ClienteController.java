package com.ferreteria.ferreteria.modulos.cliente.controller;

import com.ferreteria.ferreteria.modulos.cliente.dto.ClienteDTO;
import com.ferreteria.ferreteria.modulos.cliente.service.ClienteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import com.ferreteria.ferreteria.modulos.seguridad.service.BitacoraService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    private final ClienteService clienteService;
    private final BitacoraService bitacora;
    
    public ClienteController(ClienteService clienteService, BitacoraService bitacora) {
    this.clienteService = clienteService;
    this.bitacora = bitacora;
    }

    @GetMapping
    public ResponseEntity<List<ClienteDTO>> listarTodos() {
        bitacora.registrar("CLIENTES", "CONSULTAR", "Consultó el listado de clientes");
        return ResponseEntity.ok(clienteService.listarTodos());
    }

    @GetMapping("/activos")
    public ResponseEntity<List<ClienteDTO>> listarActivos() {
        return ResponseEntity.ok(clienteService.listarActivos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClienteDTO> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(clienteService.obtenerPorId(id));
    }

    @PostMapping
    public ResponseEntity<ClienteDTO> crear(@Valid @RequestBody ClienteDTO dto) {
        return new ResponseEntity<>(clienteService.guardar(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClienteDTO> actualizar(@PathVariable Long id, @Valid @RequestBody ClienteDTO dto) {
        return ResponseEntity.ok(clienteService.actualizar(id, dto));
    }

    @PatchMapping("/{id}/estado")
    public ResponseEntity<Void> cambiarEstado(@PathVariable Long id, @RequestParam String estado) {
        clienteService.cambiarEstado(id, estado);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        clienteService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // MANEJADORES DE EXCEPCIONES PARA EVITAR EL ERROR 500 Y DEVOLVER 400 BAD REQUEST
    // =========================================================================

    // Captura las validaciones de las anotaciones DTO (@NotBlank, @Pattern, @Size)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> manejarValidacionesDTO(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> 
            errores.put(error.getField(), error.getDefaultMessage())
        );
        return ResponseEntity.badRequest().body(errores);
    }

    // Captura las excepciones de lógica lanzadas desde ClienteServiceImpl (DNI/RUC/Teléfono)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> manejarExcepcionesServicio(IllegalArgumentException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("mensaje", ex.getMessage());
        return ResponseEntity.badRequest().body(error);
    }
}